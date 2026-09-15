package com.minhaempresa.gendaz.whatsapp.service;

import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.shared.PhoneNumberService;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppNotificacaoEntity;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppStatusNotificacao;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppTipoNotificacao;
import com.minhaempresa.gendaz.whatsapp.policy.WhatsAppPlanoPolicy;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppNotificacaoRepository;
import jakarta.persistence.OptimisticLockException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sincronizacao de lembretes de agendamento (Fase 6, somente
 * LEMBRETE_AGENDAMENTO). Chamado pelo listener AFTER_COMMIT, portanto fora
 * da transacao da agenda: cada metodo abre sua propria transacao e nunca
 * propaga falha para o dominio.
 *
 * <p>Regra oficial: 1 agendamento elegivel gera 1 lembrete exatamente 2h
 * antes do atendimento, calculado no timezone da empresa e persistido em
 * UTC. A cota continua reservada somente pelo worker no claim.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppLembreteAgendamentoService {

    static final String PREFIXO_CHAVE = "AGENDAMENTO_REMINDER:";
    static final int TOLERANCIA_MINUTOS = 10;

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA_BR = DateTimeFormatter.ofPattern("HH:mm");

    private final AgendamentoRepository agendamentoRepository;
    private final ClienteRepository clienteRepository;
    private final AssinaturaService assinaturaService;
    private final WhatsAppConfiguracaoService configuracaoService;
    private final WhatsAppNotificacaoService notificacaoService;
    private final WhatsAppNotificacaoRepository notificacaoRepository;
    private final WhatsAppFilaService filaService;
    private final WhatsAppQuotaService quotaService;
    private final PhoneNumberService phoneNumberService;
    private final WhatsAppClock clock;

    private WhatsAppLembreteAgendamentoService self;

    /**
     * Auto-referencia via proxy: o cancelamento por linha roda em transacao
     * propria para que derrota em corrida (conflito otimista) nao contamine
     * a transacao corrente — ela decide sobre o estado vencedor em seguida.
     */
    @Autowired
    public void setSelf(@Lazy WhatsAppLembreteAgendamentoService self) {
        this.self = self;
    }

    /**
     * Sincroniza o lembrete do agendamento: cancela versoes antigas ainda
     * ativas e cria (ou reutiliza por idempotencia) a versao do horario
     * atual. Nao cria se ja estiver atrasado. Nunca lanca para o chamador
     * do dominio: executor e o listener AFTER_COMMIT.
     *
     * <p>Transacao propria (REQUIRES_NEW): o listener AFTER_COMMIT executa
     * apos o commit da agenda, quando a sincronizacao antiga ainda esta
     * vinculada a thread — sem transacao nova, escritas falham.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sincronizar(Long empresaId, Long agendamentoId) {
        Optional<AgendamentoEntity> atual =
                agendamentoRepository.findByIdAndEmpresaId(agendamentoId, empresaId);
        if (atual.isEmpty()) {
            return;
        }
        AgendamentoEntity agendamento = atual.get();
        VersaoLembrete versao = calcularVersao(agendamento);
        cancelarVersoesAntigas(empresaId, agendamentoId, versao == null ? null : versao.chave());
        if (versao == null) {
            return;
        }
        filaService.enfileirar(
                empresaId,
                WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO,
                versao.chave(),
                versao.scheduledAt(),
                versao.clienteId(),
                agendamentoId,
                versao.recipient(),
                versao.mensagem(),
                versao.expiresAt());
    }

    /**
     * Cancela reminders ligados ao agendamento. PENDENTE e ENVIANDO sem
     * sendStartedAt cancelam com liberacao de reserva; ENVIANDO com chamada
     * externa possivelmente iniciada nao e tocado (o worker finaliza);
     * ENVIADO/FALHOU/CANCELADO nunca reescrevem historico. Somente
     * LEMBRETE_AGENDAMENTO.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelar(Long empresaId, Long agendamentoId) {
        List<WhatsAppNotificacaoEntity> ligadas =
                notificacaoRepository.findByEmpresaIdAndAgendamentoId(empresaId, agendamentoId);
        for (WhatsAppNotificacaoEntity candidata : ligadas) {
            if (candidata.getTipo() != WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO) {
                continue;
            }
            cancelarComRecuperacao(empresaId, candidata.getId());
        }
    }

    private void cancelarComRecuperacao(Long empresaId, Long notificacaoId) {
        try {
            self.cancelarNotificacao(empresaId, notificacaoId);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException corrida) {
            // Perdeu a corrida: a transacao interna rolou sozinha; a
            // corrente segue intacta e decide sobre o estado vencedor.
            try {
                self.reavaliarAposConflito(empresaId, notificacaoId);
            } catch (Exception e2) {
                // Patologico (duas derrotas seguidas): nao insiste; os fluxos
                // de stale do worker revisitam a linha depois.
                log.error("[whatsapp-lembrete] cancelamento apos conflito abortado notificacao={}. erroTipo={}",
                        notificacaoId, e2.getClass().getSimpleName());
            }
        }
    }

    /**
     * Cancela uma notificacao em transacao propria curta (lock detido so
     * aqui): PENDENTE e ENVIANDO sem sendStartedAt cancelam com liberacao;
     * ENVIANDO com chamada externa possivelmente iniciada nao e tocado.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelarNotificacao(Long empresaId, Long notificacaoId) {
        // Rele com lock e reavalia: se o worker marcou sendStartedAt
        // primeiro, nao cancela nem libera (ele finaliza normalmente).
        Optional<WhatsAppNotificacaoEntity> atual = notificacaoRepository
                .findByIdAndEmpresaIdForUpdate(notificacaoId, empresaId);
        if (atual.isEmpty()) {
            return;
        }
        WhatsAppNotificacaoEntity entidade = atual.get();
        if (entidade.getStatus() == WhatsAppStatusNotificacao.PENDENTE
                || (entidade.getStatus() == WhatsAppStatusNotificacao.ENVIANDO
                        && entidade.getSendStartedAt() == null)) {
            liberarReservaSeExistir(empresaId, entidade);
            entidade.setStatus(WhatsAppStatusNotificacao.CANCELADO);
            entidade.setProcessingStartedAt(null);
            entidade.setSendStartedAt(null);
            entidade.setNextAttemptAt(null);
            notificacaoRepository.save(entidade);
            log.info("[whatsapp-lembrete] reminder cancelado notificacao={}", entidade.getId());
        } else if (entidade.getStatus() == WhatsAppStatusNotificacao.ENVIANDO) {
            log.info("[whatsapp-lembrete] envio possivelmente em curso, mantido notificacao={}",
                    entidade.getId());
        }
    }

    /**
     * Apos perder corrida, decide sobre o estado vencedor em nova transacao:
     * envio iniciado e preservado; linha ainda cancelavel e cancelada agora
     * (ex.: claim venceu no meio); demais estados sao historico.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reavaliarAposConflito(Long empresaId, Long notificacaoId) {
        Optional<WhatsAppNotificacaoEntity> atual = notificacaoRepository
                .findByIdAndEmpresaIdForUpdate(notificacaoId, empresaId);
        if (atual.isEmpty()) {
            return;
        }
        WhatsAppNotificacaoEntity entidade = atual.get();
        if (entidade.getStatus() == WhatsAppStatusNotificacao.ENVIANDO
                && entidade.getSendStartedAt() != null) {
            log.info("[whatsapp-lembrete] envio venceu corrida, mantido notificacao={}", entidade.getId());
            return;
        }
        if (entidade.getStatus() == WhatsAppStatusNotificacao.PENDENTE
                || (entidade.getStatus() == WhatsAppStatusNotificacao.ENVIANDO
                        && entidade.getSendStartedAt() == null)) {
            liberarReservaSeExistir(empresaId, entidade);
            entidade.setStatus(WhatsAppStatusNotificacao.CANCELADO);
            entidade.setProcessingStartedAt(null);
            entidade.setSendStartedAt(null);
            entidade.setNextAttemptAt(null);
            notificacaoRepository.save(entidade);
            log.info("[whatsapp-lembrete] reminder cancelado apos corrida notificacao={}", entidade.getId());
        }
    }

    /**
     * Protecao defensiva do worker antes do inicio real da chamada externa.
     * Vale para todos os tipos: ENVIANDO + expiracao/plano/cliente/telefone
     * revalidados; lembrete ainda confere a versao do horario. Retorna false
     * quando cancelou (com liberacao). Registros Fase 5 sem cliente vinculado
     * validam apenas o recipient armazenado.
     */
    @Transactional
    public boolean revalidarParaEnvio(Long notificacaoId) {
        Optional<WhatsAppNotificacaoEntity> atual =
                notificacaoRepository.findByIdForUpdate(notificacaoId);
        if (atual.isEmpty()) {
            return false;
        }
        WhatsAppNotificacaoEntity entidade = atual.get();
        if (entidade.getStatus() != WhatsAppStatusNotificacao.ENVIANDO) {
            return false;
        }
        LocalDateTime agora = clock.agoraUtc();
        if (entidade.getExpiresAt() != null && agora.isAfter(entidade.getExpiresAt())) {
            expirarComCodigoPorTipo(entidade);
            return false;
        }
        Long empresaId = entidade.getEmpresa().getId();
        String plano = assinaturaService.buscarAtualPorEmpresa(empresaId)
                .map(a -> a.getPlano().getNome())
                .orElse(null);
        if (!WhatsAppPlanoPolicy.possuiWhatsApp(plano)) {
            cancelarComCodigo(entidade, "PLAN_NO_WHATSAPP");
            return false;
        }
        if (entidade.getAgendamento() == null && entidade.getCliente() == null
                && entidade.getTipo() != WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO) {
            if (!phoneNumberService.canonicoValido(entidade.getRecipient())) {
                cancelarComCodigo(entidade, "INVALID_RECIPIENT");
                return false;
            }
            return true;
        }
        if (entidade.getCliente() == null) {
            if (!phoneNumberService.canonicoValido(entidade.getRecipient())) {
                cancelarComCodigo(entidade, "INVALID_RECIPIENT");
                return false;
            }
            return entidade.getTipo() == WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO
                    ? revalidarHorario(entidade)
                    : true;
        }
        Optional<ClienteEntity> clienteAtual = clienteRepository.findByIdAndEmpresaId(
                entidade.getCliente().getId(), empresaId);
        if (clienteAtual.isEmpty() || clienteAtual.get().getStatus() != StatusCadastro.ATIVO) {
            cancelarComCodigo(entidade, "INVALID_RECIPIENT");
            return false;
        }
        ClienteEntity cliente = clienteAtual.get();
        if (!cliente.isReceberWhatsapp()) {
            cancelarComCodigo(entidade, "WHATSAPP_OPT_OUT");
            return false;
        }
        String telefone = cliente.getTelefone();
        if (!phoneNumberService.canonicoValido(telefone)
                || !telefone.trim().equals(entidade.getRecipient())) {
            cancelarComCodigo(entidade, "INVALID_RECIPIENT");
            return false;
        }
        if (entidade.getTipo() != WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO) {
            return true;
        }
        if (entidade.getAgendamento() == null) {
            return true;
        }
        return revalidarHorarioComAgendamento(entidade, empresaId);
    }

    private boolean revalidarHorario(WhatsAppNotificacaoEntity entidade) {
        // Lembrete sem agendamento vinculado (linhas operacionais diretas):
        // sem versao de horario para comparar.
        return true;
    }

    /**
     * Expiracao no claim, para todos os tipos: apos expiresAt nao ha envio
     * nem reserva. Em retry com reserva existente, libera exatamente uma vez.
     * Retorna true quando expirou (chamador nao deve prosseguir).
     */
    boolean expiradoSeNecessario(WhatsAppNotificacaoEntity entidade, LocalDateTime agora) {
        if (entidade.getExpiresAt() == null || !agora.isAfter(entidade.getExpiresAt())) {
            return false;
        }
        expirarComCodigoPorTipo(entidade);
        return true;
    }

    private void expirarComCodigoPorTipo(WhatsAppNotificacaoEntity entidade) {
        cancelarComCodigo(entidade,
                entidade.getTipo() == WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO
                        ? "REMINDER_EXPIRED"
                        : "CRM_MESSAGE_EXPIRED");
    }

    private boolean revalidarHorarioComAgendamento(
            WhatsAppNotificacaoEntity entidade, Long empresaId) {
        Optional<AgendamentoEntity> agendamento =
                agendamentoRepository.findByIdAndEmpresaId(entidade.getAgendamento().getId(), empresaId);
        if (agendamento.isEmpty()
                || !statusElegivel(agendamento.get().getStatus())
                || agendamento.get().isExcluidoAgenda()
                || !clienteAtivo(agendamento.get().getCliente())) {
            cancelarComCodigo(entidade, "REMINDER_OUTDATED");
            return false;
        }
        LocalDateTime atendimentoUtc = inicioAtendimentoUtc(agendamento.get());
        if (!atendimentoUtc.equals(entidade.getScheduledAt().plusHours(2))) {
            cancelarComCodigo(entidade, "REMINDER_OUTDATED");
            return false;
        }
        return true;
    }

    private void cancelarComCodigo(WhatsAppNotificacaoEntity entidade, String codigo) {
        liberarReservaSeExistir(entidade.getEmpresa().getId(), entidade);
        entidade.setStatus(WhatsAppStatusNotificacao.CANCELADO);
        entidade.setLastError(codigo);
        entidade.setProcessingStartedAt(null);
        entidade.setSendStartedAt(null);
        entidade.setNextAttemptAt(null);
        notificacaoRepository.save(entidade);
        log.info("[whatsapp-lembrete] reminder {} notificacao={}", codigo, entidade.getId());
    }

    private void liberarReservaSeExistir(Long empresaId, WhatsAppNotificacaoEntity entidade) {
        if (entidade.isQuotaReserved()) {
            quotaService.liberarReservaNoCiclo(
                    empresaId,
                    entidade.getTipo().categoria(),
                    entidade.getQuotaCycleStart());
            entidade.setQuotaReserved(false);
        }
    }

    private void cancelarVersoesAntigas(Long empresaId, Long agendamentoId, String chaveAtual) {
        List<WhatsAppNotificacaoEntity> ligadas =
                notificacaoRepository.findByEmpresaIdAndAgendamentoId(empresaId, agendamentoId);
        for (WhatsAppNotificacaoEntity candidata : ligadas) {
            if (candidata.getTipo() != WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO) {
                continue;
            }
            if (chaveAtual != null && chaveAtual.equals(candidata.getIdempotencyKey())) {
                continue;
            }
            // Mesmo tratamento atomico do cancelamento explicito.
            cancelarComRecuperacao(empresaId, candidata.getId());
        }
    }

    private record VersaoLembrete(
            String chave,
            LocalDateTime scheduledAt,
            LocalDateTime expiresAt,
            Long clienteId,
            String recipient,
            String mensagem) {
    }

    /**
     * Calcula a versao do lembrete para o estado atual do agendamento, ou
     * vazio (null) quando inelegivel. scheduledAt/expiresAt em UTC.
     */
    private VersaoLembrete calcularVersao(AgendamentoEntity agendamento) {
        Long empresaId = agendamento.getEmpresa().getId();
        if (!configuracaoService.lembretesAtivos(empresaId)) {
            return null;
        }
        String plano = assinaturaService.buscarAtualPorEmpresa(empresaId)
                .map(a -> a.getPlano().getNome())
                .orElse(null);
        if (!WhatsAppPlanoPolicy.possuiWhatsApp(plano)) {
            return null;
        }
        if (!statusElegivel(agendamento.getStatus()) || agendamento.isExcluidoAgenda()) {
            return null;
        }
        ClienteEntity cliente = agendamento.getCliente();
        if (!clienteAtivo(cliente)) {
            return null;
        }
        if (!cliente.isReceberWhatsapp()) {
            return null;
        }
        String telefone = cliente.getTelefone();
        if (!phoneNumberService.canonicoValido(telefone)) {
            return null;
        }
        LocalDateTime atendimentoUtc = inicioAtendimentoUtc(agendamento);
        LocalDateTime agora = clock.agoraUtc();
        LocalDateTime scheduledAt = atendimentoUtc.minusHours(2);
        if (!scheduledAt.isAfter(agora)) {
            return null;
        }
        long epoch = atendimentoUtc.toEpochSecond(ZoneOffset.UTC);
        String chave = PREFIXO_CHAVE + agendamento.getId() + ":" + epoch + ":" + cliente.getId();
        ZoneId zona = clock.zonaEmpresa(agendamento.getEmpresa().getTimezone());
        ZonedDateTime atendimentoLocal = atendimentoUtc.atZone(ZoneOffset.UTC).withZoneSameInstant(zona);
        String template = configuracaoService.obterLembreteTemplate(empresaId);

        String mensagem = template
                .replace("{cliente}", cliente.getNome())
                .replace("{empresa}", agendamento.getEmpresa().getNomeFantasia())
                .replace("{data}", atendimentoLocal.format(DATA_BR))
                .replace("{hora}", atendimentoLocal.format(HORA_BR));

        return new VersaoLembrete(
                chave, scheduledAt, scheduledAt.plusMinutes(TOLERANCIA_MINUTOS),
                cliente.getId(), telefone.trim(), mensagem);
    }

    private LocalDateTime inicioAtendimentoUtc(AgendamentoEntity agendamento) {
        ZoneId zona = clock.zonaEmpresa(agendamento.getEmpresa().getTimezone());
        return ZonedDateTime.of(agendamento.getData(), agendamento.getHoraInicio(), zona)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    private boolean statusElegivel(StatusAgendamento status) {
        return status == StatusAgendamento.PENDENTE || status == StatusAgendamento.CONFIRMADO;
    }

    public void reconsiliarTodos(Long empresaId) {
        if (!configuracaoService.lembretesAtivos(empresaId)) {
            return;
        }
        List<AgendamentoEntity> futuros = agendamentoRepository.findByEmpresaIdAndStatusInAndDataGreaterThanEqualOrderByDataAscHoraInicioAsc(
                empresaId,
                List.of(StatusAgendamento.PENDENTE, StatusAgendamento.CONFIRMADO),
                clock.agoraUtc().toLocalDate()
        );
        for (AgendamentoEntity agendamento : futuros) {
            try {
                self.sincronizar(empresaId, agendamento.getId());
            } catch (Exception e) {
                log.warn("[whatsapp-lembrete] reconciliacao ignorou agendamento={} erroTipo={}",
                        agendamento.getId(), e.getClass().getSimpleName());
            }
        }
    }

    private boolean clienteAtivo(ClienteEntity cliente) {
        return cliente != null && cliente.getStatus() == StatusCadastro.ATIVO;
    }

    /**
     * Backfill AFTER_COMMIT: roda somente depois que a ativacao foi commitada.
     * Busca SOMENTE agendamentos futuros elegiveis (empresa atual, nao
     * cancelados, status elegivel) e reutiliza sincronizar() central, que e
     * idempotente por idempotency_key. Falha posterior nunca reverte a
     * configuracao ja confirmada: apenas loga.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLembretesAtivados(WhatsAppConfiguracaoService.LembretesAtivadosEvent event) {
        Long empresaId = event.empresaId();
        try {
            List<AgendamentoEntity> futuros =
                    agendamentoRepository.findByEmpresaIdAndStatusInAndDataGreaterThanEqualOrderByDataAscHoraInicioAsc(
                            empresaId,
                            List.of(StatusAgendamento.PENDENTE, StatusAgendamento.CONFIRMADO),
                            clock.agoraUtc().toLocalDate());
            for (AgendamentoEntity agendamento : futuros) {
                try {
                    self.sincronizar(empresaId, agendamento.getId());
                } catch (Exception e) {
                    log.warn("[whatsapp-lembrete] backfill ignorou agendamento={} erroTipo={}",
                            agendamento.getId(), e.getClass().getSimpleName());
                }
            }
        } catch (Exception e) {
            log.error("[whatsapp-lembrete] backfill falhou empresa={} erroTipo={}",
                    empresaId, e.getClass().getSimpleName());
        }
    }

    /**
     * Template alterado AFTER_COMMIT: atualiza somente pendencias seguras
     * (PENDENTE e ENVIANDO com sendStartedAt == null) com lock pessimista por
     * linha. Linhas em corrida com o worker sao puladas sem derrubar o lote.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTemplateAlterado(WhatsAppConfiguracaoService.TemplateAlteradoEvent event) {
        Long empresaId = event.empresaId();

        List<WhatsAppNotificacaoEntity> candidatas = notificacaoRepository.findByEmpresaIdAndStatusIn(empresaId,
            List.of(WhatsAppStatusNotificacao.PENDENTE, WhatsAppStatusNotificacao.ENVIANDO));

        for (WhatsAppNotificacaoEntity entidade : candidatas) {
              WhatsAppNotificacaoEntity lockEntidade;
              try {
                  lockEntidade = self.bloquearParaAtualizacaoTemplate(empresaId, entidade.getId());
              } catch (Exception e) {
                  // Linha disputada com o worker: pula sem derrubar o lote.
                  log.warn("[whatsapp-lembrete] template pulou notificacao em corrida notificacao={} erroTipo={}",
                          entidade.getId(), e.getClass().getSimpleName());
                  continue;
              }
              if (lockEntidade == null) continue;

              if (lockEntidade.getStatus() != WhatsAppStatusNotificacao.PENDENTE
                      && !(lockEntidade.getStatus() == WhatsAppStatusNotificacao.ENVIANDO
                              && lockEntidade.getSendStartedAt() == null)) {
                  continue; // ENVIADO/CANCELADO/FALHOU/terminal ou envio ja iniciado: nunca toca.
              }

              try {
                  self.aplicarNovoTemplate(empresaId, lockEntidade.getId());
              } catch (Exception e) {
                  log.warn("[whatsapp-lembrete] template pulou notificacao={} erroTipo={}",
                          entidade.getId(), e.getClass().getSimpleName());
              }
         }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WhatsAppNotificacaoEntity bloquearParaAtualizacaoTemplate(Long empresaId, Long notificacaoId) {
        return notificacaoRepository.findByIdAndEmpresaIdForUpdate(notificacaoId, empresaId)
                .orElse(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aplicarNovoTemplate(Long empresaId, Long notificacaoId) {
        WhatsAppNotificacaoEntity lockEntidade = notificacaoRepository
                .findByIdAndEmpresaIdForUpdate(notificacaoId, empresaId)
                .orElse(null);
        if (lockEntidade == null) {
            return;
        }
        if (lockEntidade.getStatus() != WhatsAppStatusNotificacao.PENDENTE
                && !(lockEntidade.getStatus() == WhatsAppStatusNotificacao.ENVIANDO
                        && lockEntidade.getSendStartedAt() == null)) {
            return;
        }
        if (lockEntidade.getTipo() == WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO
                && lockEntidade.getStatus() == WhatsAppStatusNotificacao.ENVIANDO
                && lockEntidade.getSendStartedAt() != null) {
            return;
        }
        if (lockEntidade.getAgendamento() != null) {
            Optional<AgendamentoEntity> agendamento =
                    agendamentoRepository.findByIdAndEmpresaId(lockEntidade.getAgendamento().getId(), empresaId);
            if (agendamento.isPresent()) {
                VersaoLembrete versao = calcularVersao(agendamento.get());
                if (versao != null) {
                    lockEntidade.setMessageBody(versao.mensagem());
                    notificacaoRepository.save(lockEntidade);
                }
            }
        }
    }
}

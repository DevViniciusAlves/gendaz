package com.minhaempresa.gendaz.whatsapp.service;

import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.shared.PhoneNumberService;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppNotificacaoEntity;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppStatusNotificacao;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppTipoNotificacao;
import com.minhaempresa.gendaz.whatsapp.policy.WhatsAppPlanoPolicy;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppNotificacaoRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final AssinaturaService assinaturaService;
    private final WhatsAppConfiguracaoService configuracaoService;
    private final WhatsAppNotificacaoService notificacaoService;
    private final WhatsAppNotificacaoRepository notificacaoRepository;
    private final WhatsAppFilaService filaService;
    private final WhatsAppQuotaService quotaService;
    private final PhoneNumberService phoneNumberService;
    private final WhatsAppClock clock;

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
        VersaoLembrete versao = calcularVersao(agendamento);        cancelarVersoesAntigas(empresaId, agendamentoId, versao == null ? null : versao.chave());
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
        for (WhatsAppNotificacaoEntity entidade : ligadas) {
            if (entidade.getTipo() != WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO) {
                continue;
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
                log.info("[whatsapp-lembrete] reminder cancelado notificacao={}", entidade.getId());
            } else if (entidade.getStatus() == WhatsAppStatusNotificacao.ENVIANDO) {
                log.info("[whatsapp-lembrete] envio possivelmente em curso, mantido notificacao={}",
                        entidade.getId());
            }
        }
    }

    /**
     * Protecao defensiva do worker antes do inicio real da chamada externa.
     * Vale somente para LEMBRETE_AGENDAMENTO com agendamento vinculado;
     * demais casos passam. Retorna false quando cancelou (com liberacao).
     */
    @Transactional
    public boolean revalidarParaEnvio(Long notificacaoId) {
        Optional<WhatsAppNotificacaoEntity> atual = notificacaoRepository.findById(notificacaoId);
        if (atual.isEmpty()) {
            return false;
        }
        WhatsAppNotificacaoEntity entidade = atual.get();
        if (entidade.getTipo() != WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO) {
            return true;
        }
        if (entidade.getStatus() != WhatsAppStatusNotificacao.ENVIANDO) {
            return false;
        }
        LocalDateTime agora = clock.agoraUtc();
        if (entidade.getExpiresAt() != null && agora.isAfter(entidade.getExpiresAt())) {
            expirar(entidade);
            return false;
        }
        if (entidade.getAgendamento() == null) {
            return true;
        }
        Long empresaId = entidade.getEmpresa().getId();
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

    /**
     * Expiracao no claim: apos expiresAt nao ha envio nem reserva. Em retry
     * com reserva existente, libera exatamente uma vez. Retorna true quando
     * expirou (chamador nao deve prosseguir).
     */
    boolean expiradoSeNecessario(WhatsAppNotificacaoEntity entidade, LocalDateTime agora) {
        if (entidade.getTipo() != WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO) {
            return false;
        }
        if (entidade.getExpiresAt() == null || !agora.isAfter(entidade.getExpiresAt())) {
            return false;
        }
        expirar(entidade);
        return true;
    }

    private void expirar(WhatsAppNotificacaoEntity entidade) {
        cancelarComCodigo(entidade, "REMINDER_EXPIRED");
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
        for (WhatsAppNotificacaoEntity entidade : ligadas) {
            if (entidade.getTipo() != WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO) {
                continue;
            }
            if (chaveAtual != null && chaveAtual.equals(entidade.getIdempotencyKey())) {
                continue;
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
            }
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
        String mensagem = "Olá, " + cliente.getNome() + "! Lembrete: seu atendimento na "
                + agendamento.getEmpresa().getNomeFantasia() + " está marcado para "
                + atendimentoLocal.format(DATA_BR) + " às " + atendimentoLocal.format(HORA_BR) + ".";
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

    private boolean clienteAtivo(ClienteEntity cliente) {
        return cliente != null && cliente.getStatus() == StatusCadastro.ATIVO;
    }
}

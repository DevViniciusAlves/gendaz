package com.minhaempresa.gendaz.whatsapp.service;

import com.minhaempresa.gendaz.whatsapp.WhatsAppProvider;
import com.minhaempresa.gendaz.whatsapp.WhatsAppSendResult;
import com.minhaempresa.gendaz.whatsapp.WhatsAppSendStatus;
import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppNotificacaoEntity;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppCategoriaCota;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppStatusNotificacao;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppNotificacaoRepository;
import jakarta.persistence.OptimisticLockException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Motor da fila WhatsApp (V1, sem broker: a tabela e a fila persistente).
 *
 * <p>Uma notificacao por vez: claim transacional, marcacao de inicio de
 * envio em transacao curta, chamada HTTP fora de transacao e finalizacao
 * em nova transacao. O lock de banco nunca e segurado durante Baileys/HTTP.
 *
 * <p>Cota: reservada uma vez antes da primeira tentativa e gravada na
 * notificacao (quotaCycleStart); sucesso confirma e falha terminal libera
 * exatamente nesse ciclo. Retry nunca reserva de novo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppEnvioWorker {

    static final int MAX_TENTATIVAS = 3;
    private static final int LOTE_RECUPERACAO = 100;

    private final WhatsAppNotificacaoRepository notificacaoRepository;
    private final WhatsAppQuotaService quotaService;
    private final WhatsAppProvider provider;
    private final WhatsAppClock clock;
    private final WhatsAppLembreteAgendamentoService lembreteService;

    private WhatsAppEnvioWorker self;

    /**
     * Auto-referencia via proxy: chamadas internas a metodos transacionais
     * (claimUm, marcarInicioEnvio, finalizar) precisam passar pelo proxy do
     * Spring, senao o @Transactional e ignorado e o lock do claim nao e
     * segurado ate o commit.
     */
    @Autowired
    public void setSelf(@Lazy WhatsAppEnvioWorker self) {
        this.self = self;
    }

    private record LeituraEnvio(Long empresaId, String recipient, String text, String requestId) {
    }

    public int processarLote(int tamanho) {
        int processados = 0;
        for (int i = 0; i < tamanho; i++) {
            Optional<Long> id;
            try {
                id = self.claimUm();
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException corrida) {
                // Corrida benigna perdida: nada foi decidido nem commitado.
                log.info("[whatsapp-worker] claim perdeu corrida");
                continue;
            } catch (Exception e) {
                // Claim perdido em falha real aborta so a tentativa: o proximo
                // item do lote (ou ciclo) continua normalmente.
                log.error("[whatsapp-worker] claim falhou. erroTipo={}", e.getClass().getSimpleName());
                continue;
            }
            if (id.isEmpty()) {
                continue;
            }
            try {
                self.processar(id.get());
                processados++;
            } catch (Exception e) {
                log.error("[whatsapp-worker] processamento falhou notificacao={}. erroTipo={}",
                        id.get(), e.getClass().getSimpleName());
            }
        }
        return processados;
    }

    /**
     * Claim de uma notificacao vencida: PENDENTE -&gt; ENVIANDO com attempts+1.
     * Na primeira tentativa reserva a cota; sem plano ou sem limite, cancela
     * sem chamar o provider e sem afetar agenda/CRM. Perder a corrida levanta
     * conflito otimista e nada commita: o chamador trata como claim vazio.
     */
    @Transactional
    public Optional<Long> claimUm() {
        return claimUmInterno();
    }

    private Optional<Long> claimUmInterno() {
        LocalDateTime agora = clock.agoraUtc();
        List<WhatsAppNotificacaoEntity> pendentes =
                notificacaoRepository.claimPendentes(agora, 1);
        if (pendentes.isEmpty()) {
            return Optional.empty();
        }
        WhatsAppNotificacaoEntity entidade = pendentes.get(0);
        entidade.setStatus(WhatsAppStatusNotificacao.ENVIANDO);
        entidade.setAttempts(entidade.getAttempts() + 1);
        entidade.setProcessingStartedAt(agora);
        entidade.setSendStartedAt(null);
        // Lembrete vencido nao reserva nem envia: cancela antes da cota.
        if (lembreteService.expiradoSeNecessario(entidade, agora)) {
            return Optional.empty();
        }
        if (!entidade.isQuotaReserved()) {
            WhatsAppCategoriaCota categoria = entidade.getTipo().categoria();
            ReservaCota reserva = quotaService.reservarNoCicloAtual(
                    entidade.getEmpresa().getId(), categoria);
            if (reserva.resultado() != WhatsAppReserva.RESERVADA) {
                entidade.setStatus(WhatsAppStatusNotificacao.CANCELADO);
                entidade.setLastError(reserva.resultado() == WhatsAppReserva.PLANO_SEM_WHATSAPP
                        ? "PLAN_NO_WHATSAPP"
                        : "QUOTA_EXCEEDED");
                entidade.setProcessingStartedAt(null);
                notificacaoRepository.save(entidade);
                log.info("[whatsapp-worker] notificacao cancelada sem cota notificacao={} motivo={}",
                        entidade.getId(), entidade.getLastError());
                return Optional.empty();
            }
            entidade.setQuotaReserved(true);
            entidade.setQuotaCycleStart(reserva.cicloInicio());
        }
        notificacaoRepository.save(entidade);
        return Optional.of(entidade.getId());
    }

    public void processar(Long notificacaoId) {
        // Protecao defensiva de lembrete antes da chamada externa (CRM passa).
        if (!lembreteService.revalidarParaEnvio(notificacaoId)) {
            return;
        }
        if (!self.marcarInicioEnvio(notificacaoId)) {
            return;
        }
        LeituraEnvio leitura = self.lerParaEnvio(notificacaoId);
        if (leitura == null) {
            return;
        }
        WhatsAppSendResult resultado;
        try {
            resultado = provider.enviarTexto(
                    String.valueOf(leitura.empresaId()),
                    leitura.recipient(),
                    leitura.text(),
                    leitura.requestId());
        } catch (Exception e) {
            // O provider nao deveria lancar, mas o worker nunca pode morrer:
            // ambiguo por definicao, sem retry automatico.
            log.error("[whatsapp-worker] provider lancou excecao. erroTipo={}", e.getClass().getSimpleName());
            resultado = WhatsAppSendResult.erro(WhatsAppSendStatus.DELIVERY_UNKNOWN);
        }
        self.finalizar(notificacaoId, resultado);
    }

    /**
     * Marca o inicio real da chamada externa. Sem transacao propria: delega
     * a transacao curta interna e traduz derrota em corrida para false
     * (sem envio). Falso e sempre seguro: se a linha ainda estiver
     * ENVIANDO sem inicio, o recovery a revive depois.
     */
    public boolean marcarInicioEnvio(Long notificacaoId) {
        try {
            return self.marcarInicioEnvioInterno(notificacaoId);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException corrida) {
            // Alguem gravou primeiro (ex.: cancelamento venceu): sem envio.
            log.info("[whatsapp-worker] marco perdeu corrida notificacao={}", notificacaoId);
            return false;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean marcarInicioEnvioInterno(Long notificacaoId) {
        // Lock pessimista: reavalia status/sendStartedAt DEPOIS de adquirir
        // a linha. Se o cancelamento venceu, retorna false e o provider
        // nunca e chamado. Lock curto: sem HTTP aqui dentro.
        Optional<WhatsAppNotificacaoEntity> atual =
                notificacaoRepository.findByIdForUpdate(notificacaoId);
        if (atual.isEmpty()
                || atual.get().getStatus() != WhatsAppStatusNotificacao.ENVIANDO
                || atual.get().getSendStartedAt() != null) {
            return false;
        }
        atual.get().setSendStartedAt(clock.agoraUtc());
        notificacaoRepository.save(atual.get());
        return true;
    }

    @Transactional(readOnly = true)
    public LeituraEnvio lerParaEnvio(Long notificacaoId) {
        return notificacaoRepository.findById(notificacaoId)
                .filter(e -> e.getStatus() == WhatsAppStatusNotificacao.ENVIANDO)
                .map(e -> new LeituraEnvio(
                        e.getEmpresa().getId(),
                        e.getRecipient(),
                        e.getMessageBody(),
                        e.getIdempotencyKey()))
                .orElse(null);
    }

    @Transactional
    public void finalizar(Long notificacaoId, WhatsAppSendResult resultado) {
        try {
            self.finalizarInterno(notificacaoId, resultado);
            return;
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException corrida) {
            // Perdeu: outra gravacao venceu. Verifica o estado vencedor em
            // nova transacao em vez de insistir na morta.
        }
        self.verificarAposConflito(notificacaoId, resultado);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void verificarAposConflito(Long notificacaoId, WhatsAppSendResult resultado) {
        Optional<WhatsAppNotificacaoEntity> atual =
                notificacaoRepository.findByIdForUpdate(notificacaoId);
        if (atual.isEmpty()) {
            return;
        }
        WhatsAppStatusNotificacao estado = atual.get().getStatus();
        if (estado != WhatsAppStatusNotificacao.ENVIANDO) {
            // Terminal ou devolvida a fila por quem venceu: nada a fazer.
            log.info("[whatsapp-worker] finalizacao apos conflito sem efeito notificacao={} estado={}",
                    notificacaoId, estado);
            return;
        }
        try {
            self.finalizarInterno(notificacaoId, resultado);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException segunda) {
            // Patologico (duas derrotas seguidas): nao insiste; o recovery
            // por stale revisita a linha depois.
            log.error("[whatsapp-worker] finalizacao apos conflito abortada notificacao={}", notificacaoId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizarInterno(Long notificacaoId, WhatsAppSendResult resultado) {
        // Lock + reavaliacao: so ENVIANDO pode ser finalizado. Qualquer outro
        // estado (ENVIADO, PENDENTE, CANCELADO, FALHOU) nao e sobrescrito:
        // a finalizacao nao altera cota nem historico nesses casos.
        Optional<WhatsAppNotificacaoEntity> atual =
                notificacaoRepository.findByIdForUpdate(notificacaoId);
        if (atual.isEmpty()) {
            return;
        }
        WhatsAppNotificacaoEntity entidade = atual.get();
        if (entidade.getStatus() != WhatsAppStatusNotificacao.ENVIANDO) {
            return;
        }
        WhatsAppSendStatus status = resultado == null ? WhatsAppSendStatus.DELIVERY_UNKNOWN : resultado.getStatus();
        if (status == WhatsAppSendStatus.SENT) {
            concluirSucesso(entidade, resultado);
            return;
        }
        if (status.isRetryable()) {
            if (entidade.getAttempts() >= MAX_TENTATIVAS) {
                falhar(entidade, status == WhatsAppSendStatus.SESSION_NOT_CONNECTED
                        ? "SESSION_NOT_CONNECTED_MAX_RETRIES"
                        : status == WhatsAppSendStatus.DELIVERY_UNKNOWN
                                ? "DELIVERY_UNKNOWN_MAX_RETRIES"
                                : "SERVICE_UNAVAILABLE_MAX_RETRIES");
                return;
            }
            entidade.setStatus(WhatsAppStatusNotificacao.PENDENTE);
            entidade.setNextAttemptAt(clock.agoraUtc().plusMinutes(entidade.getAttempts() == 1 ? 1 : 5));
            entidade.setProcessingStartedAt(null);
            entidade.setSendStartedAt(null);
            notificacaoRepository.save(entidade);
            log.info("[whatsapp-worker] retry agendado notificacao={} tentativa={}",
                    entidade.getId(), entidade.getAttempts());
            return;
        }
        falhar(entidade, status.name());
    }

    private void concluirSucesso(WhatsAppNotificacaoEntity entidade, WhatsAppSendResult resultado) {
        entidade.setStatus(WhatsAppStatusNotificacao.ENVIADO);
        entidade.setSentAt(clock.agoraUtc());
        entidade.setProviderMessageId(resultado == null ? null : resultado.getMessageId());
        entidade.setLastError(null);
        entidade.setProcessingStartedAt(null);
        entidade.setSendStartedAt(null);
        if (entidade.isQuotaReserved()) {
            quotaService.confirmarEnvioNoCiclo(
                    entidade.getEmpresa().getId(),
                    entidade.getTipo().categoria(),
                    entidade.getQuotaCycleStart());
            entidade.setQuotaReserved(false);
        }
        notificacaoRepository.save(entidade);
    }

    private void falhar(WhatsAppNotificacaoEntity entidade, String codigo) {
        entidade.setStatus(WhatsAppStatusNotificacao.FALHOU);
        entidade.setLastError(codigo);
        entidade.setProcessingStartedAt(null);
        entidade.setSendStartedAt(null);
        if (entidade.isQuotaReserved()) {
            quotaService.liberarReservaNoCiclo(
                    entidade.getEmpresa().getId(),
                    entidade.getTipo().categoria(),
                    entidade.getQuotaCycleStart());
            entidade.setQuotaReserved(false);
        }
        notificacaoRepository.save(entidade);
        log.info("[whatsapp-worker] notificacao falhou notificacao={} codigo={}", entidade.getId(), codigo);
    }

    /**
     * Recupera ENVIANDO presos de crash/restart. Sem sendStartedAt, o worker
     * claimou mas nao registrou inicio da chamada externa: seguro voltar a
     * PENDENTE mantendo a reserva. Com sendStartedAt, o envio pode ter
     * acontecido: FALHOU/DELIVERY_UNKNOWN sem retry, liberando a reserva.
     */
    @Transactional
    public int recuperarPresos(long staleSeconds) {
        LocalDateTime limite = clock.agoraUtc().minusSeconds(staleSeconds);
        List<WhatsAppNotificacaoEntity> presos = notificacaoRepository.claimPresos(limite, LOTE_RECUPERACAO);
        for (WhatsAppNotificacaoEntity entidade : presos) {
            if (entidade.getSendStartedAt() == null) {
                // Antes de refileirar, reavalia expiracao/opt-out/plano/
                // telefone: se algo nao permite mais envio, a revalidacao
                // cancela com o codigo adequado em vez de refileirar.
                if (!lembreteService.revalidarParaEnvio(entidade.getId())) {
                    continue;
                }
                Optional<WhatsAppNotificacaoEntity> atual =
                        notificacaoRepository.findById(entidade.getId());
                if (atual.isEmpty()
                        || atual.get().getStatus() != WhatsAppStatusNotificacao.ENVIANDO) {
                    continue;
                }
                atual.get().setStatus(WhatsAppStatusNotificacao.PENDENTE);
                atual.get().setProcessingStartedAt(null);
                notificacaoRepository.save(atual.get());
                log.info("[whatsapp-worker] preso recuperado para PENDENTE notificacao={}", entidade.getId());
            } else {
                falhar(entidade, "DELIVERY_UNKNOWN");
            }
        }
        return presos.size();
    }
}

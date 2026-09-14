package com.minhaempresa.gendaz.whatsapp.service;

import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppNotificacaoEntity;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppStatusNotificacao;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppTipoNotificacao;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppNotificacaoRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Criacao idempotente de notificacoes WhatsApp. A unicidade de
 * (empresa, idempotency_key) e garantida pela constraint do banco, nao por
 * verificacao previa em memoria: sob concorrencia, a perdedora viola a
 * constraint em transacao propria (que sofre rollback isolado) e recupera
 * o registro vencedor em outra transacao propria.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppNotificacaoService {

    private final WhatsAppNotificacaoRepository notificacaoRepository;
    private final WhatsAppNotificacaoInitializer initializer;
    private final WhatsAppQuotaService quotaService;

    /**
     * Cria ou recupera a notificacao para (empresa, chave). Nunca cria
     * duplicata; nunca falha em caso de corrida: a vencedora insere e a
     * perdedora recebe o registro existente.
     */
    public WhatsAppNotificacaoEntity criarIdempotente(
            Long empresaId,
            WhatsAppTipoNotificacao tipo,
            String idempotencyKey,
            LocalDateTime scheduledAt,
            Long clienteId,
            Long agendamentoId) {
        Optional<WhatsAppNotificacaoEntity> existente =
                notificacaoRepository.findByEmpresaIdAndIdempotencyKey(empresaId, idempotencyKey);
        if (existente.isPresent()) {
            return existente.get();
        }
        try {
            return criar(empresaId, tipo, idempotencyKey, scheduledAt, clienteId, agendamentoId);
        } catch (WhatsAppNotificacaoDuplicadaException duplicada) {
            return initializer.buscar(empresaId, idempotencyKey)
                    .orElseThrow(() -> duplicada);
        }
    }

    public WhatsAppNotificacaoEntity criar(
            Long empresaId,
            WhatsAppTipoNotificacao tipo,
            String idempotencyKey,
            LocalDateTime scheduledAt,
            Long clienteId,
            Long agendamentoId) {
        return initializer.inserir(empresaId, tipo, idempotencyKey, scheduledAt,
                clienteId, agendamentoId, null, null, null);
    }

    /**
     * Variante com conteudo operacional (fila de envio). Se a notificacao ja
     * existe para (empresa, chave), ela e reutilizada como esta: nunca
     * sobrescreve o payload existente.
     */
    public WhatsAppNotificacaoEntity criarIdempotenteConteudo(
            Long empresaId,
            WhatsAppTipoNotificacao tipo,
            String idempotencyKey,
            LocalDateTime scheduledAt,
            Long clienteId,
            Long agendamentoId,
            String recipient,
            String messageBody) {
        return criarIdempotenteConteudo(empresaId, tipo, idempotencyKey, scheduledAt,
                clienteId, agendamentoId, recipient, messageBody, null);
    }

    public WhatsAppNotificacaoEntity criarIdempotenteConteudo(
            Long empresaId,
            WhatsAppTipoNotificacao tipo,
            String idempotencyKey,
            LocalDateTime scheduledAt,
            Long clienteId,
            Long agendamentoId,
            String recipient,
            String messageBody,
            LocalDateTime expiresAt) {
        Optional<WhatsAppNotificacaoEntity> existente =
                notificacaoRepository.findByEmpresaIdAndIdempotencyKey(empresaId, idempotencyKey);
        if (existente.isPresent()) {
            return existente.get();
        }
        try {
            return initializer.inserir(empresaId, tipo, idempotencyKey, scheduledAt,
                    clienteId, agendamentoId, recipient, messageBody, expiresAt);
        } catch (WhatsAppNotificacaoDuplicadaException duplicada) {
            return initializer.buscar(empresaId, idempotencyKey)
                    .orElseThrow(() -> duplicada);
        }
    }

    @Transactional(readOnly = true)
    public Optional<WhatsAppNotificacaoEntity> buscarPorId(Long empresaId, Long notificacaoId) {
        return notificacaoRepository.findByIdAndEmpresaId(notificacaoId, empresaId);
    }

    /**
     * Cancela pendencias seguras de um cliente (ex.: opt-out true-&gt;false):
     * PENDENTE e ENVIANDO sem sendStartedAt, de qualquer tipo, com liberacao
     * de reserva e lock por linha. ENVIADO/FALHOU/CANCELADO preservados;
     * ENVIANDO com chamada externa possivel nao e tocado (o worker conclui).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int cancelarPendenciasCliente(Long empresaId, Long clienteId) {
        List<WhatsAppNotificacaoEntity> candidatas =
                notificacaoRepository.findByEmpresaIdAndClienteId(empresaId, clienteId);
        int canceladas = 0;
        for (WhatsAppNotificacaoEntity candidata : candidatas) {
            Optional<WhatsAppNotificacaoEntity> atual = notificacaoRepository
                    .findByIdAndEmpresaIdForUpdate(candidata.getId(), empresaId);
            if (atual.isEmpty()) {
                continue;
            }
            WhatsAppNotificacaoEntity entidade = atual.get();
            if (entidade.getStatus() == WhatsAppStatusNotificacao.PENDENTE
                    || (entidade.getStatus() == WhatsAppStatusNotificacao.ENVIANDO
                            && entidade.getSendStartedAt() == null)) {
                if (entidade.isQuotaReserved()) {
                    quotaService.liberarReservaNoCiclo(
                            empresaId,
                            entidade.getTipo().categoria(),
                            entidade.getQuotaCycleStart());
                    entidade.setQuotaReserved(false);
                }
                entidade.setStatus(WhatsAppStatusNotificacao.CANCELADO);
                entidade.setLastError("WHATSAPP_OPT_OUT");
                entidade.setProcessingStartedAt(null);
                entidade.setSendStartedAt(null);
                entidade.setNextAttemptAt(null);
                notificacaoRepository.save(entidade);
                canceladas++;
                log.info("[whatsapp] pendencia cancelada por opt-out notificacao={}", entidade.getId());
            }
        }
        return canceladas;
    }
}

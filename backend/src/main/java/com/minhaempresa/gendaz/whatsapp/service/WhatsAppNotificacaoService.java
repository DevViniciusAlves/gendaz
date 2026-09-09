package com.minhaempresa.gendaz.whatsapp.service;

import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppNotificacaoEntity;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppTipoNotificacao;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppNotificacaoRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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
public class WhatsAppNotificacaoService {

    private final WhatsAppNotificacaoRepository notificacaoRepository;
    private final WhatsAppNotificacaoInitializer initializer;

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
}

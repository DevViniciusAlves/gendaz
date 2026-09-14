package com.minhaempresa.gendaz.agendamento.event;

/**
 * Sincronizacao WhatsApp apos mudanca de agendamento. Contem apenas IDs
 * tecnicos (sem mensagem, telefone ou PII). Publicado dentro da transacao
 * do dominio e processado via AFTER_COMMIT: falha do WhatsApp jamais
 * provoca rollback da operacao de agenda.
 */
public record AgendamentoWhatsAppSyncEvent(
        Long agendamentoId,
        Long empresaId,
        Acao acao
) {
    public enum Acao {
        SINCRONIZAR,
        CANCELAR
    }
}

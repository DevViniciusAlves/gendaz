package com.minhaempresa.gendaz.whatsapp.service;

/**
 * Sinal de dominio: a notificacao ja existe para (empresa, chave). O nucleo
 * apenas garante unicidade; a composicao final das chaves de agendamento,
 * Resgate e Reconexao sera definida quando esses fluxos forem integrados.
 */
public class WhatsAppNotificacaoDuplicadaException extends RuntimeException {
    public WhatsAppNotificacaoDuplicadaException(String idempotencyKey) {
        super("Notificacao WhatsApp ja existe para esta chave de idempotencia.");
    }
}

package com.minhaempresa.gendaz.whatsapp.enums;

/**
 * Estado persistente de uma notificacao WhatsApp. As transicoes entre
 * estados serao implementadas pelo motor de fila/envio (Fase 5); aqui
 * existe apenas o modelo necessario para historico e idempotencia.
 */
public enum WhatsAppStatusNotificacao {
    PENDENTE,
    ENVIANDO,
    ENVIADO,
    FALHOU,
    CANCELADO
}

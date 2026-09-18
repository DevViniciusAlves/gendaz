package com.minhaempresa.gendaz.whatsapp.enums;

/**
 * Estado persistente de uma notificacao WhatsApp. As transicoes entre
 * estados serao implementadas pelo motor de fila/envio (Fase 5); aqui
 * existe apenas o modelo necessario para historico e idempotencia.
 *
 * <p>Maquina de estados (cota CRM/lembretes):
 * PENDENTE -&gt; ENVIANDO -&gt; sendMessage + messageId -&gt; AGUARDANDO_ENTREGA
 * -&gt; DELIVERY_ACK (ou READ/PLAYED posteriores) -&gt; ENVIADO.
 *
 * <p>AGUARDANDO_ENTREGA significa "aceito pelo provider, aguardando prova de
 * entrega": providerMessageId persistido, quotaReserved continua true,
 * crmReservados continua 1 e crmEnviados NAO aumenta. Somente a confirmacao
 * de entrega converte a reserva em enviado. Nunca e claimado para reenvio,
 * nunca dispara sendMessage de novo e nunca e cancelado como pendencia
 * (a mensagem ja saiu para o provider).
 */
public enum WhatsAppStatusNotificacao {
    PENDENTE,
    ENVIANDO,
    AGUARDANDO_ENTREGA,
    ENVIADO,
    FALHOU,
    CANCELADO
}

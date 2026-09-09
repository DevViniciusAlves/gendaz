package com.minhaempresa.gendaz.whatsapp;

/**
 * Resultado tipado do envio de texto: classificacao + providerMessageId
 * (message.key.id do Baileys, quando disponivel).
 *
 * <p>Separado de {@link WhatsAppResult}/{@link WhatsAppOperationStatus} para
 * que os estados especificos de envio (retryable, terminal, ambiguo)
 * continuem distinguiveis pelo motor da fila.
 */
public final class WhatsAppSendResult {

    private final WhatsAppSendStatus status;
    private final String messageId;

    private WhatsAppSendResult(WhatsAppSendStatus status, String messageId) {
        this.status = status;
        this.messageId = messageId;
    }

    public static WhatsAppSendResult sent(String messageId) {
        return new WhatsAppSendResult(WhatsAppSendStatus.SENT, messageId);
    }

    public static WhatsAppSendResult erro(WhatsAppSendStatus status) {
        if (status == WhatsAppSendStatus.SENT) {
            throw new IllegalArgumentException("Use sent() para SENT.");
        }
        return new WhatsAppSendResult(status, null);
    }

    public WhatsAppSendStatus getStatus() {
        return status;
    }

    public String getMessageId() {
        return messageId;
    }

    public boolean isSent() {
        return status == WhatsAppSendStatus.SENT;
    }
}

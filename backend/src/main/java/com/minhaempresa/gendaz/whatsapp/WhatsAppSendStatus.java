package com.minhaempresa.gendaz.whatsapp;

/**
 * Classificacao do resultado de um envio de texto.
 *
 * <p>O motor precisa distinguir falha que pode tentar de novo (retryable) de
 * falha definitiva ou ambigua. Regra critica: timeout (ou qualquer resposta
 * onde o envio pode ter acontecido e a confirmacao se perdeu) nunca gera
 * retry automatico {@link #DELIVERY_UNKNOWN} — a prioridade e nao duplicar
 * a mensagem no cliente.
 */
public enum WhatsAppSendStatus {
    SENT,
    SESSION_NOT_CONNECTED,
    SERVICE_UNAVAILABLE,
    INVALID_RECIPIENT,
    INVALID_MESSAGE,
    UNAUTHORIZED,
    NOT_CONFIGURED,
    DELIVERY_UNKNOWN,
    PROVIDER_ERROR;

    /**
     * Falha comprovadamente anterior ao envio: seguro reagendar.
     * Conexao recusada, host indisponivel, connect timeout e 409/503 do
     * whatsapp-service nunca indicam mensagem entregue.
     */
    public boolean isRetryable() {
        return this == SESSION_NOT_CONNECTED || this == SERVICE_UNAVAILABLE;
    }
}

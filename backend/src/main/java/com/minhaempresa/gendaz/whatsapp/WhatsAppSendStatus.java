package com.minhaempresa.gendaz.whatsapp;

/**
 * Classificacao do resultado de um envio de texto.
 *
 * <p>O motor precisa distinguir falha que pode tentar de novo (retryable) de
 * falha definitiva. {@link #DELIVERY_UNKNOWN} (ex.: read-timeout apos o Node
 * aceitar a request, tipico de cold start Render) gera retry LIMITADO: no
 * maximo {@code MAX_TENTATIVAS} do worker, com backoff persistido e
 * revalidacao (expiracao, opt-out, plano, telefone) antes de cada nova
 * tentativa. Sem isso, mensagens ficavam FALHOU na primeira lentidao do
 * Node e nunca mais eram enviadas. O risco residual de duplicata e contido
 * pela chave de idempotencia, pelo limite de tentativas e pela expiracao.
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
     * Falha que pode tentar de novo, sempre com limite de tentativas.
     * SESSION_NOT_CONNECTED e SERVICE_UNAVAILABLE sao comprovadamente
     * anteriores ao envio. DELIVERY_UNKNOWN e ambiguo (o envio pode ter
     * acontecido), mas sem retry limitado a mensagem seria perdida na
     * primeira lentidao/cold start do Node — por isso tambem reagenda,
     * contido por MAX_TENTATIVAS, backoff, expiracao e idempotencia.
     */
    public boolean isRetryable() {
        return this == SESSION_NOT_CONNECTED
                || this == SERVICE_UNAVAILABLE
                || this == DELIVERY_UNKNOWN;
    }
}

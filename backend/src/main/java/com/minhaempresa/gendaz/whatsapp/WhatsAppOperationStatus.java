package com.minhaempresa.gendaz.whatsapp;

/**
 * Resultado tipado das operacoes do {@link WhatsAppProvider}.
 *
 * <p>Preserva a diferenca entre "servico indisponivel" e "servico funcionando,
 * mas QR nao disponivel", em vez de reduzir tudo a {@code Optional.empty()}.
 */
public enum WhatsAppOperationStatus {
    SUCCESS,
    NOT_CONFIGURED,
    UNAVAILABLE,
    UNAUTHORIZED,
    INVALID_COMPANY_ID,
    QR_UNAVAILABLE,
    INTERNAL_ERROR
}

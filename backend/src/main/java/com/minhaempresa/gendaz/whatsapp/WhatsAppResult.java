package com.minhaempresa.gendaz.whatsapp;

/**
 * Envelope de retorno do provider: status da operacao + dado (quando SUCCESS).
 */
public final class WhatsAppResult<T> {

    private final WhatsAppOperationStatus status;
    private final T data;

    private WhatsAppResult(WhatsAppOperationStatus status, T data) {
        this.status = status;
        this.data = data;
    }

    public static <T> WhatsAppResult<T> success(T data) {
        return new WhatsAppResult<>(WhatsAppOperationStatus.SUCCESS, data);
    }

    public static <T> WhatsAppResult<T> erro(WhatsAppOperationStatus status) {
        if (status == WhatsAppOperationStatus.SUCCESS) {
            throw new IllegalArgumentException("Use success() para SUCCESS.");
        }
        return new WhatsAppResult<>(status, null);
    }

    public WhatsAppOperationStatus getStatus() {
        return status;
    }

    public T getData() {
        return data;
    }

    public boolean isSuccess() {
        return status == WhatsAppOperationStatus.SUCCESS;
    }
}

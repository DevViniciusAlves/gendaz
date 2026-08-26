package com.minhaempresa.gendaz.auth.idempotencia.exception;

/**
 * Erro controlado do fluxo de idempotencia de cadastro.
 * Sempre devolvido como HTTP 409 com um codigo tecnico estavel:
 * IDEMPOTENCY_IN_PROGRESS ou IDEMPOTENCY_KEY_REUSED.
 */
public class IdempotenciaException extends RuntimeException {

    private final String codigo;

    public IdempotenciaException(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}

package com.minhaempresa.agendapro.shared;

import java.time.LocalDateTime;

public record ApiErrorResponse(
        LocalDateTime dataHora,
        int status,
        String erro,
        String mensagem,
        String caminho
) {
    public static ApiErrorResponse of(int status, String erro, String mensagem, String caminho) {
        return new ApiErrorResponse(LocalDateTime.now(), status, erro, mensagem, caminho);
    }
}

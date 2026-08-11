package com.minhaempresa.gendaz.shared;

import java.time.LocalDateTime;
import java.util.Map;

public record ValidationErrorResponse(
        LocalDateTime dataHora,
        int status,
        String erro,
        String mensagem,
        String caminho,
        Map<String, String> campos
) {
    public static ValidationErrorResponse of(String mensagem, String caminho, Map<String, String> campos) {
        return new ValidationErrorResponse(LocalDateTime.now(), 400, "Erro de validaÃ§Ã£o", mensagem, caminho, campos);
    }
}


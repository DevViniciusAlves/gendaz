package com.minhaempresa.gendaz.auditoria.dto;

import java.time.LocalDateTime;

public final class LogAtividadeDtos {

    private LogAtividadeDtos() {
    }

    public record LogAtividadeResponse(
            Long id,
            String nomeUsuario,
            String acao,
            String entidade,
            Long entidadeId,
            String detalhes,
            LocalDateTime dataHora
    ) {
    }
}

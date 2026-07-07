package com.minhaempresa.agendapro.agendamento.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class AgendaBlockedDayDtos {
    private AgendaBlockedDayDtos() {}

    public record BloquearDiaRequest(
            @NotNull Long empresaId,
            Long profissionalId,
            @NotNull LocalDate data,
            @Size(max = 255) String motivo
    ) {}

    public record DiaBloqueadoResponse(
            Long id,
            Long empresaId,
            Long profissionalId,
            String profissionalNome,
            LocalDate data,
            String motivo,
            LocalDateTime dataCriacao
    ) {}
}

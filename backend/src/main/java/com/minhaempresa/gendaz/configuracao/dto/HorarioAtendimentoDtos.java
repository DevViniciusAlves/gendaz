package com.minhaempresa.gendaz.configuracao.dto;

import com.minhaempresa.gendaz.horarioatendimento.enums.DiaSemanaAtendimento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.util.List;

public final class HorarioAtendimentoDtos {
    private HorarioAtendimentoDtos() {}

    public record HorarioAtendimentoResponse(
            Long id,
            DiaSemanaAtendimento diaSemana,
            String diaLabel,
            boolean ativo,
            LocalTime horaInicio,
            LocalTime horaFim,
            LocalTime intervaloInicio,
            LocalTime intervaloFim
    ) {}

    public record HorarioAtendimentoItemRequest(
            @NotNull DiaSemanaAtendimento diaSemana,
            @NotNull Boolean ativo,
            LocalTime horaInicio,
            LocalTime horaFim,
            LocalTime intervaloInicio,
            LocalTime intervaloFim
    ) {}

    public record SalvarHorariosAtendimentoRequest(
            @NotEmpty List<@Valid HorarioAtendimentoItemRequest> horarios
    ) {}
}


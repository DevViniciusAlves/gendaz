package com.minhaempresa.gendaz.admin.dto;

import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public final class AdminAssinaturaDtos {
    private AdminAssinaturaDtos() {}

    public record AssinaturaAdminResponse(
            Long id,
            String planoNome,
            Long planoId,
            StatusAssinatura status,
            LocalDate dataInicio,
            LocalDate dataFim,
            long dias,
            boolean isAtual,
            long diasRestantes
    ) {}

    public record EditarAssinaturaRequest(
            Long planoId,
            Integer dias,
            LocalDate dataInicio,
            LocalDate dataFim,
            StatusAssinatura status
    ) {}

    public record CriarAssinaturaRequest(
            @NotNull(message = "Selecione um plano.") Long planoId,
            @Min(value = 1, message = "Dias minimos: 1.") Integer dias,
            LocalDate dataInicio,
            LocalDate dataFim,
            StatusAssinatura status
    ) {}
}


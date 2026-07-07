package com.minhaempresa.agendapro.admin.dto;

import com.minhaempresa.agendapro.assinatura.enums.StatusAssinatura;
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
}

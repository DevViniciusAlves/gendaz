package com.minhaempresa.gendaz.assinatura.dto;

import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import java.time.LocalDate;

public final class AssinaturaDtos {
    private AssinaturaDtos() {}

    public record AssinaturaResponse(
            Long id,
            Long empresaId,
            String empresaNome,
            Long planoId,
            String planoNome,
            StatusAssinatura status,
            LocalDate dataInicio,
            LocalDate dataFim,
            LocalDate dataInicioTeste,
            LocalDate dataFimTeste,
            long diasRestantes
    ) {}
}


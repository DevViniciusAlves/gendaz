package com.minhaempresa.agendapro.plano.dto;

import com.minhaempresa.agendapro.plano.enums.StatusPlano;
import java.math.BigDecimal;

public final class PlanoDtos {
    private PlanoDtos() {}

    public record PlanoResponse(Long id, String nome, String descricao, BigDecimal valorMensal, StatusPlano status) {}
}

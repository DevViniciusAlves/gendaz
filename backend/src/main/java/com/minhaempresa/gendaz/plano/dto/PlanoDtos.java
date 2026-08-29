package com.minhaempresa.gendaz.plano.dto;

import com.minhaempresa.gendaz.plano.enums.StatusPlano;
import java.math.BigDecimal;

public final class PlanoDtos {
    private PlanoDtos() {}

    public record PlanoResponse(Long id, String nome, String descrição, BigDecimal valorMensal, StatusPlano status) {}
}


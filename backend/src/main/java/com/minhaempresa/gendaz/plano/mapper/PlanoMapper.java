package com.minhaempresa.gendaz.plano.mapper;

import com.minhaempresa.gendaz.plano.dto.PlanoDtos.PlanoResponse;
import com.minhaempresa.gendaz.plano.entity.PlanoEntity;

public class PlanoMapper {
    public PlanoResponse toResponse(PlanoEntity plano) {
        return new PlanoResponse(plano.getId(), plano.getNome(), plano.getDescricao(), plano.getValorMensal(), plano.getStatus());
    }
}


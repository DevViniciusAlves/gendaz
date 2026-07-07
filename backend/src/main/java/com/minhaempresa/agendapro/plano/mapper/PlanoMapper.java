package com.minhaempresa.agendapro.plano.mapper;

import com.minhaempresa.agendapro.plano.dto.PlanoDtos.PlanoResponse;
import com.minhaempresa.agendapro.plano.entity.PlanoEntity;

public class PlanoMapper {
    public PlanoResponse toResponse(PlanoEntity plano) {
        return new PlanoResponse(plano.getId(), plano.getNome(), plano.getDescricao(), plano.getValorMensal(), plano.getStatus());
    }
}

package com.minhaempresa.gendaz.agendamento.mapper;

import com.minhaempresa.gendaz.agendamento.dto.AgendaBlockedDayDtos.DiaBloqueadoResponse;
import com.minhaempresa.gendaz.agendamento.entity.AgendaBlockedDayEntity;

public class AgendaBlockedDayMapper {
    public DiaBloqueadoResponse toResponse(AgendaBlockedDayEntity entity) {
        Long profissionalId = entity.getProfissional() == null ? null : entity.getProfissional().getId();
        String profissionalNome = entity.getProfissional() == null ? null : entity.getProfissional().getNome();
        return new DiaBloqueadoResponse(
                entity.getId(),
                entity.getEmpresa().getId(),
                profissionalId,
                profissionalNome,
                entity.getData(),
                entity.getMotivo(),
                entity.getDataCriacao()
        );
    }
}


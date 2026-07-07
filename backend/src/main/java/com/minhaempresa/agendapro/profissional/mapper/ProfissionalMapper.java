package com.minhaempresa.agendapro.profissional.mapper;

import com.minhaempresa.agendapro.profissional.dto.ProfissionalDtos.ProfissionalResponse;
import com.minhaempresa.agendapro.profissional.entity.ProfissionalEntity;

public class ProfissionalMapper {
    public ProfissionalResponse toResponse(ProfissionalEntity profissional) {
        return new ProfissionalResponse(
                profissional.getId(),
                profissional.getNome(),
                profissional.getEspecialidade(),
                profissional.getTelefone(),
                profissional.getStatus(),
                profissional.getEmpresa().getId(),
                profissional.isSistema()
        );
    }
}

package com.minhaempresa.gendaz.profissional.mapper;

import com.minhaempresa.gendaz.profissional.dto.ProfissionalDtos.ProfissionalResponse;
import com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity;
import java.util.LinkedHashSet;

public class ProfissionalMapper {
    public ProfissionalResponse toResponse(ProfissionalEntity profissional) {
        return new ProfissionalResponse(
                profissional.getId(),
                profissional.getNome(),
                profissional.getEspecialidade(),
                profissional.getTelefone(),
                profissional.getStatus(),
                profissional.getEmpresa().getId(),
                profissional.isSistema(),
                new LinkedHashSet<>(profissional.getDiasTrabalho())
        );
    }
}


package com.minhaempresa.gendaz.servico.mapper;

import com.minhaempresa.gendaz.servico.dto.ServicoDtos.ServicoResponse;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;

public class ServicoMapper {
    public ServicoResponse toResponse(ServicoEntity servico) {
        return new ServicoResponse(
                servico.getId(),
                servico.getNome(),
                servico.getDescricao(),
                servico.getDuracaoMinutos(),
                servico.getValor(),
                servico.getStatus(),
                servico.getEmpresa().getId()
        );
    }
}


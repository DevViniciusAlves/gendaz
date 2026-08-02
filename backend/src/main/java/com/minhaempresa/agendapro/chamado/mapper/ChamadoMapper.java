package com.minhaempresa.agendapro.chamado.mapper;

import com.minhaempresa.agendapro.chamado.dto.ChamadoDtos.ChamadoResponse;
import com.minhaempresa.agendapro.chamado.entity.ChamadoEntity;

public class ChamadoMapper {
    public ChamadoResponse toResponse(ChamadoEntity chamado) {
        return new ChamadoResponse(
                chamado.getId(),
                chamado.getAssunto(),
                chamado.getMensagem(),
                chamado.getPrioridade(),
                chamado.getOrigem(),
                chamado.getEmpresa().getId(),
                chamado.getEmpresa().getNomeFantasia(),
                chamado.getUsuario().getId(),
                chamado.getUsuario().getNome(),
                chamado.getStatus(),
                chamado.getDataCriacao(),
                chamado.getDataAtualizacao(),
                chamado.getResposta()
        );
    }
}

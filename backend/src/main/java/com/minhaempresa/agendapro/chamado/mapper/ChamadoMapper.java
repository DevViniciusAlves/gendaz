package com.minhaempresa.agendapro.chamado.mapper;

import com.minhaempresa.agendapro.chamado.dto.ChamadoDtos.ChamadoResponse;
import com.minhaempresa.agendapro.chamado.entity.ChamadoEntity;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.usuario.entity.UsuarioEntity;

public class ChamadoMapper {
    public ChamadoResponse toResponse(ChamadoEntity chamado) {
        if (chamado == null) {
            return null;
        }
        EmpresaEntity empresa = chamado.getEmpresa();
        UsuarioEntity usuario = chamado.getUsuario();
        return new ChamadoResponse(
                chamado.getId(),
                chamado.getAssunto(),
                chamado.getMensagem(),
                chamado.getPrioridade(),
                chamado.getOrigem(),
                empresa != null ? empresa.getId() : null,
                empresa != null ? empresa.getNomeFantasia() : null,
                usuario != null ? usuario.getId() : null,
                usuario != null ? usuario.getNome() : null,
                chamado.getStatus(),
                chamado.getDataCriacao(),
                chamado.getDataAtualizacao(),
                chamado.getResposta()
        );
    }
}

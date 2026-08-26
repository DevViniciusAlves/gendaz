package com.minhaempresa.gendaz.chamado.mapper;

import com.minhaempresa.gendaz.chamado.dto.ChamadoDtos.ChamadoResponse;
import com.minhaempresa.gendaz.chamado.entity.ChamadoEntity;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.meugendazacesso.entity.MeuGendazAcessoEntity;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;

public class ChamadoMapper {
    public ChamadoResponse toResponse(ChamadoEntity chamado) {
        if (chamado == null) {
            return null;
        }
        EmpresaEntity empresa = chamado.getEmpresa();
        UsuarioEntity usuario = chamado.getUsuario();
        MeuGendazAcessoEntity acesso = chamado.getMeuGendazAcesso();
        return new ChamadoResponse(
                chamado.getId(),
                chamado.getAssunto(),
                chamado.getMensagem(),
                chamado.getPrioridade(),
                chamado.getOrigem(),
                empresa != null ? empresa.getId() : null,
                empresa != null ? empresa.getNomeFantasia() : null,
                usuario != null ? usuario.getId() : (acesso != null ? acesso.getId() : null),
                usuario != null ? usuario.getNome() : (acesso != null ? acesso.getNome() : null),
                chamado.getStatus(),
                chamado.getDataCriacao(),
                chamado.getDataAtualizacao(),
                chamado.getResposta()
        );
    }
}


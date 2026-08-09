package com.minhaempresa.agendapro.usuario.mapper;

import com.minhaempresa.agendapro.usuario.dto.UsuarioDtos.UsuarioResponse;
import com.minhaempresa.agendapro.usuario.entity.UsuarioEntity;
import java.util.Objects;

public class UsuarioMapper {
    public UsuarioResponse toResponse(UsuarioEntity usuario) {
        Long empresaId = usuario.getEmpresa() == null ? null : usuario.getEmpresa().getId();
        String empresaNome = usuario.getEmpresa() == null ? null : usuario.getEmpresa().getNomeFantasia();
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getPerfil(),
                usuario.getStatus(),
                empresaId,
                empresaNome,
                Objects.equals(usuario.getPerfil(), com.minhaempresa.agendapro.usuario.enums.PerfilUsuario.DONO),
                usuario.getAceitouTermos(),
                usuario.getDataAceiteTermos(),
                usuario.getVersaoTermos(),
                usuario.getDataAceitePolitica(),
                usuario.getVersaoPolitica(),
                usuario.getDataCriacao(),
                usuario.getDataAtualizacao()
        );
    }
}

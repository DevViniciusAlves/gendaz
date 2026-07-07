package com.minhaempresa.agendapro.usuario.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.usuario.entity.UsuarioEntity;
import com.minhaempresa.agendapro.usuario.enums.PerfilUsuario;
import com.minhaempresa.agendapro.usuario.enums.StatusUsuario;
import org.junit.jupiter.api.Test;

class UsuarioMapperTest {
    private final UsuarioMapper mapper = new UsuarioMapper();

    @Test
    void deveMapearUsuarioSemExporSenha() {
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).nomeFantasia("Empresa Teste").build();
        UsuarioEntity usuario = UsuarioEntity.builder()
                .id(10L)
                .nome("Usuario Teste")
                .email("teste@agendapro.com")
                .senha("hash-interno")
                .perfil(PerfilUsuario.DONO)
                .status(StatusUsuario.ATIVO)
                .empresa(empresa)
                .build();

        var response = mapper.toResponse(usuario);

        assertEquals("Usuario Teste", response.nome());
        assertEquals(1L, response.empresaId());
    }
}

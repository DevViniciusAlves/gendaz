package com.minhaempresa.gendaz.usuario.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import org.junit.jupiter.api.Test;

class UsuarioMapperTest {
    private final UsuarioMapper mapper = new UsuarioMapper();

    @Test
    void deveMapearUsuarioSemExporSenha() {
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).nomeFantasia("Empresa Teste").build();
        UsuarioEntity usuario = UsuarioEntity.builder()
                .id(10L)
                .nome("Usuario Teste")
                .email("teste@Gendaz.com")
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


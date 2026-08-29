package com.minhaempresa.gendaz.lgpd.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.auth.service.UsuarioSessionService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.lgpd.dto.LgpdDtos.ReativarContaResponse;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import com.minhaempresa.gendaz.usuario.service.UsuarioService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LgpdServiceTest {

    @Mock
    private UsuarioService usuarioService;
    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private AssinaturaService assinaturaService;
    @Mock
    private UsuarioSessionService usuarioSessionService;

    @InjectMocks
    private LgpdService lgpdService;

    @Test
    @DisplayName("ENCERRADA + DONO + plano valido -> ATIVA")
    void reativarContaDonoComPlanoVigenteDeveAtivarEmpresa() {
        EmpresaEntity empresa = empresa(StatusEmpresa.ENCERRADA);
        UsuarioEntity dono = usuario(PerfilUsuario.DONO, empresa);
        when(usuarioService.buscarEntidade(dono.getId())).thenReturn(dono);
        when(empresaRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
        when(assinaturaService.buscarAtualPorEmpresa(empresa.getId())).thenReturn(Optional.of(com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity.builder().id(1L).build()));

        ReativarContaResponse response = lgpdService.reativarConta(dono.getId());

        assertEquals("ATIVA", response.statusEmpresa());
        assertEquals(StatusEmpresa.ATIVA, empresa.getStatus());
        verify(empresaRepository).save(empresa);
        verify(usuarioSessionService).encerrarSessao("sessão-restrita");
    }

    @Test
    @DisplayName("ENCERRADA + DONO + plano expirado -> INATIVA")
    void reativarContaDonoComPlanoExpiradoDeveColocarEmpresaInativa() {
        EmpresaEntity empresa = empresa(StatusEmpresa.ENCERRADA);
        UsuarioEntity dono = usuario(PerfilUsuario.DONO, empresa);
        when(usuarioService.buscarEntidade(dono.getId())).thenReturn(dono);
        when(empresaRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
        when(assinaturaService.buscarAtualPorEmpresa(empresa.getId())).thenReturn(Optional.empty());

        ReativarContaResponse response = lgpdService.reativarConta(dono.getId());

        assertEquals("INATIVA", response.statusEmpresa());
        assertEquals(StatusEmpresa.INATIVA, empresa.getStatus());
        verify(empresaRepository).save(empresa);
    }

    @Test
    @DisplayName("ENCERRADA + usuario que não e DONO -> rejeitado")
    void reativarContaNaoDonoDeveSerNegado() {
        EmpresaEntity empresa = empresa(StatusEmpresa.ENCERRADA);
        UsuarioEntity atendente = usuario(PerfilUsuario.ATENDENTE, empresa);
        when(usuarioService.buscarEntidade(atendente.getId())).thenReturn(atendente);

        assertThrows(BusinessException.class, () -> lgpdService.reativarConta(atendente.getId()));
    }

    @Test
    @DisplayName("BLOQUEADA -> não pode ser reativada pelo endpoint")
    void reativarContaDeEmpresaBloqueadaDeveSerNegada() {
        EmpresaEntity empresa = empresa(StatusEmpresa.BLOQUEADA);
        UsuarioEntity dono = usuario(PerfilUsuario.DONO, empresa);
        when(usuarioService.buscarEntidade(dono.getId())).thenReturn(dono);
        when(empresaRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));

        assertThrows(BusinessException.class, () -> lgpdService.reativarConta(dono.getId()));
    }

    @Test
    @DisplayName("INATIVA -> não esta encerrada, não pode ser reativada por este fluxo")
    void reativarContaDeEmpresaInativaDeveSerNegada() {
        EmpresaEntity empresa = empresa(StatusEmpresa.INATIVA);
        UsuarioEntity dono = usuario(PerfilUsuario.DONO, empresa);
        when(usuarioService.buscarEntidade(dono.getId())).thenReturn(dono);
        when(empresaRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));

        assertThrows(BusinessException.class, () -> lgpdService.reativarConta(dono.getId()));
    }

    private EmpresaEntity empresa(StatusEmpresa status) {
        return EmpresaEntity.builder()
                .id(1L)
                .nomeFantasia("Empresa Teste")
                .status(status)
                .build();
    }

    private UsuarioEntity usuario(PerfilUsuario perfil, EmpresaEntity empresa) {
        return UsuarioEntity.builder()
                .id(10L)
                .nome("Usuario Teste")
                .email("usuario@test.com")
                .senha("hash")
                .perfil(perfil)
                .status(StatusUsuario.ATIVO)
                .empresa(empresa)
                .sessaoAtiva("sessão-restrita")
                .build();
    }
}
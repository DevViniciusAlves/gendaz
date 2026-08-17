package com.minhaempresa.gendaz.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import com.minhaempresa.gendaz.auth.dto.LoginRequest;
import com.minhaempresa.gendaz.auth.dto.LoginResponse;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private UsuarioSessionService usuarioSessionService;

    @InjectMocks
    private AuthService authService;

    @Test
    void loginEmpresaInativaDeveCriarSessao() {
        UsuarioEntity usuario = usuario(StatusUsuario.ATIVO, StatusEmpresa.INATIVA);
        when(usuarioRepository.findByEmailAndEmpresaStatus(anyString(), any())).thenReturn(Optional.of(usuario));
        when(usuarioSessionService.renovarSessao(any())).thenReturn("token-valido");

        LoginResponse response = authService.login(new LoginRequest("usuario@test.com", "senha123"));

        assertEquals("ACCOUNT_INACTIVE", response.statusConta());
        assertNotNull(response.sessionToken());
    }

    @Test
    void loginEmpresaBloqueadaNaoDeveCriarSessao() {
        UsuarioEntity usuario = usuario(StatusUsuario.ATIVO, StatusEmpresa.BLOQUEADA);
        when(usuarioRepository.findByEmailAndEmpresaStatus(anyString(), any())).thenReturn(Optional.of(usuario));

        LoginResponse response = authService.login(new LoginRequest("usuario@test.com", "senha123"));

        assertEquals("ACCOUNT_INACTIVE", response.statusConta());
        assertNull(response.sessionToken());
    }

    @Test
    void loginEmpresaComAssinaturaExpiradaDeveCriarSessao() {
        UsuarioEntity usuario = usuario(StatusUsuario.ATIVO, StatusEmpresa.INATIVA);
        AssinaturaEntity assinatura = AssinaturaEntity.builder()
                .status(StatusAssinatura.EXPIRADA)
                .build();
        usuario.setAssinaturaAtual(assinatura);
        
        when(usuarioRepository.findByEmailAndEmpresaStatus(anyString(), any())).thenReturn(Optional.of(usuario));
        when(usuarioSessionService.renovarSessao(any())).thenReturn("token-valido");

        LoginResponse response = authService.login(new LoginRequest("usuario@test.com", "senha123"));

        assertEquals("ACCOUNT_INACTIVE", response.statusConta());
        assertNotNull(response.sessionToken());
    }

    private UsuarioEntity usuario(StatusUsuario statusUsuario, StatusEmpresa statusEmpresa) {
        return UsuarioEntity.builder()
                .id(1L)
                .nome("Usuario Teste")
                .email("usuario@test.com")
                .senha("senha-criptografada")
                .perfil(PerfilUsuario.DONO)
                .status(statusUsuario)
                .empresa(EmpresaEntity.builder()
                        .id(1L)
                        .nomeFantasia("Empresa Teste")
                        .status(statusEmpresa)
                        .build())
                .build();
    }
}
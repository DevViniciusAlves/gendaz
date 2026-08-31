package com.minhaempresa.gendaz.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.auth.dto.AuthDtos.LoginRequest;
import com.minhaempresa.gendaz.auth.dto.AuthDtos.LoginResponse;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.pagamento.service.PagamentoService;
import com.minhaempresa.gendaz.security.IpTrackingService;
import com.minhaempresa.gendaz.security.RecaptchaService;
import com.minhaempresa.gendaz.shared.security.SecurityMonitoringService;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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
    @Mock
    private PasswordService passwordService;
    @Mock
    private SecurityMonitoringService securityMonitoringService;
    @Mock
    private AssinaturaService assinaturaService;
    @Mock
    private PagamentoService pagamentoService;
    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private RecaptchaService recaptchaService;
    @Mock
    private IpTrackingService ipTrackingService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setup() {
        lenient().when(passwordService.matches(anyString(), anyString())).thenReturn(true);
        lenient().when(assinaturaService.buscarAtualPorEmpresa(any())).thenReturn(Optional.empty());
    }

    @Test
    void loginEmpresaInativaDeveCriarSessao() {
        UsuarioEntity usuario = usuario(StatusUsuario.ATIVO, StatusEmpresa.INATIVA);
        when(usuarioRepository.findUsuariosPainelByEmailIgnoreCase(anyString(), any())).thenReturn(java.util.List.of(usuario));
        when(usuarioSessionService.renovarSessao(any())).thenReturn("token-valido");

        LoginResponse response = authService.login(new LoginRequest("usuario@test.com", "senha123", null));

        assertEquals("ACCOUNT_INACTIVE", response.statusConta());
        assertEquals("PAGAMENTO_PENDENTE", response.motivoInatividade());
        assertNotNull(response.sessionToken());
    }

    @Test
    void loginEmpresaBloqueadaNaoDeveCriarSessao() {
        UsuarioEntity usuario = usuario(StatusUsuario.ATIVO, StatusEmpresa.BLOQUEADA);
        when(usuarioRepository.findUsuariosPainelByEmailIgnoreCase(anyString(), any())).thenReturn(java.util.List.of(usuario));

        LoginResponse response = authService.login(new LoginRequest("usuario@test.com", "senha123", null));

        assertEquals("ACCOUNT_INACTIVE", response.statusConta());
        assertEquals("ADMIN_SUSPENSAO", response.motivoInatividade());
        assertNull(response.sessionToken());
    }

    @Test
    void loginEmpresaEncerradaDeveCriarSessaoRestritaComMotivoProprio() {
        UsuarioEntity usuario = usuario(StatusUsuario.ATIVO, StatusEmpresa.ENCERRADA);
        when(usuarioRepository.findUsuariosPainelByEmailIgnoreCase(anyString(), any())).thenReturn(java.util.List.of(usuario));
        when(usuarioSessionService.renovarSessao(any())).thenReturn("token-restrito");

        LoginResponse response = authService.login(new LoginRequest("usuario@test.com", "senha123", null));

        assertEquals("ACCOUNT_INACTIVE", response.statusConta());
        assertEquals("CONTA_ENCERRADA", response.motivoInatividade());
        assertNotNull(response.sessionToken());
    }

    @Test
    void loginEmpresaComAssinaturaExpiradaDeveCriarSessao() {
        UsuarioEntity usuario = usuario(StatusUsuario.ATIVO, StatusEmpresa.INATIVA);
        
        when(usuarioRepository.findUsuariosPainelByEmailIgnoreCase(anyString(), any())).thenReturn(java.util.List.of(usuario));
        when(usuarioSessionService.renovarSessao(any())).thenReturn("token-valido");

        LoginResponse response = authService.login(new LoginRequest("usuario@test.com", "senha123", null));

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
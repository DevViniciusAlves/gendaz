package com.minhaempresa.gendaz.auth.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.admin.service.AdminAuditService;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.email.ResendEmailService;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.membresia.repository.MembresiaRepository;
import com.minhaempresa.gendaz.pagamento.service.PagamentoService;
import com.minhaempresa.gendaz.plano.service.PlanoService;
import com.minhaempresa.gendaz.profissional.service.ProfissionalService;
import com.minhaempresa.gendaz.security.IpTrackingService;
import com.minhaempresa.gendaz.security.RecaptchaService;
import com.minhaempresa.gendaz.shared.security.SecurityMonitoringService;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import com.minhaempresa.gendaz.usuario.service.UsuarioService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class AuthServiceSessionTest {
    @Mock UsuarioService usuarioService;
    @Mock UsuarioRepository usuarioRepository;
    @Mock EmpresaRepository empresaRepository;
    @Mock PlanoService planoService;
    @Mock AssinaturaService assinaturaService;
    @Mock PagamentoService pagamentoService;
    @Mock PasswordRecoveryService passwordRecoveryService;
    @Mock ResendEmailService resendEmailService;
    @Mock PasswordService passwordService;
    @Mock UsuarioSessionService usuarioSessionService;
    @Mock AdminAuditService auditService;
    @Mock RecaptchaService recaptchaService;
    @Mock IpTrackingService ipTrackingService;
    @Mock SecurityMonitoringService securityMonitoringService;
    @Mock ProfissionalService profissionalService;
    @Mock TransactionTemplate transactionTemplate;
    @Mock MembresiaRepository membresiaRepository;
    @InjectMocks AuthService authService;

    @Test
    void logoutDelegaEncerramentoSomentePorToken() {
        authService.logout("token-a");

        verify(usuarioSessionService).encerrarSessao("token-a");
    }

    @Test
    void trocarSenhaContinuaInvalidandoSessaoAtualPorToken() {
        UsuarioEntity usuario = UsuarioEntity.builder()
                .id(1L)
                .nome("Usuario")
                .email("usuario@gendaz.test")
                .senha("hash-atual")
                .perfil(PerfilUsuario.SUPER_ADMIN)
                .status(StatusUsuario.ATIVO)
                .sessaoAtiva("token-a")
                .build();
        when(usuarioRepository.findBySessaoAtiva("token-a")).thenReturn(Optional.of(usuario));
        when(passwordService.matches("SenhaAtual123!", "hash-atual")).thenReturn(true);
        when(passwordService.hash("NovaSenha123!")).thenReturn("hash-novo");

        authService.trocarSenha(null, "token-a", "SenhaAtual123!", "NovaSenha123!", "NovaSenha123!");

        verify(usuarioRepository).save(usuario);
        verify(usuarioSessionService).encerrarSessao("token-a");
    }
}

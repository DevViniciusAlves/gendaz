package com.minhaempresa.gendaz.admin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.minhaempresa.gendaz.admin.dto.AdminDtos.AdminLoginRequest;
import com.minhaempresa.gendaz.admin.repository.AdminImpersonationSessionRepository;
import com.minhaempresa.gendaz.chamado.repository.ChamadoRepository;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.assinatura.repository.AssinaturaRepository;
import com.minhaempresa.gendaz.auth.service.PasswordService;
import com.minhaempresa.gendaz.auth.service.UsuarioSessionService;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoPlanoRepository;
import com.minhaempresa.gendaz.pagamento.service.PagamentoService;
import com.minhaempresa.gendaz.plano.service.PlanoService;
import com.minhaempresa.gendaz.profissional.service.ProfissionalService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.SessaoExpiradaException;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminServiceTest {
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final EmpresaRepository empresaRepository = mock(EmpresaRepository.class);
    private final PagamentoPlanoRepository pagamentoPlanoRepository = mock(PagamentoPlanoRepository.class);
    private final AssinaturaRepository assinaturaRepository = mock(AssinaturaRepository.class);
    private final AdminImpersonationSessionRepository impersonationRepository = mock(AdminImpersonationSessionRepository.class);
    private final ChamadoRepository chamadoRepository = mock(ChamadoRepository.class);
    private final AssinaturaService assinaturaService = mock(AssinaturaService.class);
    private final PlanoService planoService = mock(PlanoService.class);
    private final AdminAuditService auditService = mock(AdminAuditService.class);
    private final PasswordService passwordService = new PasswordService();
    private final UsuarioSessionService usuarioSessionService = mock(UsuarioSessionService.class);
    private final PagamentoService pagamentoService = mock(PagamentoService.class);
    private final ProfissionalService profissionalService = mock(ProfissionalService.class);
    private final AdminService adminService = new AdminService(
            usuarioRepository,
            empresaRepository,
            pagamentoPlanoRepository,
            assinaturaRepository,
            impersonationRepository,
            chamadoRepository,
            assinaturaService,
            planoService,
            auditService,
            passwordService,
            usuarioSessionService,
            pagamentoService,
            profissionalService
    );

    @Test
    void deveBloquearUsuarioComumNoLoginAdmin() {
        UsuarioEntity usuario = UsuarioEntity.builder()
                .id(1L)
                .nome("Dono")
                .email("dono@Gendaz.com")
                .senha(passwordService.hash("SenhaForte123!"))
                .perfil(PerfilUsuario.DONO)
                .status(StatusUsuario.ATIVO)
                .build();
        when(usuarioRepository.findByEmail("dono@Gendaz.com")).thenReturn(Optional.of(usuario));

        assertThrows(BusinessException.class, () -> adminService.login(new AdminLoginRequest("dono@Gendaz.com", "SenhaForte123!"), "127.0.0.1", "test"));
        verify(auditService).registrar(eq("ADMIN_LOGIN_FAILED"), eq("SECURITY"), isNull(), isNull(), isNull(), anyString(), isNull(), eq("127.0.0.1"), eq("test"));
    }

    @Test
    void devePermitirSuperAdminNoLoginAdmin() {
        UsuarioEntity admin = UsuarioEntity.builder()
                .id(2L)
                .nome("Admin")
                .email("admin@Gendaz.com")
                .senha(passwordService.hash("SenhaForte123!"))
                .perfil(PerfilUsuario.SUPER_ADMIN)
                .status(StatusUsuario.ATIVO)
                .build();
        when(usuarioRepository.findAllByEmailIgnoreCase("admin@gendaz.com")).thenReturn(List.of(admin));
        when(usuarioSessionService.renovarSessao(admin)).thenReturn("token-admin");

        var response = adminService.login(new AdminLoginRequest("admin@Gendaz.com", "SenhaForte123!"), "127.0.0.1", "test");

        assertNotNull(response.token());
        assertEquals("admin@Gendaz.com", response.admin().email());
    }

    @Test
    void deveExigirTokenValidoParaAcessoAdmin() {
        assertThrows(SessaoExpiradaException.class, () -> adminService.exigirAdmin("token-invalido"));
    }
}


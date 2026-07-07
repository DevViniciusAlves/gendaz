package com.minhaempresa.agendapro.admin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.minhaempresa.agendapro.admin.dto.AdminDtos.AdminLoginRequest;
import com.minhaempresa.agendapro.admin.repository.AdminImpersonationSessionRepository;
import com.minhaempresa.agendapro.chamado.repository.ChamadoRepository;
import com.minhaempresa.agendapro.assinatura.service.AssinaturaService;
import com.minhaempresa.agendapro.auth.service.PasswordService;
import com.minhaempresa.agendapro.auth.service.UsuarioSessionService;
import com.minhaempresa.agendapro.empresa.repository.EmpresaRepository;
import com.minhaempresa.agendapro.pagamento.repository.PagamentoPlanoRepository;
import com.minhaempresa.agendapro.pagamento.service.PagamentoService;
import com.minhaempresa.agendapro.profissional.service.ProfissionalService;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.usuario.entity.UsuarioEntity;
import com.minhaempresa.agendapro.usuario.enums.PerfilUsuario;
import com.minhaempresa.agendapro.usuario.enums.StatusUsuario;
import com.minhaempresa.agendapro.usuario.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminServiceTest {
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final EmpresaRepository empresaRepository = mock(EmpresaRepository.class);
    private final PagamentoPlanoRepository pagamentoPlanoRepository = mock(PagamentoPlanoRepository.class);
    private final AdminImpersonationSessionRepository impersonationRepository = mock(AdminImpersonationSessionRepository.class);
    private final ChamadoRepository chamadoRepository = mock(ChamadoRepository.class);
    private final AssinaturaService assinaturaService = mock(AssinaturaService.class);
    private final AdminAuditService auditService = mock(AdminAuditService.class);
    private final PasswordService passwordService = new PasswordService();
    private final PagamentoService pagamentoService = mock(PagamentoService.class);
    private final UsuarioSessionService usuarioSessionService = mock(UsuarioSessionService.class);
    private final ProfissionalService profissionalService = mock(ProfissionalService.class);
    private final AdminService adminService = new AdminService(
            usuarioRepository,
            empresaRepository,
            pagamentoPlanoRepository,
            impersonationRepository,
            chamadoRepository,
            assinaturaService,
            auditService,
            passwordService,
            pagamentoService,
            usuarioSessionService,
            profissionalService
    );

    @Test
    void deveBloquearUsuarioComumNoLoginAdmin() {
        UsuarioEntity usuario = UsuarioEntity.builder()
                .id(1L)
                .nome("Dono")
                .email("dono@agendapro.com")
                .senha(passwordService.hash("SenhaForte123!"))
                .perfil(PerfilUsuario.DONO)
                .status(StatusUsuario.ATIVO)
                .build();
        when(usuarioRepository.findByEmail("dono@agendapro.com")).thenReturn(Optional.of(usuario));

        assertThrows(BusinessException.class, () -> adminService.login(new AdminLoginRequest("dono@agendapro.com", "SenhaForte123!"), "127.0.0.1", "test"));
        verify(auditService).registrar(eq("ADMIN_LOGIN_FAILED"), eq("SECURITY"), isNull(), isNull(), isNull(), anyString(), isNull(), eq("127.0.0.1"), eq("test"));
    }

    @Test
    void devePermitirSuperAdminNoLoginAdmin() {
        UsuarioEntity admin = UsuarioEntity.builder()
                .id(2L)
                .nome("Admin")
                .email("admin@agendapro.com")
                .senha(passwordService.hash("SenhaForte123!"))
                .perfil(PerfilUsuario.SUPER_ADMIN)
                .status(StatusUsuario.ATIVO)
                .build();
        when(usuarioRepository.findByEmail("admin@agendapro.com")).thenReturn(Optional.of(admin));

        var response = adminService.login(new AdminLoginRequest("admin@agendapro.com", "SenhaForte123!"), "127.0.0.1", "test");

        assertNotNull(response.token());
        assertEquals("admin@agendapro.com", response.admin().email());
    }

    @Test
    void deveExigirTokenValidoParaAcessoAdmin() {
        assertThrows(BusinessException.class, () -> adminService.exigirAdmin("token-invalido"));
    }
}

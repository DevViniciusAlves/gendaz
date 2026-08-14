package com.minhaempresa.gendaz.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.admin.entity.AdminImpersonationSessionEntity;
import com.minhaempresa.gendaz.admin.service.AdminImpersonationService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GendazSessionAuthenticationFilterTest {
    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AdminImpersonationService adminImpersonationService;

    @AfterEach
    void limparContextos() {
        SecurityContextHolder.clearContext();
        CompanyContext.clear();
    }

    @Test
    void impersonacaoValidaCarregaUsuarioComEmpresaEInjetaContextos() throws Exception {
        GendazSessionAuthenticationFilter filter = filtro();
        AdminImpersonationSessionEntity sessao = sessao(10L, 20L, 30L);
        UsuarioEntity usuario = usuario(20L, 30L, StatusUsuario.ATIVO, StatusEmpresa.ATIVA);
        when(adminImpersonationService.validar("token-impersonacao")).thenReturn(Optional.of(sessao));
        when(usuarioRepository.findByIdComEmpresa(20L)).thenReturn(Optional.of(usuario));
        AtomicBoolean passouNoController = new AtomicBoolean(false);
        MockHttpServletRequest request = getComCookie("Gendaz_impersonation_session", "token-impersonacao");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> {
            passouNoController.set(true);
            assertEquals(30L, CompanyContext.getCompanyId());
            assertEquals(20L, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
            assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN_IMPERSONATION".equals(a.getAuthority())));
            assertEquals(true, req.getAttribute("adminImpersonation"));
            assertEquals(10L, req.getAttribute("impersonationAdminId"));
            assertEquals(1L, req.getAttribute("impersonationSessionId"));
            assertEquals(20L, req.getAttribute("impersonationUsuarioId"));
            assertEquals(30L, req.getAttribute("impersonationEmpresaId"));
        });

        assertTrue(passouNoController.get());
        assertEquals(200, response.getStatus());
        verify(usuarioRepository).findByIdComEmpresa(20L);
        verify(usuarioRepository, never()).findById(20L);
    }

    @Test
    void impersonacaoComUsuarioInativoRetorna403() throws Exception {
        GendazSessionAuthenticationFilter filter = filtro();
        when(adminImpersonationService.validar("token-impersonacao")).thenReturn(Optional.of(sessao(10L, 20L, 30L)));
        when(usuarioRepository.findByIdComEmpresa(20L)).thenReturn(Optional.of(usuario(20L, 30L, StatusUsuario.INATIVO, StatusEmpresa.ATIVA)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(getComCookie("Gendaz_impersonation_session", "token-impersonacao"), response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void impersonacaoComEmpresaInativaRetorna403() throws Exception {
        GendazSessionAuthenticationFilter filter = filtro();
        when(adminImpersonationService.validar("token-impersonacao")).thenReturn(Optional.of(sessao(10L, 20L, 30L)));
        when(usuarioRepository.findByIdComEmpresa(20L)).thenReturn(Optional.of(usuario(20L, 30L, StatusUsuario.ATIVO, StatusEmpresa.INATIVA)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(getComCookie("Gendaz_impersonation_session", "token-impersonacao"), response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void impersonacaoComEmpresaDiferenteDaSessaoRetorna403() throws Exception {
        GendazSessionAuthenticationFilter filter = filtro();
        when(adminImpersonationService.validar("token-impersonacao")).thenReturn(Optional.of(sessao(10L, 20L, 30L)));
        when(usuarioRepository.findByIdComEmpresa(20L)).thenReturn(Optional.of(usuario(20L, 40L, StatusUsuario.ATIVO, StatusEmpresa.ATIVA)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(getComCookie("Gendaz_impersonation_session", "token-impersonacao"), response, new MockFilterChain());

        assertEquals(403, response.getStatus());
        assertTrue(response.getErrorMessage().contains("Sessao de impersonacao invalida"));
    }

    @Test
    void tokenDeImpersonacaoInvalidoSemSessaoNormalRetorna401() throws Exception {
        GendazSessionAuthenticationFilter filter = filtro();
        when(adminImpersonationService.validar("token-invalido")).thenReturn(Optional.empty());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(getComCookie("Gendaz_impersonation_session", "token-invalido"), response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void loginNormalContinuaUsandoFindBySessaoAtiva() throws Exception {
        GendazSessionAuthenticationFilter filter = filtro();
        when(usuarioRepository.findBySessaoAtiva("sessao-normal")).thenReturn(Optional.of(usuario(20L, 30L, StatusUsuario.ATIVO, StatusEmpresa.ATIVA)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(getComCookie("Gendaz_session", "sessao-normal"), response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        verify(usuarioRepository).findBySessaoAtiva("sessao-normal");
        verify(usuarioRepository, never()).findByIdComEmpresa(org.mockito.ArgumentMatchers.anyLong());
        verify(adminImpersonationService, never()).validar(anyString());
    }

    private GendazSessionAuthenticationFilter filtro() {
        GendazSessionAuthenticationFilter filter = new GendazSessionAuthenticationFilter(usuarioRepository, adminImpersonationService);
        ReflectionTestUtils.setField(filter, "frontendUrl", "https://gendaz.site");
        return filter;
    }

    private MockHttpServletRequest getComCookie(String nome, String valor) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard");
        request.setCookies(new Cookie(nome, valor));
        return request;
    }

    private AdminImpersonationSessionEntity sessao(Long adminId, Long usuarioId, Long empresaId) {
        return AdminImpersonationSessionEntity.builder()
                .id(1L)
                .adminUsuarioId(adminId)
                .usuarioImpersonadoId(usuarioId)
                .empresaId(empresaId)
                .status(AdminImpersonationService.STATUS_ATIVA)
                .sessionTokenHash("hash")
                .criadoEm(LocalDateTime.now())
                .expiraEm(LocalDateTime.now().plusMinutes(30))
                .build();
    }

    private UsuarioEntity usuario(Long usuarioId, Long empresaId, StatusUsuario statusUsuario, StatusEmpresa statusEmpresa) {
        return UsuarioEntity.builder()
                .id(usuarioId)
                .nome("Usuario")
                .email("usuario@gendaz.test")
                .senha("hash")
                .perfil(PerfilUsuario.DONO)
                .status(statusUsuario)
                .empresa(EmpresaEntity.builder().id(empresaId).status(statusEmpresa).build())
                .aceitouTermos(true)
                .build();
    }
}

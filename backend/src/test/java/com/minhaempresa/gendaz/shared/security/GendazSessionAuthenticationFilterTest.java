package com.minhaempresa.gendaz.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

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
        verifyNoInteractions(adminImpersonationService);
    }

    @Test
    void semCookieImpersonationNemSessaoNormalRetorna401() throws Exception {
        GendazSessionAuthenticationFilter filter = filtro();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard");
        request.addHeader("Origin", "http://localhost:5173");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        verifyNoInteractions(adminImpersonationService);
    }

    @Test
    void endpointsPublicosDeConviteNaoExigemSessao() throws Exception {
        GendazSessionAuthenticationFilter filter = filtro();

        for (String[] rota : new String[][] {
                {"GET", "/api/usuarios/convites/publico"},
                {"POST", "/api/usuarios/convites/aceitar"},
                {"POST", "/api/usuarios/convites/recusar"}
        }) {
            MockHttpServletRequest request = new MockHttpServletRequest(rota[0], rota[1]);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertEquals(200, response.getStatus());
            assertTrue(chain.getRequest() != null);
        }

        verifyNoInteractions(usuarioRepository, adminImpersonationService);
    }

    @Test
    void empresaEncerradaPermiteSomenteReativarConta() throws Exception {
        GendazSessionAuthenticationFilter filter = filtro();
        when(usuarioRepository.findBySessaoAtiva("sessao-encerrada")).thenReturn(Optional.of(usuario(20L, 30L, StatusUsuario.ATIVO, StatusEmpresa.ENCERRADA)));

        MockHttpServletResponse painel = new MockHttpServletResponse();
        MockHttpServletRequest reqPainel = requestComCookie("GET", "/api/dashboard", "Gendaz_session", "sessao-encerrada");
        filter.doFilterInternal(reqPainel, painel, new MockFilterChain());
        assertEquals(403, painel.getStatus());

        MockHttpServletResponse exportar = new MockHttpServletResponse();
        MockHttpServletRequest reqExportar = requestComCookie("GET", "/api/lgpd/exportar", "Gendaz_session", "sessao-encerrada");
        filter.doFilterInternal(reqExportar, exportar, new MockFilterChain());
        assertEquals(403, exportar.getStatus());

        MockHttpServletResponse financeiro = new MockHttpServletResponse();
        MockHttpServletRequest reqFinanceiro = requestComCookie("GET", "/api/pagamentos/planos/empresa/30/atual", "Gendaz_session", "sessao-encerrada");
        filter.doFilterInternal(reqFinanceiro, financeiro, new MockFilterChain());
        assertEquals(403, financeiro.getStatus());

        MockHttpServletResponse reativar = new MockHttpServletResponse();
        MockHttpServletRequest reqReativar = requestComCookie("POST", "/api/lgpd/reativar-conta", "Gendaz_session", "sessao-encerrada");
        filter.doFilterInternal(reqReativar, reativar, new MockFilterChain());
        assertEquals(200, reativar.getStatus());
    }

    @Test
    void empresaBloqueadaBloqueiaInclusiveReativarConta() throws Exception {
        GendazSessionAuthenticationFilter filter = filtro();
        when(usuarioRepository.findBySessaoAtiva("sessao-bloqueada")).thenReturn(Optional.of(usuario(20L, 30L, StatusUsuario.ATIVO, StatusEmpresa.BLOQUEADA)));

        MockHttpServletResponse painel = new MockHttpServletResponse();
        MockHttpServletRequest reqPainel = requestComCookie("GET", "/api/dashboard", "Gendaz_session", "sessao-bloqueada");
        filter.doFilterInternal(reqPainel, painel, new MockFilterChain());
        assertEquals(403, painel.getStatus());

        MockHttpServletResponse reativar = new MockHttpServletResponse();
        MockHttpServletRequest reqReativar = requestComCookie("POST", "/api/lgpd/reativar-conta", "Gendaz_session", "sessao-bloqueada");
        filter.doFilterInternal(reqReativar, reativar, new MockFilterChain());
        assertEquals(403, reativar.getStatus());
    }

    @Test
    void sessaoNormalComImpersonationValidaDaPrioridadeAImpersonation() throws Exception {
        GendazSessionAuthenticationFilter filter = filtro();
        AdminImpersonationSessionEntity sessao = sessao(10L, 20L, 30L);
        UsuarioEntity usuarioImpersonado = usuario(20L, 30L, StatusUsuario.ATIVO, StatusEmpresa.ATIVA);
        when(adminImpersonationService.validar("token-impersonacao")).thenReturn(Optional.of(sessao));
        when(usuarioRepository.findByIdComEmpresa(20L)).thenReturn(Optional.of(usuarioImpersonado));

        AtomicBoolean passouNoController = new AtomicBoolean(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard");
        request.addHeader("Origin", "http://localhost:5173");
        request.setCookies(
                new Cookie("Gendaz_impersonation_session", "token-impersonacao"),
                new Cookie("Gendaz_session", "sessao-admin-normal")
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> {
            passouNoController.set(true);
            assertEquals(30L, CompanyContext.getCompanyId());
            assertEquals(20L, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
            assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN_IMPERSONATION".equals(a.getAuthority())));
            assertEquals(true, req.getAttribute("adminImpersonation"));
        });

        assertTrue(passouNoController.get());
        assertEquals(200, response.getStatus());
        verify(usuarioRepository).findByIdComEmpresa(20L);
        verify(usuarioRepository, never()).findBySessaoAtiva(anyString());
    }

    @Test
    void impersonationExpiradaNaoConcedeRoleNemEmpresa() throws Exception {
        GendazSessionAuthenticationFilter filter = filtro();
        when(adminImpersonationService.validar("token-expirado")).thenReturn(Optional.empty());
        AtomicBoolean passouNoController = new AtomicBoolean(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard");
        request.addHeader("Origin", "http://localhost:5173");
        request.setCookies(new Cookie("Gendaz_impersonation_session", "token-expirado"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> passouNoController.set(true));

        // Sem sessao normal, cookie invalido nao pode conceder acesso
        assertEquals(401, response.getStatus());
        assertEquals(false, passouNoController.get());
        verify(usuarioRepository, never()).findByIdComEmpresa(anyLong());
    }

    @Test
    void impersonationInvalidaComSessaoNormalVoltaAoFluxoNormalSemRoleAdmin() throws Exception {
        GendazSessionAuthenticationFilter filter = filtro();
        when(adminImpersonationService.validar("token-invalido")).thenReturn(Optional.empty());
        when(usuarioRepository.findBySessaoAtiva("sessao-normal")).thenReturn(Optional.of(usuario(20L, 30L, StatusUsuario.ATIVO, StatusEmpresa.ATIVA)));

        AtomicBoolean passouNoController = new AtomicBoolean(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard");
        request.addHeader("Origin", "http://localhost:5173");
        request.setCookies(
                new Cookie("Gendaz_impersonation_session", "token-invalido"),
                new Cookie("Gendaz_session", "sessao-normal")
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> {
            passouNoController.set(true);
            assertNull(req.getAttribute("adminImpersonation"));
            assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                    .noneMatch(a -> "ROLE_ADMIN_IMPERSONATION".equals(a.getAuthority())));
        });

        assertTrue(passouNoController.get());
        assertEquals(200, response.getStatus());
        verify(usuarioRepository).findBySessaoAtiva("sessao-normal");
    }

    @Test
    void CompanyContextLimpoAposRequestNormal() throws Exception {
        GendazSessionAuthenticationFilter filter = filtro();
        when(usuarioRepository.findBySessaoAtiva("sessao-normal")).thenReturn(Optional.of(usuario(20L, 30L, StatusUsuario.ATIVO, StatusEmpresa.ATIVA)));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(getComCookie("Gendaz_session", "sessao-normal"), response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        assertEquals(null, CompanyContext.getCompanyId());
        verifyNoInteractions(adminImpersonationService);
    }

    @Test
    void CompanyContextLimpoAposRequestImpersonation() throws Exception {
        GendazSessionAuthenticationFilter filter = filtro();
        when(adminImpersonationService.validar("token-impersonacao")).thenReturn(Optional.of(sessao(10L, 20L, 30L)));
        when(usuarioRepository.findByIdComEmpresa(20L)).thenReturn(Optional.of(usuario(20L, 30L, StatusUsuario.ATIVO, StatusEmpresa.ATIVA)));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(getComCookie("Gendaz_impersonation_session", "token-impersonacao"), response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        assertEquals(null, CompanyContext.getCompanyId());
    }

    @Test
    void encerrarImpersonationRestauraFluxoNormal() throws Exception {
        GendazSessionAuthenticationFilter filter = filtro();
        // Apos sair da impersonation o cookie nao esta mais presente: so Gendaz_session
        when(usuarioRepository.findBySessaoAtiva("sessao-admin")).thenReturn(Optional.of(usuario(40L, 50L, StatusUsuario.ATIVO, StatusEmpresa.ATIVA)));

        AtomicBoolean passouNoController = new AtomicBoolean(false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(getComCookie("Gendaz_session", "sessao-admin"), response, (req, res) -> {
            passouNoController.set(true);
            assertNull(req.getAttribute("adminImpersonation"));
            assertEquals(50L, CompanyContext.getCompanyId());
        });

        assertTrue(passouNoController.get());
        assertEquals(200, response.getStatus());
        verify(usuarioRepository).findBySessaoAtiva("sessao-admin");
        verify(usuarioRepository, never()).findByIdComEmpresa(anyLong());
        verifyNoInteractions(adminImpersonationService);
    }

    private GendazSessionAuthenticationFilter filtro() {
        GendazSessionAuthenticationFilter filter = new GendazSessionAuthenticationFilter(usuarioRepository, adminImpersonationService);
        ReflectionTestUtils.setField(filter, "frontendUrl", "https://gendaz.site");
        return filter;
    }

    private MockHttpServletRequest getComCookie(String nome, String valor) {
        return requestComCookie("GET", "/api/dashboard", nome, valor);
    }

    private MockHttpServletRequest requestComCookie(String method, String uri, String nome, String valor) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("Origin", "http://localhost:5173");
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

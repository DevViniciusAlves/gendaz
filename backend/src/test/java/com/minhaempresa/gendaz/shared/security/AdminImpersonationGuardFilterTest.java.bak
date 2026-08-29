package com.minhaempresa.gendaz.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class AdminImpersonationGuardFilterTest {
    @Mock
    private SecurityMonitoringService securityMonitoringService;

    @Test
    void bloqueiaAcaoSensivelDuranteImpersonacao() throws Exception {
        AdminImpersonationGuardFilter filter = new AdminImpersonationGuardFilter(securityMonitoringService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/pagamentos/teste");
        request.setAttribute("adminImpersonation", true);
        request.setAttribute("impersonationAdminId", 10L);
        request.setAttribute("impersonationUsuarioId", 20L);
        request.setAttribute("impersonationEmpresaId", 30L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("Acao nao permitida durante impersonacao administrativa"));
        verify(securityMonitoringService).registrarEvento(
                eq("ADMIN_IMPERSONATION_BLOCKED_ACTION"),
                eq("SECURITY"),
                eq(request),
                eq("10"),
                eq("usuarioId=20; empresaId=30; metodo=POST")
        );
    }

    @Test
    void liberaGetDuranteImpersonacao() throws Exception {
        AdminImpersonationGuardFilter filter = new AdminImpersonationGuardFilter(securityMonitoringService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/pagamentos/planos/empresa/atual");
        request.setAttribute("adminImpersonation", true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        verify(securityMonitoringService, never()).registrarEvento(any(), any(), any(), any(), any());
    }
}

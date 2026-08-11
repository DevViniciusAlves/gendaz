package com.minhaempresa.agendapro.shared.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.CsrfToken;

class CsrfCookieFilterTest {
    @Test
    void deveLerTokenCsrfDisponivelNaRequisicao() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        CsrfToken token = mock(CsrfToken.class);

        org.mockito.Mockito.when(request.getAttribute(CsrfToken.class.getName())).thenReturn(token);

        new CsrfCookieFilter().doFilter(request, response, chain);

        verify(token).getToken();
        verify(chain).doFilter(request, response);
    }
}

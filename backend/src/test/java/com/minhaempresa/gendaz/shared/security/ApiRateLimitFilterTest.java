package com.minhaempresa.gendaz.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhaempresa.gendaz.auth.config.MeuGendazSecurityProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiRateLimitFilterTest {
    private ApiRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        MeuGendazSecurityProperties properties = new MeuGendazSecurityProperties();
        properties.getRateLimit().setLocalWindowSeconds(60);
        filter = new ApiRateLimitFilter(properties, new ClientIpResolver(""));
    }

    @Test
    void loginBloqueiaSextaRequisicaoDoMesmoIp() throws Exception {
        assertLimite("/api/auth/login", 5);
    }

    @Test
    void criarContaBloqueiaQuartaRequisicaoDoMesmoIp() throws Exception {
        assertLimite("/api/auth/criar-conta", 3);
    }

    @Test
    void webhookStripeBloqueiaVigesimaPrimeiraRequisicaoDoMesmoIp() throws Exception {
        assertLimite("/api/pagamentos/webhook/stripe", 20);
    }

    @Test
    void namespacePublicoDeWebhooksTambemEstaProtegido() throws Exception {
        assertLimite("/api/webhooks/provedor", 20);
    }

    @Test
    void contadoresSaoIsoladosPorIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(executar("/api/auth/login", "198.51.100.10").getStatus()).isEqualTo(200);
        }
        assertThat(executar("/api/auth/login", "198.51.100.11").getStatus()).isEqualTo(200);
    }

    private void assertLimite(String path, int permitidas) throws Exception {
        for (int i = 0; i < permitidas; i++) {
            assertThat(executar(path, "198.51.100.20").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse bloqueada = executar(path, "198.51.100.20");
        assertThat(bloqueada.getStatus()).isEqualTo(429);
        assertThat(bloqueada.getHeader("Retry-After")).isNotBlank();
    }

    private MockHttpServletResponse executar(String path, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(200);
        filter.doFilter(request, response, chain);
        return response;
    }
}

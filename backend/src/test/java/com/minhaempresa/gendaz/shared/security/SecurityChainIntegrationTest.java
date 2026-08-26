package com.minhaempresa.gendaz.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityChainIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void endpointProtegidoSemSessaoDeveRetornar401() throws Exception {
        mockMvc.perform(get("/api/dashboard/resumo"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void preflightCorsPermiteCriacaoDeContaComHeadersDeIdempotencia() throws Exception {
        mockMvc.perform(options("/api/auth/criar-conta")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type, X-Idempotency-Key, X-Request-Id"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("X-Idempotency-Key")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("X-Request-Id")));
    }

    @Test
    void consultaPublicaDeConviteChegaAoControllerSemSessao() throws Exception {
        mockMvc.perform(get("/api/usuarios/convites/publico").param("token", "inexistente"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aceiteERecusaDeConviteNaoSaoBloqueadosPorSessaoOuCsrf() throws Exception {
        mockMvc.perform(post("/api/usuarios/convites/aceitar")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/usuarios/convites/recusar")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}

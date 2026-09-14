package com.minhaempresa.gendaz.whatsapp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A API publica do WhatsApp exige sessao autenticada como qualquer outro
 * recurso /api: sem autenticacao, a cadeia de seguranca bloqueia antes do
 * controller (o tenant nunca chega a ser resolvido).
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class WhatsAppIntegracaoSegurancaTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void resumoSemAutenticacaoBloqueado() throws Exception {
        mockMvc.perform(get("/api/whatsapp/resumo"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conectarSemAutenticacaoBloqueado() throws Exception {
        mockMvc.perform(post("/api/whatsapp/conectar"))
                .andExpect(status().isForbidden());
    }

    @Test
    void qrSemAutenticacaoBloqueado() throws Exception {
        mockMvc.perform(get("/api/whatsapp/qr"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void desconectarSemAutenticacaoBloqueado() throws Exception {
        mockMvc.perform(post("/api/whatsapp/desconectar"))
                .andExpect(status().isForbidden());
    }

    @Test
    void configuracaoSemAutenticacaoBloqueado() throws Exception {
        mockMvc.perform(patch("/api/whatsapp/configuracao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lembretesAtivos\":true}"))
                .andExpect(status().isForbidden());
    }
}

package com.minhaempresa.agendapro.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.agendapro.auth.dto.AuthDtos.MeuGendazAuthResponse;
import com.minhaempresa.agendapro.auth.dto.AuthDtos.MeuGendazCodigoResponse;
import com.minhaempresa.agendapro.auth.dto.AuthDtos.MeuGendazSolicitarCodigoRequest;
import com.minhaempresa.agendapro.auth.service.MeuGendazAuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MeuGendazAuthControllerTest {

    @Mock
    private MeuGendazAuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new MeuGendazAuthController(authService)).build();
    }

    @Test
    void deveSolicitarCodigoSemExporToken() throws Exception {
        when(authService.solicitarCodigo(anyString(), anyString(), anyString()))
                .thenReturn(new MeuGendazCodigoResponse("Enviamos um codigo para o seu e-mail.", "cliente@teste.com", false));

        mockMvc.perform(post("/api/meu-gendaz/auth/solicitar-codigo")
                        .contentType("application/json")
                        .content("""
                                {"slug":"gendaz-pro","email":"cliente@teste.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cliente@teste.com"))
                .andExpect(jsonPath("$.mensagem").value("Enviamos um codigo para o seu e-mail."));
    }

    @Test
    void deveValidarCodigoEGravarCookieDaSessao() throws Exception {
        when(authService.validarCodigo(anyString(), anyString(), anyString()))
                .thenReturn(new MeuGendazAuthResponse("Login realizado com sucesso.", "cliente@teste.com", "sessao-meu-gendaz", "ACTIVE"));

        mockMvc.perform(post("/api/meu-gendaz/auth/validar-codigo")
                        .contentType("application/json")
                        .content("""
                                {"slug":"gendaz-pro","email":"cliente@teste.com","codigo":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionToken").value(""))
                .andExpect(cookie().exists("meu_gendaz_session_gendaz-pro"));
    }

    @Test
    void deveRenovarSessaoUsandoCookieDaEmpresa() throws Exception {
        when(authService.refreshSessao(anyString(), anyString()))
                .thenReturn(new MeuGendazAuthResponse("Sessao renovada com sucesso.", "cliente@teste.com", "sessao-renovada", "ACTIVE"));

        mockMvc.perform(post("/api/meu-gendaz/auth/refresh")
                        .header("X-Meu-Gendaz-Slug", "gendaz-pro")
                        .cookie(new Cookie("meu_gendaz_session_gendaz-pro", "sessao-antiga")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionToken").value(""))
                .andExpect(cookie().exists("meu_gendaz_session_gendaz-pro"));
    }
}

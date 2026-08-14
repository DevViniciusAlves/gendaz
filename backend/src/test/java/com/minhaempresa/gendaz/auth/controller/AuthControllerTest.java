package com.minhaempresa.gendaz.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhaempresa.gendaz.auth.dto.AuthDtos.LoginResponse;
import com.minhaempresa.gendaz.auth.dto.AuthDtos.RefreshResponse;
import com.minhaempresa.gendaz.auth.service.AuthService;
import com.minhaempresa.gendaz.shared.CookieService;
import com.minhaempresa.gendaz.usuario.dto.UsuarioDtos.UsuarioResponse;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {
    @Mock AuthService authService;
    private CookieService cookieService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        cookieService = new CookieService("test");
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, cookieService)).build();
    }

    @Test
    void deveRealizarLoginComCredenciais() throws Exception {
        LocalDateTime agora = LocalDateTime.now();
        UsuarioResponse usuario = new UsuarioResponse(1L, "Usuario Teste", "teste@Gendaz.com", PerfilUsuario.DONO, StatusUsuario.ATIVO, 1L, "Empresa", true, null, null, null, null, null, agora, agora);
        when(authService.login(any())).thenReturn(new LoginResponse("ok", usuario, null, null, "ACTIVE", "sessao-teste"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new java.util.HashMap<String, String>() {{
                            put("email", "teste@Gendaz.com");
                            put("senha", "Senha123!");
                        }})))
                .andExpect(cookie().exists("Gendaz_session"))
                .andExpect(status().isOk());
    }

    @Test
    void deveRenovarSessaoComCookie() throws Exception {
        LocalDateTime agora = LocalDateTime.now();
        UsuarioResponse usuario = new UsuarioResponse(1L, "Usuario Teste", "teste@Gendaz.com", PerfilUsuario.DONO, StatusUsuario.ATIVO, 1L, "Empresa", true, null, null, null, null, null, agora, agora);
        when(authService.refresh(any())).thenReturn(new RefreshResponse("ok", usuario, null, null, "ACTIVE", "sessao-renovada"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("Gendaz_session", "sessao-antiga")))
                .andExpect(cookie().exists("Gendaz_session"))
                .andExpect(status().isOk());
    }

    @Test
    void logoutSemCookieComXUsuarioIdNaoEncerraSessao() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("X-Usuario-Id", "2"))
                .andExpect(cookie().maxAge("Gendaz_session", 0))
                .andExpect(cookie().maxAge("agendapro_session", 0))
                .andExpect(status().isNoContent());

        verify(authService).logout(null);
        verify(authService, never()).logout(anyString());
    }

    @Test
    void logoutComCookieUsaSomenteTokenDaSessaoMesmoComXUsuarioIdDivergente() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("Gendaz_session", "token-atacante"))
                        .header("X-Usuario-Id", "2"))
                .andExpect(cookie().maxAge("Gendaz_session", 0))
                .andExpect(cookie().maxAge("agendapro_session", 0))
                .andExpect(status().isNoContent());

        verify(authService).logout("token-atacante");
    }
}

package com.minhaempresa.gendaz.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

class AuthControllerTest {
    @Mock AuthService authService;
    private CookieService cookieService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        cookieService = new CookieService("test");
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, cookieService))
                .setValidator(validatorPermissivo())
                .build();
    }

    private Validator validatorPermissivo() {
        return new Validator() {
            @Override
            public boolean supports(Class<?> clazz) {
                return true;
            }

            @Override
            public void validate(Object target, Errors errors) {
            }
        };
    }

    private Map<String, Object> corpoCriarConta() {
        return new HashMap<>(Map.of(
                "nomeEmpresa", "Clinica Beta",
                "nomeProprietario", "Ana Maria",
                "email", "ana@gendaz.com.br",
                "telefone", "+5511999999999",
                "senha", "Senha123!",
                "confirmarSenha", "Senha123!",
                "plano", "basico",
                "aceiteTermos", true
        ));
    }

    @Test
    void criarContaRepassaHeadersDeIdempotenciaParaOServico() throws Exception {
        when(authService.criarConta(any(), eq("chave-a"), eq("req-1")))
                .thenReturn(new LoginResponse("Conta criada com sucesso.", usuarioTeste(), null, null, "ACTIVE", "sessão-nova", null));

        mockMvc.perform(post("/api/auth/criar-conta")
                        .contentType("application/json")
                        .header("X-Idempotency-Key", "chave-a")
                        .header("X-Request-Id", "req-1")
                        .content(objectMapper.writeValueAsString(corpoCriarConta())))
                .andExpect(cookie().exists("Gendaz_session"))
                .andExpect(status().isOk());

        verify(authService).criarConta(any(), eq("chave-a"), eq("req-1"));
    }

    @Test
    void criarContaSemHeadersChamaServicoComNull() throws Exception {
        when(authService.criarConta(any(), eq(null), eq(null)))
                .thenReturn(new LoginResponse("Conta criada com sucesso.", usuarioTeste(), null, null, "ACTIVE", "sessão-nova", null));

        mockMvc.perform(post("/api/auth/criar-conta")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(corpoCriarConta())))
                .andExpect(status().isOk());

        verify(authService).criarConta(any(), eq(null), eq(null));
    }

    private UsuarioResponse usuarioTeste() {
        LocalDateTime agora = LocalDateTime.now();
        return new UsuarioResponse(1L, "Ana Maria", "ana@gendaz.com.br", PerfilUsuario.DONO, StatusUsuario.ATIVO,
                1L, "Clinica Beta", true, true, agora, "2026-06-22", agora, "2026-06-22", agora, agora);
    }

    @Test
    void deveRealizarLoginComCredenciais() throws Exception {
        LocalDateTime agora = LocalDateTime.now();
        UsuarioResponse usuario = new UsuarioResponse(1L, "Usuario Teste", "teste@Gendaz.com", PerfilUsuario.DONO, StatusUsuario.ATIVO, 1L, "Empresa", true, null, null, null, null, null, agora, agora);
        when(authService.login(any())).thenReturn(new LoginResponse("ok", usuario, null, null, "ACTIVE", "sessão-teste"));

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
        when(authService.refresh(any())).thenReturn(new RefreshResponse("ok", usuario, null, null, "ACTIVE", "sessão-renovada"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("Gendaz_session", "sessão-antiga")))
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

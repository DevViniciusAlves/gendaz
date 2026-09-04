package com.minhaempresa.gendaz.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhaempresa.gendaz.admin.dto.AdminDtos.AdminDashboardResponse;
import com.minhaempresa.gendaz.admin.dto.AdminDtos.AdminLoginResponse;
import com.minhaempresa.gendaz.admin.dto.AdminDtos.AdminUsuarioResponse;
import com.minhaempresa.gendaz.admin.dto.AdminDtos.PlanoDistribuicaoResponse;
import com.minhaempresa.gendaz.admin.dto.AdminDtos.ReceitaDiaResponse;
import com.minhaempresa.gendaz.admin.service.AdminService;
import com.minhaempresa.gendaz.shared.CookieService;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminControllerTest {
    @Mock AdminService adminService;
    private CookieService cookieService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        cookieService = new CookieService("test");
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminController(adminService, cookieService)).build();
    }

    @Test
    void deveRealizarLoginAdminComCookie() throws Exception {
        when(adminService.login(any(), any(), any()))
                .thenReturn(new AdminLoginResponse("token-teste", new AdminUsuarioResponse(1L, "Admin", "admin@Gendaz.com", "SUPER_ADMIN")));

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new HashMap<String, String>() {{
                            put("email", "admin@Gendaz.com");
                            put("senha", "Senha123!");
                        }})))
                .andExpect(cookie().exists("agendeasy_admin_session"))
                .andExpect(status().isOk());
    }

    @Test
    void deveBuscarDashboardRepassandoMesInformado() throws Exception {
        when(adminService.dashboard(any(), any()))
                .thenReturn(new AdminDashboardResponse(
                        3L, 1L, 2L,
                        new BigDecimal("150.00"),
                        new BigDecimal("150.00"),
                        1L, 1L,
                        List.of(new ReceitaDiaResponse("2026-09-01", "01/09", new BigDecimal("150.00"))),
                        List.of(new PlanoDistribuicaoResponse("BASICO", 2L))
                ));

        mockMvc.perform(get("/api/admin/dashboard").param("mes", "2026-09").header("X-Admin-Token", "token-teste"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contasAtivas").value(3))
                .andExpect(jsonPath("$.receitaDia[0].data").value("2026-09-01"));

        verify(adminService).dashboard("token-teste", "2026-09");
    }
}

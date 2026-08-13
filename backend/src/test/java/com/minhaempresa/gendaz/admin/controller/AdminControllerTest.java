package com.minhaempresa.gendaz.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhaempresa.gendaz.admin.dto.AdminDtos.AdminLoginResponse;
import com.minhaempresa.gendaz.admin.dto.AdminDtos.AdminUsuarioResponse;
import com.minhaempresa.gendaz.admin.service.AdminService;
import com.minhaempresa.gendaz.shared.CookieService;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminControllerTest {
    @Mock AdminService adminService;
    @Mock CookieService cookieService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
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
}

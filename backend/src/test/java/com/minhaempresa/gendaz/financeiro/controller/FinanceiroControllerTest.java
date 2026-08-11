package com.minhaempresa.gendaz.financeiro.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.gendaz.financeiro.dto.FinanceiroDtos.ResumoFinanceiroResponse;
import com.minhaempresa.gendaz.financeiro.service.FinanceiroService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FinanceiroControllerTest {
    @Mock
    private FinanceiroService financeiroService;
    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new FinanceiroController(financeiroService)).build();
    }

    @Test
    void deveAceitarResumoSemEmpresaIdQuandoContextoDeterminarEmpresa() throws Exception {
        when(financeiroService.resumo(isNull(), anyInt(), anyInt()))
                .thenReturn(new ResumoFinanceiroResponse(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        0L,
                        List.of(),
                        List.of(),
                        List.of()
                ));

        mockMvc.perform(get("/api/financeiro/resumo")
                        .param("mes", "8")
                        .param("ano", "2026"))
                .andExpect(status().isOk());

        verify(financeiroService).resumo(isNull(), anyInt(), anyInt());
    }
}


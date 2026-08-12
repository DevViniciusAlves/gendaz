package com.minhaempresa.gendaz.pagamento.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.gendaz.auth.service.AuthService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.pagamento.service.PagamentoBulkService;
import com.minhaempresa.gendaz.pagamento.service.PagamentoService;
import com.minhaempresa.gendaz.pagamento.service.StripeWebhookService;
import com.minhaempresa.gendaz.shared.GlobalExceptionHandler;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PagamentoControllerTest {
    @Mock
    private PagamentoService pagamentoService;
    @Mock
    private PagamentoBulkService pagamentoBulkService;
    @Mock
    private AuthService authService;
    @Mock
    private StripeWebhookService stripeWebhookService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new PagamentoController(pagamentoService, pagamentoBulkService, stripeWebhookService, authService)
        ).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void deveBloquearEmpresaDiferenteDaSessao() throws Exception {
        UsuarioEntity usuario = new UsuarioEntity();
        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(10L);
        usuario.setEmpresa(empresa);
        when(authService.buscarUsuarioAutenticado(isNull(), any())).thenReturn(usuario);

        mockMvc.perform(get("/api/pagamentos/empresa/99"))
                .andExpect(status().isBadRequest());

        verify(pagamentoService, never()).listarPorEmpresa(anyLong());
    }
}

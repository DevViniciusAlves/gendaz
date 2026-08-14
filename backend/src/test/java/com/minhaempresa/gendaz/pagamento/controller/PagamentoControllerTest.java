package com.minhaempresa.gendaz.pagamento.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.gendaz.assinatura.dto.AssinaturaDtos.AssinaturaResponse;
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import com.minhaempresa.gendaz.pagamento.service.PagamentoBulkService;
import com.minhaempresa.gendaz.pagamento.service.PagamentoService;
import com.minhaempresa.gendaz.pagamento.service.StripeWebhookService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.GlobalExceptionHandler;
import com.minhaempresa.gendaz.shared.security.UsuarioAutenticadoProvider;
import java.time.LocalDate;
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
    private StripeWebhookService stripeWebhookService;
    @Mock
    private UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new PagamentoController(pagamentoService, pagamentoBulkService, stripeWebhookService, usuarioAutenticadoProvider)
        ).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void deveBloquearEmpresaDiferenteDaSessao() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException("Empresa da sessao nao corresponde ao recurso solicitado."))
                .when(usuarioAutenticadoProvider).exigirEmpresa(99L);

        mockMvc.perform(get("/api/pagamentos/empresa/99"))
                .andExpect(status().isBadRequest());

        verify(pagamentoService, never()).listarPorEmpresa(anyLong());
    }

    @Test
    void planoAtualUsaProviderSemReautenticarPorCookie() throws Exception {
        AssinaturaResponse response = new AssinaturaResponse(1L, 10L, "Empresa", 2L, "PRO", StatusAssinatura.ATIVA, LocalDate.now(), LocalDate.now().plusMonths(1), null, null, 30);
        when(pagamentoService.consultarPlanoAtual(10L)).thenReturn(response);

        mockMvc.perform(get("/api/pagamentos/planos/empresa/10/atual"))
                .andExpect(status().isOk());

        verify(usuarioAutenticadoProvider).exigirEmpresa(10L);
        verify(pagamentoService).consultarPlanoAtual(10L);
    }

    @Test
    void contagemPendentesUsaProviderSemReautenticarPorCookie() throws Exception {
        when(pagamentoService.contarPendentes(10L)).thenReturn(3L);

        mockMvc.perform(get("/api/pagamentos/pendentes/contagem").param("empresaId", "10"))
                .andExpect(status().isOk());

        verify(usuarioAutenticadoProvider).exigirEmpresa(10L);
        verify(pagamentoService).contarPendentes(10L);
    }
}

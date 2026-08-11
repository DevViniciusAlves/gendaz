package com.minhaempresa.gendaz.financeiro.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.financeiro.dto.FinanceiroDtos.ResumoFinanceiroResponse;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class FinanceiroServiceTest {
    @Mock
    private PagamentoRepository pagamentoRepository;
    @Mock
    private AgendamentoRepository agendamentoRepository;
    private FinanceiroService service;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new FinanceiroService(pagamentoRepository, agendamentoRepository);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    void deveUsarEmpresaDaSessaoQuandoParametroNaoForEnviado() {
        CompanyContext.setCompanyId(9L);
        when(pagamentoRepository.findByEmpresaIdAndDataPagamentoBetween(eq(9L), any(), any())).thenReturn(List.of());
        when(pagamentoRepository.findByEmpresaIdAndStatus(eq(9L), eq(StatusPagamento.PENDENTE))).thenReturn(List.of());
        when(agendamentoRepository.findByEmpresaId(eq(9L))).thenReturn(List.of());

        assertDoesNotThrow(() -> {
            ResumoFinanceiroResponse resposta = service.resumo(null, 8, 2026);
            org.junit.jupiter.api.Assertions.assertNotNull(resposta);
        });

        verify(pagamentoRepository).findByEmpresaIdAndDataPagamentoBetween(eq(9L), any(), any());
        verify(pagamentoRepository).findByEmpresaIdAndStatus(eq(9L), eq(StatusPagamento.PENDENTE));
        verify(agendamentoRepository).findByEmpresaId(eq(9L));
    }

    @Test
    void deveRejeitarEmpresaDivergenteDaSessao() {
        CompanyContext.setCompanyId(9L);

        assertThrows(BusinessException.class, () -> service.resumo(1L, 8, 2026));
    }
}


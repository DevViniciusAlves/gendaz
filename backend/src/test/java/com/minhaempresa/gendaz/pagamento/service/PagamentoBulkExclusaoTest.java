package com.minhaempresa.gendaz.pagamento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.AcaoEmMassaPagamentoRequest;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Parte 6 — bulk EXCLUIR de pagamento delega a regra central
 * (PagamentoService.excluirPagamento): PAGO vira falha orientando ao estorno
 * explicito; PENDENTE vira CANCELADO logico sem tocar no Caixa. O bulk nao
 * implementa regra financeira.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PagamentoBulkExclusaoTest {
    @Mock PagamentoService pagamentoService;
    PagamentoBulkService bulk;

    @BeforeEach
    void setup() {
        bulk = new PagamentoBulkService(pagamentoService);
        CompanyContext.setCompanyId(1L);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    void excluirPagamentoPagoViraFalhaViaRegraCentral() {
        doThrow(new BusinessException("Pagamento confirmado nao pode ser excluido."))
                .when(pagamentoService).excluirPagamento(eq(1L));

        var response = bulk.executar(new AcaoEmMassaPagamentoRequest(List.of(1L), "EXCLUIR", 1L, null, null));

        assertEquals(0, response.totalProcessado());
        assertEquals(1, response.falhas().size());
    }

    @Test
    void excluirPagamentoPendenteDelegaExclusaoLogica() {
        doNothing().when(pagamentoService).excluirPagamento(eq(2L));

        var response = bulk.executar(new AcaoEmMassaPagamentoRequest(List.of(2L), "EXCLUIR", 1L, null, null));

        assertEquals(1, response.totalProcessado());
        assertEquals(0, response.falhas().size());
        verify(pagamentoService).excluirPagamento(eq(2L));
    }

    @Test
    void excluirEmMassaContinuaAposFalhaDeItem() {
        doThrow(new BusinessException("Pagamento confirmado nao pode ser excluido."))
                .when(pagamentoService).excluirPagamento(eq(1L));
        doNothing().when(pagamentoService).excluirPagamento(eq(2L));

        var response = bulk.executar(new AcaoEmMassaPagamentoRequest(List.of(1L, 2L), "EXCLUIR", 1L, null, null));

        assertEquals(1, response.totalProcessado());
        assertEquals(1, response.falhas().size());
    }
}

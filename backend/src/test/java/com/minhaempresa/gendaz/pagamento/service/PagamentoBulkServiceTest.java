package com.minhaempresa.gendaz.pagamento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.AcaoEmMassaPagamentoRequest;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PagamentoBulkServiceTest {
    @Mock PagamentoService pagamentoService;
    PagamentoBulkService bulk;

    @BeforeEach
    void setup() {
        bulk = new PagamentoBulkService(pagamentoService);
        CompanyContext.setCompanyId(1L);
    }

    @AfterEach
    void limparContexto() {
        CompanyContext.clear();
    }

    @Test
    void deveDelegarCadaItemAoServiceSemTocarEmRepository() {
        var resultado = bulk.executar(new AcaoEmMassaPagamentoRequest(
                List.of(100L), "MARCAR_COMO_PAGO", 1L, MetodoPagamento.PIX, null));

        assertEquals(1, resultado.totalProcessado());
        assertEquals(0, resultado.falhas().size());
        verify(pagamentoService, times(1)).marcarPago(eq(100L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deveFalharAntesDeConsultarSemCompanyContext() {
        CompanyContext.clear();
        assertThrows(BusinessException.class, () -> bulk.executar(
                new AcaoEmMassaPagamentoRequest(
                        List.of(100L), "MARCAR_COMO_PAGO", 1L, MetodoPagamento.PIX, null)));

        verify(pagamentoService, never()).marcarPago(anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deveTransformarBusinessExceptionEmFalhaDoItemEContinuar() {
        org.mockito.Mockito.doThrow(new BusinessException("Pagamento nao encontrado."))
                .when(pagamentoService).excluirPagamento(eq(100L));

        var resultado = bulk.executar(new AcaoEmMassaPagamentoRequest(
                List.of(100L, 101L), "EXCLUIR", 1L, null, null));

        assertEquals(1, resultado.totalProcessado());
        assertEquals(1, resultado.falhas().size());
        verify(pagamentoService).excluirPagamento(eq(101L));
    }
}

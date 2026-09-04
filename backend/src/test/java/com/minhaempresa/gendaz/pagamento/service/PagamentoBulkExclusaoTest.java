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
    @Mock com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository pagamentoRepository;
    PagamentoBulkService bulk;

    @BeforeEach
    void setup() {
        bulk = new PagamentoBulkService(pagamentoService, pagamentoRepository);
        CompanyContext.setCompanyId(1L);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private void stubLote(Long id, com.minhaempresa.gendaz.pagamento.enums.StatusPagamento status) {
        var pagamento = com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity.builder()
                .id(id).valor(new java.math.BigDecimal("100.00")).status(status).build();
        org.mockito.Mockito.when(pagamentoRepository.findByIdAndEmpresaIdForUpdate(eq(id), eq(1L)))
                .thenReturn(java.util.Optional.of(pagamento));
    }

    @Test
    void excluirPagamentoPagoViraFalhaViaRegraCentral() {
        stubLote(1L, com.minhaempresa.gendaz.pagamento.enums.StatusPagamento.PENDENTE);
        doThrow(new BusinessException("Pagamento confirmado nao pode ser excluido."))
                .when(pagamentoService).aplicarExclusao(org.mockito.ArgumentMatchers.any());

        var response = bulk.executar(new AcaoEmMassaPagamentoRequest(List.of(1L), "EXCLUIR", 1L, null, null));

        assertEquals(0, response.totalProcessado());
        assertEquals(1, response.falhas().size());
    }

    @Test
    void excluirPagamentoPendenteDelegaExclusaoLogica() {
        stubLote(2L, com.minhaempresa.gendaz.pagamento.enums.StatusPagamento.PENDENTE);
        doNothing().when(pagamentoService).aplicarExclusao(org.mockito.ArgumentMatchers.any());

        var response = bulk.executar(new AcaoEmMassaPagamentoRequest(List.of(2L), "EXCLUIR", 1L, null, null));

        assertEquals(1, response.totalProcessado());
        assertEquals(0, response.falhas().size());
        verify(pagamentoService).aplicarExclusao(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void excluirEmMassaContinuaAposFalhaDeItem() {
        stubLote(1L, com.minhaempresa.gendaz.pagamento.enums.StatusPagamento.PENDENTE);
        stubLote(2L, com.minhaempresa.gendaz.pagamento.enums.StatusPagamento.PENDENTE);
        doThrow(new BusinessException("Pagamento confirmado nao pode ser excluido."))
                .when(pagamentoService).aplicarExclusao(org.mockito.ArgumentMatchers.argThat(p -> p != null && p.getId() != null && p.getId().equals(1L)));
        doNothing().when(pagamentoService).aplicarExclusao(org.mockito.ArgumentMatchers.argThat(p -> p != null && p.getId() != null && p.getId().equals(2L)));

        var response = bulk.executar(new AcaoEmMassaPagamentoRequest(List.of(1L, 2L), "EXCLUIR", 1L, null, null));

        assertEquals(1, response.totalProcessado());
        assertEquals(1, response.falhas().size());
    }

    @Test
    void excluirPagamentoCanceladoEhIdempotente() {
        stubLote(3L, com.minhaempresa.gendaz.pagamento.enums.StatusPagamento.CANCELADO);
        doNothing().when(pagamentoService).aplicarExclusao(org.mockito.ArgumentMatchers.any());

        var response = bulk.executar(new AcaoEmMassaPagamentoRequest(List.of(3L), "EXCLUIR", 1L, null, null));

        assertEquals(1, response.totalProcessado());
        assertEquals(0, response.falhas().size());
    }
}

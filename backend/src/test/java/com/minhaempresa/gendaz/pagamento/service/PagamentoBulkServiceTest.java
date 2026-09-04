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
    @Mock com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository pagamentoRepository;
    PagamentoBulkService bulk;

    @BeforeEach
    void setup() {
        bulk = new PagamentoBulkService(pagamentoService, pagamentoRepository);
        CompanyContext.setCompanyId(1L);
    }

    @AfterEach
    void limparContexto() {
        CompanyContext.clear();
    }

    private com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity pagamentoComStatus(Long id, com.minhaempresa.gendaz.pagamento.enums.StatusPagamento status) {
        return com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity.builder()
                .id(id)
                .valor(new java.math.BigDecimal("100.00"))
                .status(status)
                .build();
    }

    private void stubLote(java.util.Map<Long, com.minhaempresa.gendaz.pagamento.enums.StatusPagamento> statusPorId) {
        statusPorId.forEach((id, status) -> org.mockito.Mockito
                .when(pagamentoRepository.findByIdAndEmpresaIdForUpdate(eq(id), eq(1L)))
                .thenReturn(java.util.Optional.of(pagamentoComStatus(id, status))));
    }

    @Test
    void deveDelegarCadaItemAoNucleoCentral() {
        stubLote(java.util.Map.of(100L, com.minhaempresa.gendaz.pagamento.enums.StatusPagamento.PENDENTE));
        var resultado = bulk.executar(new AcaoEmMassaPagamentoRequest(
                List.of(100L), "MARCAR_COMO_PAGO", 1L, MetodoPagamento.PIX, null));

        assertEquals(1, resultado.totalProcessado());
        assertEquals(0, resultado.falhas().size());
        verify(pagamentoService, times(1)).aplicarMarcarPago(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void idsNulosDevemFalharSemNpe() {
        assertThrows(BusinessException.class, () -> bulk.executar(new AcaoEmMassaPagamentoRequest(
                java.util.Arrays.asList(100L, null), "MARCAR_COMO_PAGO", 1L, MetodoPagamento.PIX, null)));

        verify(pagamentoService, never()).aplicarMarcarPago(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deveFalharAntesDeConsultarSemCompanyContext() {
        CompanyContext.clear();
        assertThrows(BusinessException.class, () -> bulk.executar(
                new AcaoEmMassaPagamentoRequest(
                        List.of(100L), "MARCAR_COMO_PAGO", 1L, MetodoPagamento.PIX, null)));

        verify(pagamentoService, never()).aplicarMarcarPago(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deveTransformarBusinessExceptionEmFalhaDoItemEContinuar() {
        stubLote(java.util.Map.of(
                100L, com.minhaempresa.gendaz.pagamento.enums.StatusPagamento.PENDENTE,
                101L, com.minhaempresa.gendaz.pagamento.enums.StatusPagamento.PENDENTE));
        org.mockito.Mockito.doThrow(new BusinessException("Pagamento confirmado nao pode ser excluido."))
                .when(pagamentoService).aplicarExclusao(org.mockito.ArgumentMatchers.argThat(p -> p != null && p.getId() != null && p.getId().equals(100L)));

        var resultado = bulk.executar(new AcaoEmMassaPagamentoRequest(
                List.of(100L, 101L), "EXCLUIR", 1L, null, null));

        assertEquals(1, resultado.totalProcessado());
        assertEquals(1, resultado.falhas().size());
        verify(pagamentoService).aplicarExclusao(org.mockito.ArgumentMatchers.argThat(p -> p != null && p.getId() != null && p.getId().equals(101L)));
    }

    @Test
    void deveFalharQuandoPagamentoCanceladoEmMassa() {
        stubLote(java.util.Map.of(
                100L, com.minhaempresa.gendaz.pagamento.enums.StatusPagamento.PENDENTE,
                101L, com.minhaempresa.gendaz.pagamento.enums.StatusPagamento.CANCELADO));

        // Pre-validacao atomica com lock: [PENDENTE, CANCELADO] + MARCAR_COMO_PAGO => ERRO, zero alteracoes.
        assertThrows(BusinessException.class, () -> bulk.executar(new AcaoEmMassaPagamentoRequest(
                List.of(100L, 101L), "MARCAR_COMO_PAGO", 1L, MetodoPagamento.PIX, null)));

        verify(pagamentoService, never()).aplicarMarcarPago(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(pagamentoService, never()).aplicarAtualizarStatus(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void bulkPendenteCanceladoMarcarComoPagoFalhaSemAtualizacaoParcial() {
        stubLote(java.util.Map.of(
                100L, com.minhaempresa.gendaz.pagamento.enums.StatusPagamento.PENDENTE,
                101L, com.minhaempresa.gendaz.pagamento.enums.StatusPagamento.CANCELADO));

        assertThrows(BusinessException.class, () -> bulk.executar(new AcaoEmMassaPagamentoRequest(
                List.of(100L, 101L), "MARCAR_COMO_PAGO", 1L, MetodoPagamento.PIX, null)));

        // Prova ausencia de atualizacao parcial: PENDENTE continua PENDENTE.
        verify(pagamentoService, never()).aplicarMarcarPago(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void bulkPendenteCanceladoMarcarComoPendenteFalhaSemAlterarNenhum() {
        stubLote(java.util.Map.of(
                100L, com.minhaempresa.gendaz.pagamento.enums.StatusPagamento.PENDENTE,
                101L, com.minhaempresa.gendaz.pagamento.enums.StatusPagamento.CANCELADO));

        assertThrows(BusinessException.class, () -> bulk.executar(new AcaoEmMassaPagamentoRequest(
                List.of(100L, 101L), "MARCAR_COMO_PENDENTE", 1L, null, null)));

        verify(pagamentoService, never()).aplicarAtualizarStatus(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(pagamentoService, never()).aplicarMarcarPago(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void bulkRaceCanceladoDuranteLoteAbortaTudo() {
        stubLote(java.util.Map.of(
                100L, com.minhaempresa.gendaz.pagamento.enums.StatusPagamento.PENDENTE,
                101L, com.minhaempresa.gendaz.pagamento.enums.StatusPagamento.PENDENTE));
        // Simula a janela residual: item virou CANCELADO entre a pre-validacao e o processamento.
        org.mockito.Mockito.doThrow(new BusinessException("Pagamento cancelado não pode ser alterado."))
                .when(pagamentoService).aplicarMarcarPago(
                        org.mockito.ArgumentMatchers.argThat(p -> p != null && p.getId() != null && p.getId().equals(101L)),
                        org.mockito.ArgumentMatchers.any());

        assertThrows(BusinessException.class, () -> bulk.executar(new AcaoEmMassaPagamentoRequest(
                List.of(100L, 101L), "MARCAR_COMO_PAGO", 1L, MetodoPagamento.PIX, null)));
    }
}

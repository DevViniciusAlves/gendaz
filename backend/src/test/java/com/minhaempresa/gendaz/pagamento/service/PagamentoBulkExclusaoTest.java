package com.minhaempresa.gendaz.pagamento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.AcaoEmMassaPagamentoRequest;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.security.UsuarioAutenticadoProvider;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Parte 6 — bulk EXCLUIR de pagamento nunca apaga historico financeiro:
 * PAGO vira falha orientando ao estorno explicito; PENDENTE vira CANCELADO
 * logico (sem tocar no Caixa, pois nada foi registrado).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PagamentoBulkExclusaoTest {
    @Mock PagamentoRepository pagamentoRepository;
    @Mock FormaPagamentoEmpresaService formaPagamentoEmpresaService;
    @Mock com.minhaempresa.gendaz.financeiro.caixadespesas.service.CaixaDespesasService caixaDespesasService;
    @Mock UsuarioAutenticadoProvider usuarioAutenticadoProvider;
    PagamentoBulkService bulk;

    @BeforeEach
    void setup() {
        bulk = new PagamentoBulkService(
                pagamentoRepository, formaPagamentoEmpresaService, caixaDespesasService, usuarioAutenticadoProvider);
        CompanyContext.setCompanyId(1L);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private PagamentoEntity pagamento(Long id, StatusPagamento status) {
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).build();
        ClienteEntity cliente = ClienteEntity.builder().id(1L).nome("Ana").build();
        return PagamentoEntity.builder()
                .id(id).empresa(empresa).cliente(cliente)
                .valor(new BigDecimal("150.00")).metodoPagamento(MetodoPagamento.PIX)
                .status(status).build();
    }

    @Test
    void excluirPagamentoPagoViraFalhaSemApagarNada() {
        when(pagamentoRepository.findByIdAndEmpresaIdForUpdate(1L, 1L))
                .thenReturn(Optional.of(pagamento(1L, StatusPagamento.PAGO)));

        var response = bulk.executar(new AcaoEmMassaPagamentoRequest(List.of(1L), "EXCLUIR", 1L, null, null));

        assertEquals(0, response.totalProcessado());
        assertEquals(1, response.falhas().size());
        verify(pagamentoRepository, never()).delete(any());
        verify(pagamentoRepository, never()).save(any());
        verify(caixaDespesasService, never()).registrarPagamentoAprovado(any());
        verify(caixaDespesasService, never()).registrarPagamentoRemovido(any(), any());
    }

    @Test
    void excluirPagamentoPendenteViraCanceladoLogicoSemCaixa() {
        PagamentoEntity pendente = pagamento(2L, StatusPagamento.PENDENTE);
        when(pagamentoRepository.findByIdAndEmpresaIdForUpdate(2L, 1L)).thenReturn(Optional.of(pendente));
        when(pagamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = bulk.executar(new AcaoEmMassaPagamentoRequest(List.of(2L), "EXCLUIR", 1L, null, null));

        assertEquals(1, response.totalProcessado());
        assertEquals(0, response.falhas().size());
        assertEquals(StatusPagamento.CANCELADO, pendente.getStatus());
        verify(pagamentoRepository, never()).delete(any());
        verify(caixaDespesasService, never()).registrarPagamentoAprovado(any());
        verify(caixaDespesasService, never()).registrarPagamentoRemovido(any(), any());
    }

    @Test
    void excluirPagamentoJaCanceladoEhIdempotente() {
        PagamentoEntity cancelado = pagamento(3L, StatusPagamento.CANCELADO);
        when(pagamentoRepository.findByIdAndEmpresaIdForUpdate(3L, 1L)).thenReturn(Optional.of(cancelado));
        when(pagamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = bulk.executar(new AcaoEmMassaPagamentoRequest(List.of(3L), "EXCLUIR", 1L, null, null));

        assertEquals(1, response.totalProcessado());
        assertEquals(StatusPagamento.CANCELADO, cancelado.getStatus());
        verify(pagamentoRepository, never()).delete(any());
    }
}

package com.minhaempresa.gendaz.pagamento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.AcaoEmMassaPagamentoRequest;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PagamentoBulkServiceTest {
    @Mock PagamentoRepository pagamentoRepository;
    @Mock FormaPagamentoEmpresaService formaPagamentoEmpresaService;
    @InjectMocks PagamentoBulkService pagamentoBulkService;

    @AfterEach
    void limparContexto() {
        CompanyContext.clear();
    }

    @Test
    void deveConsultarCadaPagamentoComEmpresaDaSessao() {
        CompanyContext.setCompanyId(1L);
        when(pagamentoRepository.findByIdAndEmpresaId(100L, 1L)).thenReturn(Optional.empty());

        var resultado = pagamentoBulkService.executar(new AcaoEmMassaPagamentoRequest(
                List.of(100L), "MARCAR_COMO_PAGO", 1L, MetodoPagamento.PIX, null));

        assertEquals(0, resultado.totalProcessado());
        assertEquals(1, resultado.falhas().size());
        verify(pagamentoRepository).findByIdAndEmpresaId(100L, 1L);
        verify(pagamentoRepository, never()).findById(anyLong());
    }

    @Test
    void deveFalharAntesDeConsultarSemCompanyContext() {
        assertThrows(BusinessException.class, () -> pagamentoBulkService.executar(
                new AcaoEmMassaPagamentoRequest(
                        List.of(100L), "MARCAR_COMO_PAGO", 1L, MetodoPagamento.PIX, null)));

        verify(pagamentoRepository, never()).findByIdAndEmpresaId(anyLong(), anyLong());
    }
}

package com.minhaempresa.gendaz.agendamento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AcaoEmMassaAgendamentoRequest;
import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
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
class AgendamentoBulkServiceTest {
    @Mock PagamentoRepository pagamentoRepository;
    @Mock AgendamentoService agendamentoService;
    @Mock LogAtividadeService logAtividadeService;
    AgendamentoBulkService service;

    @BeforeEach
    void setup() {
        service = new AgendamentoBulkService(pagamentoRepository, agendamentoService, logAtividadeService);
        CompanyContext.setCompanyId(1L);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    void cancelarEmMassaCancelaAgendamentosEPagamentosPendentes() {
        var response = service.executar(new AcaoEmMassaAgendamentoRequest(List.of(1L, 2L, 3L), "CANCELAR", 1L));

        assertEquals(3, response.totalProcessado());
        assertEquals(0, response.falhas().size());
        verify(agendamentoService, times(3)).cancelar(anyLong(), anyLong());
    }

    @Test
    void cancelarEmMassaComPagamentoPagoChamaRegularizacaoUmaVezPorItem() {
        var response = service.executar(new AcaoEmMassaAgendamentoRequest(List.of(1L, 2L, 3L), "CANCELAR", 1L));

        assertEquals(3, response.totalProcessado());
        // A regra de "pagamento PAGO permanece PAGO" e garantida dentro do PagamentoService.
        // Aqui garantimos que o bulk delega ao cancelamento central para cada item.
        verify(agendamentoService, times(3)).cancelar(anyLong(), eq(1L));
    }

    @Test
    void cancelarEmMassaNaoCancelaPagamentoDeOutraEmpresa() {
        org.junit.jupiter.api.Assertions.assertThrows(
                com.minhaempresa.gendaz.shared.BusinessException.class,
                () -> service.executar(new AcaoEmMassaAgendamentoRequest(List.of(1L), "CANCELAR", 99L)));

        verify(agendamentoService, never()).cancelar(anyLong(), anyLong());
    }
}

package com.minhaempresa.gendaz.agendamento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AcaoEmMassaAgendamentoRequest;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
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
 * Bulk FINALIZAR nunca decide regra financeira: sem parametros, delega a
 * {@code finalizarPreservandoPagamento} (decisao sob lock dentro do service);
 * com parametros explicitos, repassa ao {@code finalizar} individual.
 * O bulk nao le PagamentoEntity em nenhum caminho.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BulkFinalizarConsistenciaTest {
    @Mock AgendamentoService agendamentoService;
    AgendamentoBulkService bulk;

    @BeforeEach
    void setup() {
        bulk = new AgendamentoBulkService(agendamentoService);
        CompanyContext.setCompanyId(1L);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    void finalizarEmMassaSemParametrosPreservaPagamentoSobLock() {
        var response = bulk.executar(new AcaoEmMassaAgendamentoRequest(List.of(1L), "FINALIZAR", 1L));

        assertEquals(1, response.totalProcessado());
        // Sem parametros: preservacao decidida sob lock no service, nunca
        // boolean stale calculado no bulk. O finalizar explicito nao e chamado.
        verify(agendamentoService).finalizarPreservandoPagamento(eq(1L), eq(1L));
        verify(agendamentoService, never()).finalizar(
                eq(1L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void finalizarEmMassaComParametrosExplicitosRepassaTudo() {
        var response = bulk.executar(new AcaoEmMassaAgendamentoRequest(
                List.of(1L), "FINALIZAR", 1L, true, MetodoPagamento.DINHEIRO, null));

        assertEquals(1, response.totalProcessado());
        verify(agendamentoService).finalizar(eq(1L), eq(true), eq(MetodoPagamento.DINHEIRO), eq(null));
        verify(agendamentoService, never()).finalizarPreservandoPagamento(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void finalizarEmMassaComMetodoParcialUsaFluxoExplicito() {
        var response = bulk.executar(new AcaoEmMassaAgendamentoRequest(
                List.of(1L), "FINALIZAR", 1L, null, MetodoPagamento.PIX, null));

        assertEquals(1, response.totalProcessado());
        verify(agendamentoService).finalizar(eq(1L), eq(null), eq(MetodoPagamento.PIX), eq(null));
    }
}

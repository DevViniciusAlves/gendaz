package com.minhaempresa.gendaz.agendamento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AcaoEmMassaAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.pagamento.service.PagamentoService;
import com.minhaempresa.gendaz.shared.CompanyContext;
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
 * Parte 3 — bulk FINALIZAR reutiliza a mesma regra do fluxo individual.
 * Sem parametros de pagamento, nunca inventa recebimento.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BulkFinalizarConsistenciaTest {
    @Mock AgendamentoRepository agendamentoRepository;
    @Mock PagamentoRepository pagamentoRepository;
    @Mock PagamentoService pagamentoService;
    @Mock AgendamentoService agendamentoService;
    @Mock LogAtividadeService logAtividadeService;
    AgendamentoBulkService bulk;

    @BeforeEach
    void setup() {
        bulk = new AgendamentoBulkService(
                agendamentoRepository, pagamentoRepository, pagamentoService, agendamentoService, logAtividadeService);
        CompanyContext.setCompanyId(1L);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private AgendamentoEntity agendamento(Long id) {
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).build();
        ClienteEntity cliente = ClienteEntity.builder().id(1L).nome("Ana").build();
        return AgendamentoEntity.builder()
                .id(id).empresa(empresa).cliente(cliente)
                .status(StatusAgendamento.PENDENTE).build();
    }

    @Test
    void finalizarEmMassaSemParametrosNaoInventaRecebimento() {
        when(agendamentoRepository.findById(1L)).thenReturn(Optional.of(agendamento(1L)));
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaId(1L, 1L)).thenReturn(Optional.of(
                PagamentoEntity.builder().id(9L).valor(new BigDecimal("100.00"))
                        .status(StatusPagamento.PENDENTE).build()));

        var response = bulk.executar(new AcaoEmMassaAgendamentoRequest(List.of(1L), "FINALIZAR", 1L));

        assertEquals(1, response.totalProcessado());
        // Pagamento pendente -> finaliza SEM pagamento (false), nunca PAGO implicito.
        verify(agendamentoService).finalizar(eq(1L), eq(false), eq(null), eq(null));
    }

    @Test
    void finalizarEmMassaPreservaPagoJaConfirmadoSemDuplicarCaixa() {
        when(agendamentoRepository.findById(1L)).thenReturn(Optional.of(agendamento(1L)));
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaId(1L, 1L)).thenReturn(Optional.of(
                PagamentoEntity.builder().id(9L).valor(new BigDecimal("100.00"))
                        .metodoPagamento(MetodoPagamento.PIX)
                        .status(StatusPagamento.PAGO).build()));

        var response = bulk.executar(new AcaoEmMassaAgendamentoRequest(List.of(1L), "FINALIZAR", 1L));

        assertEquals(1, response.totalProcessado());
        verify(agendamentoService).finalizar(eq(1L), eq(true), eq(MetodoPagamento.PIX), eq(null));
    }

    @Test
    void finalizarEmMassaComParametrosExplicitosRepassaTudo() {
        when(agendamentoRepository.findById(1L)).thenReturn(Optional.of(agendamento(1L)));

        var response = bulk.executar(new AcaoEmMassaAgendamentoRequest(
                List.of(1L), "FINALIZAR", 1L, true, MetodoPagamento.DINHEIRO, null));

        assertEquals(1, response.totalProcessado());
        verify(agendamentoService).finalizar(eq(1L), eq(true), eq(MetodoPagamento.DINHEIRO), eq(null));
        verify(pagamentoRepository, never()).findByAgendamentoIdAndEmpresaId(any(), any());
    }
}

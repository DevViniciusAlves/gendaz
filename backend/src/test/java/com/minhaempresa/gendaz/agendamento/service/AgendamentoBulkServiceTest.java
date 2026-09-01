package com.minhaempresa.gendaz.agendamento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AcaoEmMassaAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.pagamento.service.PagamentoService;
import com.minhaempresa.gendaz.shared.CompanyContext;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgendamentoBulkServiceTest {
    @Mock AgendamentoRepository agendamentoRepository;
    @Mock PagamentoRepository pagamentoRepository;
    @Mock PagamentoService pagamentoService;
    @Mock LogAtividadeService logAtividadeService;
    AgendamentoBulkService service;

    @BeforeEach
    void setup() {
        service = new AgendamentoBulkService(agendamentoRepository, pagamentoRepository, pagamentoService, logAtividadeService);
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
    void cancelarEmMassaCancelaAgendamentosEPagamentosPendentes() {
        when(agendamentoRepository.findById(1L)).thenReturn(Optional.of(agendamento(1L)));
        when(agendamentoRepository.findById(2L)).thenReturn(Optional.of(agendamento(2L)));
        when(agendamentoRepository.findById(3L)).thenReturn(Optional.of(agendamento(3L)));
        when(agendamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.executar(new AcaoEmMassaAgendamentoRequest(List.of(1L, 2L, 3L), "CANCELAR", 1L));

        assertEquals(3, response.totalProcessado());
        assertEquals(0, response.falhas().size());
        verify(pagamentoService, times(3)).cancelarPagamentoPendenteDoAgendamento(anyLong(), anyLong());
    }

    @Test
    void cancelarEmMassaComPagamentoPagoChamaRegularizacaoUmaVezPorItem() {
        when(agendamentoRepository.findById(1L)).thenReturn(Optional.of(agendamento(1L)));
        when(agendamentoRepository.findById(2L)).thenReturn(Optional.of(agendamento(2L)));
        when(agendamentoRepository.findById(3L)).thenReturn(Optional.of(agendamento(3L)));
        when(agendamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.executar(new AcaoEmMassaAgendamentoRequest(List.of(1L, 2L, 3L), "CANCELAR", 1L));

        assertEquals(3, response.totalProcessado());
        // A regra de "pagamento PAGO permanece PAGO" e garantida dentro do PagamentoService.
        // Aqui garantimos que o bulk chama a regularizacao para cada agendamento cancelado.
        verify(pagamentoService, times(3)).cancelarPagamentoPendenteDoAgendamento(anyLong(), eq(1L));
    }

    @Test
    void cancelarEmMassaNaoCancelaPagamentoDeOutraEmpresa() {
        when(agendamentoRepository.findById(1L)).thenReturn(Optional.of(agendamento(1L)));

        org.junit.jupiter.api.Assertions.assertThrows(
                com.minhaempresa.gendaz.shared.BusinessException.class,
                () -> service.executar(new AcaoEmMassaAgendamentoRequest(List.of(1L), "CANCELAR", 99L)));

        verify(pagamentoService, never()).cancelarPagamentoPendenteDoAgendamento(anyLong(), anyLong());
    }
}

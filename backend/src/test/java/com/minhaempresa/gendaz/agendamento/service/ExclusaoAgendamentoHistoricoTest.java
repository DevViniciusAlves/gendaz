package com.minhaempresa.gendaz.agendamento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AcaoEmMassaAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.service.ClienteService;
import com.minhaempresa.gendaz.email.ResendEmailService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.service.EmpresaService;
import com.minhaempresa.gendaz.financeiro.caixadespesas.service.CaixaDespesasService;
import com.minhaempresa.gendaz.horarioatendimento.service.HorarioAtendimentoService;
import com.minhaempresa.gendaz.meugendazpromocao.service.MeuGendazPromocaoService;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.pagamento.service.FormaPagamentoEmpresaService;
import com.minhaempresa.gendaz.pagamento.service.PagamentoService;
import com.minhaempresa.gendaz.profissional.service.ProfissionalService;
import com.minhaempresa.gendaz.servico.service.ServicoService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.SanitizacaoService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Partes 4 e 5 — exclusao de agendamento nunca destroi historico financeiro,
 * e o bulk EXCLUIR usa a mesma regra da exclusao individual.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExclusaoAgendamentoHistoricoTest {
    @Mock AgendamentoRepository agendamentoRepository;
    @Mock ClienteService clienteService;
    @Mock ServicoService servicoService;
    @Mock ProfissionalService profissionalService;
    @Mock EmpresaService empresaService;
    @Mock HorarioAtendimentoService horarioAtendimentoService;
    @Mock AgendaBlockedDayService agendaBlockedDayService;
    @Mock PagamentoRepository pagamentoRepository;
    @Mock PagamentoService pagamentoService;
    @Mock SanitizacaoService sanitizacaoService;
    @Mock ResendEmailService resendEmailService;
    @Mock MeuGendazPromocaoService meuGendazPromocaoService;
    @Mock FormaPagamentoEmpresaService formaPagamentoEmpresaService;
    @Mock LogAtividadeService logAtividadeService;
    @Mock CaixaDespesasService caixaDespesasService;
    @Mock TransactionTemplate transactionTemplate;
    @InjectMocks AgendamentoService agendamentoService;

    private EmpresaEntity empresa;
    private ClienteEntity cliente;

    @BeforeEach
    void setup() {
        CompanyContext.setCompanyId(1L);
        org.springframework.test.util.ReflectionTestUtils.setField(agendamentoService, "appTimezone", "America/Cuiaba");
        org.springframework.test.util.ReflectionTestUtils.setField(agendamentoService, "pagamentoService", pagamentoService);
        empresa = EmpresaEntity.builder().id(1L).timezone("America/Cuiaba").build();
        cliente = ClienteEntity.builder().id(1L).nome("Ana").empresa(empresa).build();
        when(agendamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private AgendamentoEntity agendamento(Long id, StatusAgendamento status) {
        return AgendamentoEntity.builder()
                .id(id).empresa(empresa).cliente(cliente)
                .status(status).build();
    }

    private PagamentoEntity pagamentoPago(AgendamentoEntity ag) {
        return PagamentoEntity.builder()
                .id(50L).agendamento(ag).cliente(cliente).empresa(empresa)
                .valor(new BigDecimal("200.00")).metodoPagamento(MetodoPagamento.PIX)
                .status(StatusPagamento.PAGO).build();
    }

    @Test
    void excluirAgendamentoPagoCancelaSemApagarHistorico() {
        AgendamentoEntity ag = agendamento(10L, StatusAgendamento.PENDENTE);
        when(agendamentoRepository.findById(10L)).thenReturn(Optional.of(ag));
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaId(10L, 1L))
                .thenReturn(Optional.of(pagamentoPago(ag)));

        agendamentoService.excluir(10L, 1L);

        assertEquals(StatusAgendamento.CANCELADO, ag.getStatus());
        verify(agendamentoRepository, never()).delete(any());
        verify(pagamentoRepository, never()).deleteByAgendamentoIdAndEmpresaId(anyLong(), anyLong());
        verify(pagamentoService).cancelarPagamentoPendenteDoAgendamento(10L, 1L);
    }

    @Test
    void excluirAgendamentoFinalizadoBloqueadoMantemHistorico() {
        AgendamentoEntity ag = agendamento(11L, StatusAgendamento.FINALIZADO);
        when(agendamentoRepository.findById(11L)).thenReturn(Optional.of(ag));
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaId(11L, 1L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> agendamentoService.excluir(11L, 1L));

        assertEquals(StatusAgendamento.FINALIZADO, ag.getStatus());
        verify(agendamentoRepository, never()).delete(any());
        verify(agendamentoRepository, never()).save(any());
    }

    @Test
    void excluirAgendamentoNovoSemPagamentoRemoveFisicamente() {
        AgendamentoEntity ag = agendamento(12L, StatusAgendamento.PENDENTE);
        when(agendamentoRepository.findById(12L)).thenReturn(Optional.of(ag));
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaId(12L, 1L)).thenReturn(Optional.empty());

        agendamentoService.excluir(12L, 1L);

        verify(agendamentoRepository).delete(ag);
        verify(pagamentoRepository, never()).deleteByAgendamentoIdAndEmpresaId(anyLong(), anyLong());
    }

    @Test
    void excluirConfirmadoComPagamentoGeraCanceladoLogico() {
        AgendamentoEntity ag = agendamento(13L, StatusAgendamento.CONFIRMADO);
        when(agendamentoRepository.findById(13L)).thenReturn(Optional.of(ag));
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaId(13L, 1L))
                .thenReturn(Optional.of(pagamentoPago(ag)));

        agendamentoService.excluir(13L, 1L);

        assertEquals(StatusAgendamento.CANCELADO, ag.getStatus());
        verify(agendamentoRepository, never()).delete(any());
        verify(pagamentoRepository, never()).deleteByAgendamentoIdAndEmpresaId(anyLong(), anyLong());
    }

    @Test
    void excluirCanceladoEIdempotenteESeguro() {
        AgendamentoEntity comPagamento = agendamento(14L, StatusAgendamento.CANCELADO);
        when(agendamentoRepository.findById(14L)).thenReturn(Optional.of(comPagamento));
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaId(14L, 1L))
                .thenReturn(Optional.of(pagamentoPago(comPagamento)));

        agendamentoService.excluir(14L, 1L);

        assertEquals(StatusAgendamento.CANCELADO, comPagamento.getStatus());
        verify(agendamentoRepository, never()).delete(any());
        verify(pagamentoRepository, never()).deleteByAgendamentoIdAndEmpresaId(anyLong(), anyLong());
    }

    @Test
    void excluirEmAndamentoOuPausadoBloqueadoSemTocarEmNada() {
        for (StatusAgendamento bloqueado : List.of(StatusAgendamento.EM_ATENDIMENTO, StatusAgendamento.PAUSADO)) {
            AgendamentoEntity ag = agendamento(15L, bloqueado);
            PagamentoEntity pago = pagamentoPago(ag);
            when(agendamentoRepository.findById(15L)).thenReturn(Optional.of(ag));
            when(pagamentoRepository.findByAgendamentoIdAndEmpresaId(15L, 1L))
                    .thenReturn(Optional.of(pago));

            assertThrows(BusinessException.class, () -> agendamentoService.excluir(15L, 1L),
                    "excluir de " + bloqueado);

            assertEquals(bloqueado, ag.getStatus());
            assertEquals(StatusPagamento.PAGO, pago.getStatus());
        }
        verify(agendamentoRepository, never()).delete(any());
        verify(agendamentoRepository, never()).save(any());
        verify(pagamentoRepository, never()).deleteByAgendamentoIdAndEmpresaId(anyLong(), anyLong());
        verifyNoInteractions(pagamentoService);
        verifyNoInteractions(caixaDespesasService);
    }

    @Test
    void excluirFinalizadoComPagamentoBloqueadoSemTocarEmNada() {
        AgendamentoEntity ag = agendamento(16L, StatusAgendamento.FINALIZADO);
        PagamentoEntity pago = pagamentoPago(ag);
        when(agendamentoRepository.findById(16L)).thenReturn(Optional.of(ag));
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaId(16L, 1L))
                .thenReturn(Optional.of(pago));

        assertThrows(BusinessException.class, () -> agendamentoService.excluir(16L, 1L));

        assertEquals(StatusAgendamento.FINALIZADO, ag.getStatus());
        assertEquals(StatusPagamento.PAGO, pago.getStatus());
        verify(agendamentoRepository, never()).delete(any());
        verify(agendamentoRepository, never()).save(any());
        verifyNoInteractions(pagamentoService);
        verifyNoInteractions(caixaDespesasService);
    }

    @Test
    void excluirCrossTenantBloqueadoAntesDeQualquerAlteracao() {
        AgendamentoEntity ag = agendamento(17L, StatusAgendamento.PENDENTE);
        when(agendamentoRepository.findById(17L)).thenReturn(Optional.of(ag));

        assertThrows(BusinessException.class, () -> agendamentoService.excluir(17L, 99L));

        assertEquals(StatusAgendamento.PENDENTE, ag.getStatus());
        verify(agendamentoRepository, never()).delete(any());
        verify(agendamentoRepository, never()).save(any());
        verifyNoInteractions(pagamentoRepository);
    }

    @Test
    void bulkExcluirBloqueiaEmAndamentoPausadoEFinalizado() {
        AgendamentoBulkService bulk = new AgendamentoBulkService(
                agendamentoRepository, pagamentoRepository, agendamentoService, logAtividadeService);
        AgendamentoEntity emAt = agendamento(21L, StatusAgendamento.EM_ATENDIMENTO);
        AgendamentoEntity pausado = agendamento(22L, StatusAgendamento.PAUSADO);
        AgendamentoEntity finalizado = agendamento(23L, StatusAgendamento.FINALIZADO);
        when(agendamentoRepository.findById(21L)).thenReturn(Optional.of(emAt));
        when(agendamentoRepository.findById(22L)).thenReturn(Optional.of(pausado));
        when(agendamentoRepository.findById(23L)).thenReturn(Optional.of(finalizado));

        var response = bulk.executar(new AcaoEmMassaAgendamentoRequest(List.of(21L, 22L, 23L), "EXCLUIR", 1L));

        assertEquals(0, response.totalProcessado());
        assertEquals(3, response.falhas().size());
        assertEquals(StatusAgendamento.EM_ATENDIMENTO, emAt.getStatus());
        assertEquals(StatusAgendamento.PAUSADO, pausado.getStatus());
        assertEquals(StatusAgendamento.FINALIZADO, finalizado.getStatus());
        verify(agendamentoRepository, never()).delete(any());
        verify(pagamentoRepository, never()).deleteByAgendamentoIdAndEmpresaId(anyLong(), anyLong());
    }

    @Test
    void bulkExcluirDelegaParaExclusaoIndividual() {
        AgendamentoBulkService bulk = new AgendamentoBulkService(
                agendamentoRepository, pagamentoRepository, agendamentoService, logAtividadeService);
        AgendamentoEntity ag = agendamento(20L, StatusAgendamento.PENDENTE);
        when(agendamentoRepository.findById(20L)).thenReturn(Optional.of(ag));
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaId(20L, 1L)).thenReturn(Optional.empty());

        var response = bulk.executar(new AcaoEmMassaAgendamentoRequest(List.of(20L), "EXCLUIR", 1L));

        assertEquals(1, response.totalProcessado());
        assertEquals(0, response.falhas().size());
        verify(agendamentoRepository).delete(any());
        verify(pagamentoRepository, never()).deleteByAgendamentoIdAndEmpresaId(anyLong(), anyLong());
    }
}

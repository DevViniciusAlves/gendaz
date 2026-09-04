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
        com.minhaempresa.gendaz.servico.entity.ServicoEntity servico =
                com.minhaempresa.gendaz.servico.entity.ServicoEntity.builder()
                        .id(1L).nome("Corte").empresa(empresa).build();
        com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity profissional =
                com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity.builder()
                        .id(1L).nome("Jo").empresa(empresa).build();
        return AgendamentoEntity.builder()
                .id(id).empresa(empresa).cliente(cliente).servico(servico).profissional(profissional)
                .status(status).build();
    }

    private PagamentoEntity pagamentoPago(AgendamentoEntity ag) {
        return PagamentoEntity.builder()
                .id(50L).agendamento(ag).cliente(cliente).empresa(empresa)
                .valor(new BigDecimal("200.00")).metodoPagamento(MetodoPagamento.PIX)
                .status(StatusPagamento.PAGO).build();
    }

    private PagamentoEntity pagamentoPendente(AgendamentoEntity ag) {
        return PagamentoEntity.builder()
                .id(51L).agendamento(ag).cliente(cliente).empresa(empresa)
                .valor(new BigDecimal("200.00"))
                .status(StatusPagamento.PENDENTE).build();
    }

    @Test
    void excluirAgendamentoPagoCancelaSemApagarHistorico() {
        AgendamentoEntity ag = agendamento(10L, StatusAgendamento.PENDENTE);
        PagamentoEntity pago = pagamentoPago(ag);
        pago.setDataPagamento(java.time.LocalDateTime.of(2026, 8, 10, 10, 0));
        when(agendamentoRepository.findByIdAndEmpresaIdForUpdate(10L, 1L)).thenReturn(Optional.of(ag));
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(pago));

        agendamentoService.excluir(10L, 1L);

        assertEquals(StatusAgendamento.CANCELADO, ag.getStatus());
        assertEquals(true, ag.isExcluidoAgenda());
        // Pagamento PAGO preservado integralmente pela regra central.
        assertEquals(StatusPagamento.PAGO, pago.getStatus());
        assertEquals(MetodoPagamento.PIX, pago.getMetodoPagamento());
        assertEquals(java.time.LocalDateTime.of(2026, 8, 10, 10, 0), pago.getDataPagamento());
        assertEquals(0, new BigDecimal("200.00").compareTo(pago.getValor()));
        verify(agendamentoRepository, never()).delete(any());
        verify(pagamentoRepository, never()).deleteByAgendamentoIdAndEmpresaId(anyLong(), anyLong());
        verify(pagamentoService).cancelarPagamentoPendenteDoAgendamento(10L, 1L);
        verifyNoInteractions(caixaDespesasService);
    }

    @Test
    void excluirComPagamentoPendenteCancelaPagamentoViaRegraCentral() {
        AgendamentoEntity ag = agendamento(10L, StatusAgendamento.CONFIRMADO);
        PagamentoEntity pendente = pagamentoPendente(ag);
        when(agendamentoRepository.findByIdAndEmpresaIdForUpdate(10L, 1L)).thenReturn(Optional.of(ag));
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(pendente));

        agendamentoService.excluir(10L, 1L);

        assertEquals(StatusAgendamento.CANCELADO, ag.getStatus());
        assertEquals(true, ag.isExcluidoAgenda());
        verify(agendamentoRepository, never()).delete(any());
        verify(pagamentoService).cancelarPagamentoPendenteDoAgendamento(10L, 1L);
        verifyNoInteractions(caixaDespesasService);
    }

    @Test
    void excluirAgendamentoFinalizadoMantemStatusESomeDaAgenda() {
        AgendamentoEntity ag = agendamento(11L, StatusAgendamento.FINALIZADO);
        when(agendamentoRepository.findByIdAndEmpresaIdForUpdate(11L, 1L)).thenReturn(Optional.of(ag));
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaIdForUpdate(11L, 1L)).thenReturn(Optional.empty());

        agendamentoService.excluir(11L, 1L);

        // FINALIZADO nao vira CANCELADO: historico preservado, apenas sai da Agenda.
        assertEquals(StatusAgendamento.FINALIZADO, ag.getStatus());
        assertEquals(true, ag.isExcluidoAgenda());
        verify(agendamentoRepository, never()).delete(any());
        verify(agendamentoRepository).save(ag);
        verify(pagamentoService).cancelarPagamentoPendenteDoAgendamento(11L, 1L);
        verifyNoInteractions(caixaDespesasService);
    }

    @Test
    void excluirAgendamentoNovoSemPagamentoFazSoftDelete() {
        AgendamentoEntity ag = agendamento(12L, StatusAgendamento.PENDENTE);
        when(agendamentoRepository.findByIdAndEmpresaIdForUpdate(12L, 1L)).thenReturn(Optional.of(ag));
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaIdForUpdate(12L, 1L)).thenReturn(Optional.empty());

        agendamentoService.excluir(12L, 1L);

        // Soft delete operacional: nunca DELETE fisico, mesmo sem pagamento.
        assertEquals(StatusAgendamento.CANCELADO, ag.getStatus());
        assertEquals(true, ag.isExcluidoAgenda());
        verify(agendamentoRepository, never()).delete(any());
        verify(agendamentoRepository).save(ag);
        verify(pagamentoRepository, never()).deleteByAgendamentoIdAndEmpresaId(anyLong(), anyLong());
    }

    @Test
    void excluirConfirmadoComPagamentoGeraCanceladoLogico() {
        AgendamentoEntity ag = agendamento(13L, StatusAgendamento.CONFIRMADO);
        when(agendamentoRepository.findByIdAndEmpresaIdForUpdate(13L, 1L)).thenReturn(Optional.of(ag));
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaIdForUpdate(13L, 1L))
                .thenReturn(Optional.of(pagamentoPago(ag)));

        agendamentoService.excluir(13L, 1L);

        assertEquals(StatusAgendamento.CANCELADO, ag.getStatus());
        assertEquals(true, ag.isExcluidoAgenda());
        verify(agendamentoRepository, never()).delete(any());
        verify(pagamentoRepository, never()).deleteByAgendamentoIdAndEmpresaId(anyLong(), anyLong());
    }

    @Test
    void excluirCanceladoEIdempotenteESeguro() {
        AgendamentoEntity comPagamento = agendamento(14L, StatusAgendamento.CANCELADO);
        PagamentoEntity pago = pagamentoPago(comPagamento);
        when(agendamentoRepository.findByIdAndEmpresaIdForUpdate(14L, 1L)).thenReturn(Optional.of(comPagamento));
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaIdForUpdate(14L, 1L))
                .thenReturn(Optional.of(pago));

        agendamentoService.excluir(14L, 1L);

        assertEquals(StatusAgendamento.CANCELADO, comPagamento.getStatus());
        assertEquals(true, comPagamento.isExcluidoAgenda());
        // Pagamento nao ressuscitado, Caixa intocado.
        assertEquals(StatusPagamento.PAGO, pago.getStatus());
        verify(agendamentoRepository, never()).delete(any());
        verify(pagamentoRepository, never()).deleteByAgendamentoIdAndEmpresaId(anyLong(), anyLong());
        verifyNoInteractions(caixaDespesasService);
    }

    @Test
    void excluirEmAndamentoViraCanceladoComSoftDelete() {
        AgendamentoEntity ag = agendamento(15L, StatusAgendamento.EM_ATENDIMENTO);
        PagamentoEntity pendente = pagamentoPendente(ag);
        when(agendamentoRepository.findByIdAndEmpresaIdForUpdate(15L, 1L)).thenReturn(Optional.of(ag));
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaIdForUpdate(15L, 1L))
                .thenReturn(Optional.of(pendente));

        agendamentoService.excluir(15L, 1L);

        assertEquals(StatusAgendamento.CANCELADO, ag.getStatus());
        assertEquals(true, ag.isExcluidoAgenda());
        verify(agendamentoRepository, never()).delete(any());
        verify(agendamentoRepository).save(ag);
        verify(pagamentoService).cancelarPagamentoPendenteDoAgendamento(15L, 1L);
        verifyNoInteractions(caixaDespesasService);
    }

    @Test
    void excluirEmAndamentoComPagamentoPagoPreservaPagamentoECaixa() {
        AgendamentoEntity ag = agendamento(18L, StatusAgendamento.EM_ATENDIMENTO);
        PagamentoEntity pago = pagamentoPago(ag);
        when(agendamentoRepository.findByIdAndEmpresaIdForUpdate(18L, 1L)).thenReturn(Optional.of(ag));
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaIdForUpdate(18L, 1L))
                .thenReturn(Optional.of(pago));

        agendamentoService.excluir(18L, 1L);

        assertEquals(StatusAgendamento.CANCELADO, ag.getStatus());
        assertEquals(true, ag.isExcluidoAgenda());
        assertEquals(StatusPagamento.PAGO, pago.getStatus());
        verify(agendamentoRepository, never()).delete(any());
        verify(pagamentoService).cancelarPagamentoPendenteDoAgendamento(18L, 1L);
        verifyNoInteractions(caixaDespesasService);
    }

    @Test
    void excluirPausadoContinuaBloqueadoSemTocarEmNada() {
        AgendamentoEntity ag = agendamento(15L, StatusAgendamento.PAUSADO);
        PagamentoEntity pago = pagamentoPago(ag);
        when(agendamentoRepository.findByIdAndEmpresaIdForUpdate(15L, 1L)).thenReturn(Optional.of(ag));
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaIdForUpdate(15L, 1L))
                .thenReturn(Optional.of(pago));

        assertThrows(BusinessException.class, () -> agendamentoService.excluir(15L, 1L));

        assertEquals(StatusAgendamento.PAUSADO, ag.getStatus());
        assertEquals(StatusPagamento.PAGO, pago.getStatus());
        verify(agendamentoRepository, never()).delete(any());
        verify(agendamentoRepository, never()).save(any());
        verify(pagamentoRepository, never()).deleteByAgendamentoIdAndEmpresaId(anyLong(), anyLong());
        verifyNoInteractions(pagamentoService);
        verifyNoInteractions(caixaDespesasService);
    }

    @Test
    void excluirFinalizadoComPagamentoPagoPreservaPagamentoECaixa() {
        AgendamentoEntity ag = agendamento(16L, StatusAgendamento.FINALIZADO);
        PagamentoEntity pago = pagamentoPago(ag);
        when(agendamentoRepository.findByIdAndEmpresaIdForUpdate(16L, 1L)).thenReturn(Optional.of(ag));
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaIdForUpdate(16L, 1L))
                .thenReturn(Optional.of(pago));

        agendamentoService.excluir(16L, 1L);

        // FINALIZADO continua FINALIZADO; pagamento PAGO e Caixa intactos.
        assertEquals(StatusAgendamento.FINALIZADO, ag.getStatus());
        assertEquals(true, ag.isExcluidoAgenda());
        assertEquals(StatusPagamento.PAGO, pago.getStatus());
        verify(agendamentoRepository, never()).delete(any());
        verify(pagamentoService).cancelarPagamentoPendenteDoAgendamento(16L, 1L);
        verifyNoInteractions(caixaDespesasService);
    }

    @Test
    void excluirCrossTenantBloqueadoAntesDeQualquerAlteracao() {
        AgendamentoEntity ag = agendamento(17L, StatusAgendamento.PENDENTE);
        when(agendamentoRepository.findByIdAndEmpresaIdForUpdate(17L, 1L)).thenReturn(Optional.of(ag));

        assertThrows(BusinessException.class, () -> agendamentoService.excluir(17L, 99L));

        assertEquals(StatusAgendamento.PENDENTE, ag.getStatus());
        verify(agendamentoRepository, never()).delete(any());
        verify(agendamentoRepository, never()).save(any());
        verifyNoInteractions(pagamentoRepository);
    }

    @Test
    void bulkExcluirProcessaEmAndamentoEFinalizadoEBloqueiaPausado() {
        AgendamentoBulkService bulk = new AgendamentoBulkService(agendamentoService);
        AgendamentoEntity emAt = agendamento(21L, StatusAgendamento.EM_ATENDIMENTO);
        AgendamentoEntity pausado = agendamento(22L, StatusAgendamento.PAUSADO);
        AgendamentoEntity finalizado = agendamento(23L, StatusAgendamento.FINALIZADO);
        when(agendamentoRepository.findByIdAndEmpresaIdForUpdate(21L, 1L)).thenReturn(Optional.of(emAt));
        when(agendamentoRepository.findByIdAndEmpresaIdForUpdate(22L, 1L)).thenReturn(Optional.of(pausado));
        when(agendamentoRepository.findByIdAndEmpresaIdForUpdate(23L, 1L)).thenReturn(Optional.of(finalizado));

        var response = bulk.executar(new AcaoEmMassaAgendamentoRequest(List.of(21L, 22L, 23L), "EXCLUIR", 1L));

        assertEquals(2, response.totalProcessado());
        assertEquals(1, response.falhas().size());
        assertEquals(StatusAgendamento.CANCELADO, emAt.getStatus());
        assertEquals(true, emAt.isExcluidoAgenda());
        assertEquals(StatusAgendamento.PAUSADO, pausado.getStatus());
        assertEquals(StatusAgendamento.FINALIZADO, finalizado.getStatus());
        assertEquals(true, finalizado.isExcluidoAgenda());
        verify(agendamentoRepository, never()).delete(any());
        verify(pagamentoRepository, never()).deleteByAgendamentoIdAndEmpresaId(anyLong(), anyLong());
    }

    @Test
    void bulkExcluirDelegaParaExclusaoIndividual() {
        AgendamentoBulkService bulk = new AgendamentoBulkService(agendamentoService);
        AgendamentoEntity ag = agendamento(20L, StatusAgendamento.PENDENTE);
        when(agendamentoRepository.findByIdAndEmpresaIdForUpdate(20L, 1L)).thenReturn(Optional.of(ag));
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaIdForUpdate(20L, 1L)).thenReturn(Optional.empty());

        var response = bulk.executar(new AcaoEmMassaAgendamentoRequest(List.of(20L), "EXCLUIR", 1L));

        assertEquals(1, response.totalProcessado());
        assertEquals(0, response.falhas().size());
        // Soft delete via regra central: sem DELETE fisico, sem apagar pagamento.
        assertEquals(StatusAgendamento.CANCELADO, ag.getStatus());
        assertEquals(true, ag.isExcluidoAgenda());
        verify(agendamentoRepository, never()).delete(any());
        verify(pagamentoRepository, never()).deleteByAgendamentoIdAndEmpresaId(anyLong(), anyLong());
    }

    @Test
    void bulkCancelarEmAtendimentoUsaRegraCentral() {
        AgendamentoBulkService bulk = new AgendamentoBulkService(agendamentoService);
        AgendamentoEntity emAt = agendamento(21L, StatusAgendamento.EM_ATENDIMENTO);
        when(agendamentoRepository.findByIdAndEmpresaIdForUpdate(21L, 1L)).thenReturn(Optional.of(emAt));

        var response = bulk.executar(new AcaoEmMassaAgendamentoRequest(List.of(21L), "CANCELAR", 1L));

        assertEquals(1, response.totalProcessado());
        assertEquals(0, response.falhas().size());
        assertEquals(StatusAgendamento.CANCELADO, emAt.getStatus());
    }
}

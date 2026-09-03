package com.minhaempresa.gendaz.agendamento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AcaoEmMassaAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AtualizarAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.RemarcarAgendamentoRequest;
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
import com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity;
import com.minhaempresa.gendaz.profissional.service.ProfissionalService;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import com.minhaempresa.gendaz.servico.service.ServicoService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.SanitizacaoService;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
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
 * Maquina de estados no nivel de service: cada acao (individual, bulk,
 * Meu Gendaz) respeita a mesma regra central.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgendamentoStatusMachineServiceTest {
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
    private ServicoEntity servico;
    private ProfissionalEntity profissional;

    @BeforeEach
    void setup() {
        CompanyContext.setCompanyId(1L);
        org.springframework.test.util.ReflectionTestUtils.setField(agendamentoService, "appTimezone", "America/Cuiaba");
        org.springframework.test.util.ReflectionTestUtils.setField(agendamentoService, "pagamentoService", pagamentoService);
        empresa = EmpresaEntity.builder().id(1L).timezone("America/Cuiaba").build();
        cliente = ClienteEntity.builder().id(1L).nome("Ana").empresa(empresa).status(StatusCadastro.ATIVO).build();
        servico = ServicoEntity.builder().id(1L).nome("Corte").duracaoMinutos(30)
                .valor(new BigDecimal("100.00")).status(StatusCadastro.ATIVO).empresa(empresa).build();
        profissional = ProfissionalEntity.builder().id(1L).nome("Jo").status(StatusCadastro.ATIVO)
                .diasTrabalho(EnumSet.allOf(com.minhaempresa.gendaz.profissional.enums.DiaSemana.class))
                .empresa(empresa).build();
        when(agendamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(empresaService.buscarEntidade(1L)).thenReturn(empresa);
        when(profissionalService.buscarEntidadeParaReserva(any(), any())).thenReturn(profissional);
        when(agendaBlockedDayService.diaBloqueado(any(), any(), any())).thenReturn(false);
        when(agendamentoRepository.existeConflitoDeHorario(any(), any(), any(), any(), any(), any())).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private AgendamentoEntity agendamento(Long id, StatusAgendamento status) {
        return AgendamentoEntity.builder()
                .id(id).empresa(empresa).cliente(cliente).servico(servico).profissional(profissional)
                .data(LocalDate.now().plusDays(1)).horaInicio(LocalTime.of(9, 0)).horaFim(LocalTime.of(9, 30))
                .status(status).build();
    }

    private void carregar(AgendamentoEntity ag) {
        when(agendamentoRepository.findByIdAndEmpresaIdForUpdate(ag.getId(), 1L)).thenReturn(Optional.of(ag));
    }

    // ---- confirmar / iniciar / pausar / retomar ----

    @Test
    void confirmarPendenteOkECanceladoOuFinalizadoBloqueados() {
        AgendamentoEntity pendente = agendamento(1L, StatusAgendamento.PENDENTE);
        carregar(pendente);
        agendamentoService.confirmar(1L);
        assertEquals(StatusAgendamento.CONFIRMADO, pendente.getStatus());

        AgendamentoEntity cancelado = agendamento(1L, StatusAgendamento.CANCELADO);
        carregar(cancelado);
        assertThrows(BusinessException.class, () -> agendamentoService.confirmar(1L));

        AgendamentoEntity finalizado = agendamento(1L, StatusAgendamento.FINALIZADO);
        carregar(finalizado);
        assertThrows(BusinessException.class, () -> agendamentoService.confirmar(1L));
    }

    @Test
    void iniciarSomentePendenteOuConfirmado() {
        for (StatusAgendamento permitido : List.of(StatusAgendamento.PENDENTE, StatusAgendamento.CONFIRMADO)) {
            AgendamentoEntity ag = agendamento(2L, permitido);
            carregar(ag);
            agendamentoService.iniciar(2L);
            assertEquals(StatusAgendamento.EM_ATENDIMENTO, ag.getStatus());
        }

        for (StatusAgendamento bloqueado : List.of(StatusAgendamento.EM_ATENDIMENTO, StatusAgendamento.PAUSADO,
                StatusAgendamento.FINALIZADO, StatusAgendamento.CANCELADO)) {
            AgendamentoEntity ag = agendamento(2L, bloqueado);
            carregar(ag);
            assertThrows(BusinessException.class, () -> agendamentoService.iniciar(2L), "iniciar de " + bloqueado);
        }
    }

    @Test
    void pausarSomenteEmAtendimentoERetomarSomentePausado() {
        AgendamentoEntity emAt = agendamento(3L, StatusAgendamento.EM_ATENDIMENTO);
        carregar(emAt);
        agendamentoService.pausar(3L);
        assertEquals(StatusAgendamento.PAUSADO, emAt.getStatus());

        AgendamentoEntity pendente = agendamento(3L, StatusAgendamento.PENDENTE);
        carregar(pendente);
        assertThrows(BusinessException.class, () -> agendamentoService.pausar(3L));

        AgendamentoEntity pausado = agendamento(3L, StatusAgendamento.PAUSADO);
        carregar(pausado);
        agendamentoService.retomar(3L);
        assertEquals(StatusAgendamento.EM_ATENDIMENTO, pausado.getStatus());

        AgendamentoEntity confirmado = agendamento(3L, StatusAgendamento.CONFIRMADO);
        carregar(confirmado);
        assertThrows(BusinessException.class, () -> agendamentoService.retomar(3L));
    }

    // ---- finalizar origem ----

    @Test
    void finalizarSomenteDeEmAtendimentoOuPausado() {
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaIdForUpdate(any(), any()))
                .thenReturn(Optional.empty());
        AgendamentoEntity pausado = agendamento(4L, StatusAgendamento.PAUSADO);
        carregar(pausado);
        agendamentoService.finalizar(4L, false, null, null);
        assertEquals(StatusAgendamento.FINALIZADO, pausado.getStatus());

        for (StatusAgendamento bloqueado : List.of(StatusAgendamento.PENDENTE, StatusAgendamento.CONFIRMADO,
                StatusAgendamento.CANCELADO, StatusAgendamento.FINALIZADO)) {
            AgendamentoEntity ag = agendamento(4L, bloqueado);
            carregar(ag);
            assertThrows(BusinessException.class, () -> agendamentoService.finalizar(4L, false, null, null),
                    "finalizar de " + bloqueado);
        }
        verify(caixaDespesasService, never()).registrarPagamentoAprovado(any());
    }

    // ---- reabrir ----

    @Test
    void reabrirFinalizadoVaiParaEmAtendimentoSemTocarPagamentoOuCaixa() {
        AgendamentoEntity finalizado = agendamento(5L, StatusAgendamento.FINALIZADO);
        carregar(finalizado);

        var response = agendamentoService.reabrir(5L);

        assertEquals(StatusAgendamento.EM_ATENDIMENTO, response.status());
        verifyNoInteractions(pagamentoRepository);
        verifyNoInteractions(caixaDespesasService);
    }

    @Test
    void reabrirSomenteFinalizado() {
        for (StatusAgendamento bloqueado : List.of(StatusAgendamento.PENDENTE, StatusAgendamento.CONFIRMADO,
                StatusAgendamento.EM_ATENDIMENTO, StatusAgendamento.PAUSADO, StatusAgendamento.CANCELADO)) {
            AgendamentoEntity ag = agendamento(6L, bloqueado);
            carregar(ag);
            assertThrows(BusinessException.class, () -> agendamentoService.reabrir(6L), "reabrir de " + bloqueado);
        }
    }

    // ---- reagendar ----

    private RemarcarAgendamentoRequest remarcarReq() {
        return new RemarcarAgendamentoRequest(LocalDate.now().plusDays(2), LocalTime.of(10, 0));
    }

    @Test
    void reagendarPendenteMantemEConfirmadoVoltaAPendente() {
        AgendamentoEntity pendente = agendamento(7L, StatusAgendamento.PENDENTE);
        carregar(pendente);
        agendamentoService.remarcar(7L, remarcarReq());
        assertEquals(StatusAgendamento.PENDENTE, pendente.getStatus());

        AgendamentoEntity confirmado = agendamento(7L, StatusAgendamento.CONFIRMADO);
        carregar(confirmado);
        agendamentoService.remarcar(7L, remarcarReq());
        assertEquals(StatusAgendamento.PENDENTE, confirmado.getStatus());
    }

    @Test
    void reagendarEstadosEncerradosOuEmAndamentoBloqueado() {
        for (StatusAgendamento bloqueado : List.of(StatusAgendamento.FINALIZADO, StatusAgendamento.CANCELADO,
                StatusAgendamento.EM_ATENDIMENTO, StatusAgendamento.PAUSADO)) {
            AgendamentoEntity ag = agendamento(8L, bloqueado);
            carregar(ag);
            assertThrows(BusinessException.class, () -> agendamentoService.remarcar(8L, remarcarReq()),
                    "reagendar de " + bloqueado);
        }
        verify(agendamentoRepository, never()).save(any());
    }

    // ---- atualizar ----

    private AtualizarAgendamentoRequest editarReq(StatusAgendamento status) {
        return new AtualizarAgendamentoRequest(1L, 1L, 1L, 1L,
                LocalDate.now().plusDays(1), LocalTime.of(10, 0), status, null);
    }

    @Test
    void editarRespeitaMatriz() {
        when(clienteService.buscarEntidadeOperacional(1L)).thenReturn(cliente);
        when(servicoService.buscarEntidadeOperacional(1L)).thenReturn(servico);
        when(profissionalService.buscarEntidade(1L)).thenReturn(profissional);
        when(profissionalService.buscarEntidadeParaReserva(eq(1L), eq(1L))).thenReturn(profissional);
        when(sanitizacaoService.texto(any())).thenAnswer(inv -> inv.getArgument(0));

        AgendamentoEntity pendente = agendamento(9L, StatusAgendamento.PENDENTE);
        carregar(pendente);
        agendamentoService.atualizar(9L, editarReq(StatusAgendamento.CONFIRMADO));
        assertEquals(StatusAgendamento.CONFIRMADO, pendente.getStatus());

        AgendamentoEntity emAt = agendamento(9L, StatusAgendamento.EM_ATENDIMENTO);
        carregar(emAt);
        assertThrows(BusinessException.class, () -> agendamentoService.atualizar(9L, editarReq(StatusAgendamento.PENDENTE)));

        AgendamentoEntity finalizado = agendamento(9L, StatusAgendamento.FINALIZADO);
        carregar(finalizado);
        assertThrows(BusinessException.class, () -> agendamentoService.atualizar(9L, editarReq(StatusAgendamento.PENDENTE)));
    }

    // ---- cancelar ----

    @Test
    void cancelarSomentePendenteOuConfirmadoERecancelarEIdempotente() {
        AgendamentoEntity confirmado = agendamento(10L, StatusAgendamento.CONFIRMADO);
        carregar(confirmado);
        agendamentoService.cancelar(10L, 1L);
        assertEquals(StatusAgendamento.CANCELADO, confirmado.getStatus());

        // Recancelar e idempotente (duplo clique/retry/bulk): sem erro, sem efeito.
        AgendamentoEntity cancelado = agendamento(10L, StatusAgendamento.CANCELADO);
        carregar(cancelado);
        agendamentoService.cancelar(10L, 1L);
        assertEquals(StatusAgendamento.CANCELADO, cancelado.getStatus());

        for (StatusAgendamento bloqueado : List.of(StatusAgendamento.FINALIZADO,
                StatusAgendamento.EM_ATENDIMENTO, StatusAgendamento.PAUSADO)) {
            AgendamentoEntity ag = agendamento(10L, bloqueado);
            carregar(ag);
            assertThrows(BusinessException.class, () -> agendamentoService.cancelar(10L, 1L),
                    "cancelar de " + bloqueado);
        }
    }

    // ---- bulk ----

    @Test
    void bulkPendenteDescontinuadoECancelarRespeitaEstados() {
        AgendamentoBulkService bulk = new AgendamentoBulkService(agendamentoService);

        assertThrows(BusinessException.class, () -> bulk.executar(
                new AcaoEmMassaAgendamentoRequest(List.of(11L), "PENDENTE", 1L)));

        AgendamentoEntity finalizado = agendamento(11L, StatusAgendamento.FINALIZADO);
        when(agendamentoRepository.findByIdAndEmpresaIdForUpdate(11L, 1L)).thenReturn(Optional.of(finalizado));
        var resp = bulk.executar(new AcaoEmMassaAgendamentoRequest(List.of(11L), "CANCELAR", 1L));
        assertEquals(0, resp.totalProcessado());
        assertEquals(1, resp.falhas().size());
        assertEquals(StatusAgendamento.FINALIZADO, finalizado.getStatus());
    }

    @Test
    void bulkExcluirNaoConverteFinalizadoEmCancelado() {
        AgendamentoBulkService bulk = new AgendamentoBulkService(agendamentoService);
        AgendamentoEntity finalizado = agendamento(12L, StatusAgendamento.FINALIZADO);
        when(agendamentoRepository.findByIdAndEmpresaIdForUpdate(12L, 1L)).thenReturn(Optional.of(finalizado));

        var resp = bulk.executar(new AcaoEmMassaAgendamentoRequest(List.of(12L), "EXCLUIR", 1L));

        assertEquals(0, resp.totalProcessado());
        assertEquals(1, resp.falhas().size());
        assertEquals(StatusAgendamento.FINALIZADO, finalizado.getStatus());
    }

    // ---- Meu Gendaz ----

    @Test
    void meuGendazNaoReagendaNemCancelaFinalizadoProprio() {
        AgendamentoEntity finalizado = agendamento(13L, StatusAgendamento.FINALIZADO);
        when(agendamentoRepository.findByIdAndEmpresaIdAndClienteIdForUpdate(13L, 1L, 1L))
                .thenReturn(Optional.of(finalizado));

        assertThrows(BusinessException.class,
                () -> agendamentoService.remarcarParaCliente(13L, remarcarReq(), 1L, 1L));
        assertThrows(BusinessException.class,
                () -> agendamentoService.cancelarParaCliente(13L, 1L, 1L));
        assertEquals(StatusAgendamento.FINALIZADO, finalizado.getStatus());
        verify(agendamentoRepository, never()).save(any());
    }

    // ---- pagamento preservado ao reabrir ----

    @Test
    void reabrirMantemPagamentoPagoSemCaixa() {
        AgendamentoEntity finalizado = agendamento(14L, StatusAgendamento.FINALIZADO);
        carregar(finalizado);
        PagamentoEntity pago = PagamentoEntity.builder()
                .id(50L).agendamento(finalizado).cliente(cliente).empresa(empresa)
                .valor(new BigDecimal("200.00")).metodoPagamento(MetodoPagamento.PIX)
                .status(StatusPagamento.PAGO).build();
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaId(14L, 1L)).thenReturn(Optional.of(pago));

        agendamentoService.reabrir(14L);

        assertEquals(StatusAgendamento.EM_ATENDIMENTO, finalizado.getStatus());
        assertEquals(StatusPagamento.PAGO, pago.getStatus());
        verifyNoInteractions(caixaDespesasService);
    }
}

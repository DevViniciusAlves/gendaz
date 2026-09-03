package com.minhaempresa.gendaz.agendamento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.math.BigDecimal;
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
 * Parte 2 — transicoes formais do finalizar:
 * PENDENTE->PAGO (caixa 1x), PAGO->PAGO (sem duplicar),
 * PAGO->PENDENTE via finalizar (bloqueado), FINALIZADO->finalizar (bloqueado).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinalizarRegrasServiceTest {
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
    private AgendamentoEntity agendamento;

    @BeforeEach
    void setup() {
        CompanyContext.setCompanyId(1L);
        org.springframework.test.util.ReflectionTestUtils.setField(agendamentoService, "appTimezone", "America/Cuiaba");
        org.springframework.test.util.ReflectionTestUtils.setField(agendamentoService, "pagamentoService", pagamentoService);
        empresa = EmpresaEntity.builder().id(1L).timezone("America/Cuiaba").build();
        ClienteEntity cliente = ClienteEntity.builder().id(1L).nome("Ana").empresa(empresa).build();
        ServicoEntity servico = ServicoEntity.builder().id(1L).nome("Corte").duracaoMinutos(30).empresa(empresa).build();
        ProfissionalEntity profissional = ProfissionalEntity.builder().id(1L).nome("Jo").empresa(empresa).build();
        agendamento = AgendamentoEntity.builder()
                .id(10L).empresa(empresa).cliente(cliente).servico(servico).profissional(profissional)
                .status(StatusAgendamento.EM_ATENDIMENTO).build();
        when(agendamentoRepository.findByIdAndEmpresaIdForUpdate(10L, 1L)).thenReturn(Optional.of(agendamento));
        when(agendamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pagamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(formaPagamentoEmpresaService.normalizarMetodoManual(MetodoPagamento.PIX)).thenReturn(MetodoPagamento.PIX);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private PagamentoEntity pagamento(StatusPagamento status) {
        return PagamentoEntity.builder()
                .id(5L).agendamento(agendamento)
                .cliente(agendamento.getCliente()).empresa(empresa)
                .valor(new BigDecimal("200.00")).metodoPagamento(MetodoPagamento.PIX)
                .status(status).build();
    }

    @Test
    void emAtendimentoParaPagoRegistraCaixaUmaVezERefinalizarBloqueia() {
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(pagamento(StatusPagamento.PENDENTE)));

        agendamentoService.finalizar(10L, true, MetodoPagamento.PIX, null);

        assertEquals(StatusAgendamento.FINALIZADO, agendamento.getStatus());
        verify(caixaDespesasService).registrarPagamentoAprovado(any());

        // Segunda finalizacao do mesmo agendamento: bloqueada no backend.
        assertThrows(BusinessException.class,
                () -> agendamentoService.finalizar(10L, true, MetodoPagamento.PIX, null));
        verify(caixaDespesasService, org.mockito.Mockito.times(1)).registrarPagamentoAprovado(any());
    }

    @Test
    void pagoParaNaoPagoViaFinalizarEBloqueadoSemMexerNoCaixa() {
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(pagamento(StatusPagamento.PAGO)));

        assertThrows(BusinessException.class,
                () -> agendamentoService.finalizar(10L, false, null, null));

        verify(caixaDespesasService, never()).registrarPagamentoAprovado(any());
        verify(caixaDespesasService, never()).registrarPagamentoRemovido(any(), any());
    }

    @Test
    void finalizarSemPagamentoNaoTocaNoCaixa() {
        PagamentoEntity pendente = pagamento(StatusPagamento.PENDENTE);
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(pendente));

        agendamentoService.finalizar(10L, false, null, null);

        assertEquals(StatusPagamento.PENDENTE, pendente.getStatus());
        verify(caixaDespesasService, never()).registrarPagamentoAprovado(any());
    }

    @Test
    void finalizarDePendenteOuCanceladoBloqueado() {
        agendamento.setStatus(StatusAgendamento.PENDENTE);
        assertThrows(BusinessException.class,
                () -> agendamentoService.finalizar(10L, true, MetodoPagamento.PIX, null));
        agendamento.setStatus(StatusAgendamento.CANCELADO);
        assertThrows(BusinessException.class,
                () -> agendamentoService.finalizar(10L, true, MetodoPagamento.PIX, null));
        verify(caixaDespesasService, never()).registrarPagamentoAprovado(any());
    }

    @Test
    void finalizarComPagamentoCanceladoNaoRessuscitaExigeRegularizacao() {
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(pagamento(StatusPagamento.CANCELADO)));

        assertThrows(BusinessException.class,
                () -> agendamentoService.finalizar(10L, true, MetodoPagamento.PIX, null));
        assertThrows(BusinessException.class,
                () -> agendamentoService.finalizarPreservandoPagamento(10L, 1L));

        assertEquals(StatusAgendamento.EM_ATENDIMENTO, agendamento.getStatus());
        verify(caixaDespesasService, never()).registrarPagamentoAprovado(any());
    }

    @Test
    void finalizarPreservandoPagamentoRepassaEstadoAtualSemCaixaDuplo() {
        PagamentoEntity pago = pagamento(StatusPagamento.PAGO);
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(pago));

        agendamentoService.finalizarPreservandoPagamento(10L, 1L);

        assertEquals(StatusAgendamento.FINALIZADO, agendamento.getStatus());
        assertEquals(StatusPagamento.PAGO, pago.getStatus());
        verify(caixaDespesasService, never()).registrarPagamentoAprovado(any());
    }

    @Test
    void editarParaFinalizadoDiretoEBloqueado() {
        when(clienteService.buscarEntidadeOperacional(any())).thenReturn(agendamento.getCliente());
        when(servicoService.buscarEntidadeOperacional(any())).thenReturn(agendamento.getServico());
        when(profissionalService.buscarEntidade(any())).thenReturn(agendamento.getProfissional());
        when(empresaService.buscarEntidade(any())).thenReturn(empresa);

        assertThrows(BusinessException.class, () -> agendamentoService.atualizar(10L,
                new com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AtualizarAgendamentoRequest(
                        1L, 1L, 1L, 1L, java.time.LocalDate.now().plusDays(1),
                        java.time.LocalTime.of(10, 0), StatusAgendamento.FINALIZADO, null)));

        verify(agendamentoRepository, never()).save(any());
    }
}

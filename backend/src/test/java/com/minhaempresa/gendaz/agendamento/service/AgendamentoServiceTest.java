package com.minhaempresa.gendaz.agendamento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.CriarAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AtualizarAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AgendamentoResponse;
import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.agendamento.service.AgendaBlockedDayService;
import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.service.ClienteService;
import com.minhaempresa.gendaz.email.ResendEmailService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.service.EmpresaService;
import com.minhaempresa.gendaz.horarioatendimento.service.HorarioAtendimentoService;
import com.minhaempresa.gendaz.meugendazpromocao.dto.MeuGendazPromocaoDtos.CupomAplicadoResult;
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
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.shared.SanitizacaoService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgendamentoServiceTest {
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
    @Mock TransactionTemplate transactionTemplate;
    @Captor ArgumentCaptor<AgendamentoEntity> agendamentoCaptor;
    @Captor ArgumentCaptor<PagamentoEntity> pagamentoCaptor;
    @InjectMocks AgendamentoService agendamentoService;

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(agendamentoService, "appTimezone", "America/Cuiaba");
        ReflectionTestUtils.setField(agendamentoService, "pagamentoService", pagamentoService);
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private ServicoEntity preparaCriacao(BigDecimal valorServico, CupomAplicadoResult cupom) {
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).timezone("America/Cuiaba").build();
        ClienteEntity cliente = ClienteEntity.builder().id(1L).nome("Ana").empresa(empresa).build();
        ServicoEntity servico = ServicoEntity.builder().id(1L).nome("Consulta").duracaoMinutos(60).empresa(empresa).valor(valorServico).build();
        ProfissionalEntity profissional = ProfissionalEntity.builder().id(1L).nome("Dra. Marina").status(com.minhaempresa.gendaz.shared.enums.StatusCadastro.ATIVO).diasTrabalho(java.util.EnumSet.allOf(com.minhaempresa.gendaz.profissional.enums.DiaSemana.class)).empresa(empresa).build();
        when(clienteService.buscarEntidadeOperacional(1L)).thenReturn(cliente);
        when(servicoService.buscarEntidade(1L)).thenReturn(servico);
        when(profissionalService.buscarEntidade(1L)).thenReturn(profissional);
        when(empresaService.buscarEntidade(1L)).thenReturn(empresa);
        when(agendamentoRepository.existeConflitoDeHorario(any(), any(), any(), any(), any(), any())).thenReturn(false);
        when(agendaBlockedDayService.diaBloqueado(any(), any(), any())).thenReturn(false);
        when(agendamentoRepository.save(any())).thenAnswer(invocation -> {
            AgendamentoEntity agendamento = invocation.getArgument(0);
            if (agendamento.getId() == null) {
                agendamento.setId(10L);
            }
            return agendamento;
        });
        when(pagamentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        if (cupom != null) {
            when(meuGendazPromocaoService.aplicarCupomAoAgendamento(any(), any(), any(), any(), any()))
                    .thenReturn(cupom);
        }
        return servico;
    }

    private CriarAgendamentoRequest requestBase(String cupom) {
        return new CriarAgendamentoRequest(1L, 1L, 1L, 1L, LocalDate.now().plusDays(1), LocalTime.of(9, 0), cupom, null);
    }

    @Test
    void deveCalcularHoraFimPelaDuracaoDoServico() {
        preparaCriacao(new BigDecimal("100.00"), null);

        var response = agendamentoService.criar(requestBase(null));

        assertEquals(LocalTime.of(10, 0), response.horaFim());
        verify(resendEmailService).enviarEmailNovoAgendamento(any(EmpresaEntity.class), agendamentoCaptor.capture());
        assertEquals("10", agendamentoCaptor.getValue().getId().toString());
    }

    @Test
    void cupomFixo50EmServico100CalculaSnapshotEPagamento50() {
        preparaCriacao(new BigDecimal("100.00"),
                new CupomAplicadoResult("TESTE50", "VALOR_FIXO", new BigDecimal("50.00"), new BigDecimal("50.00"), 999L));

        var response = agendamentoService.criar(requestBase("TESTE50"));

        assertEquals(0, new BigDecimal("100.00").compareTo(response.valorOriginal()));
        assertEquals(0, new BigDecimal("50.00").compareTo(response.valorDesconto()));
        assertEquals(0, new BigDecimal("50.00").compareTo(response.valorFinal()));
        assertEquals("TESTE50", response.cupomCodigo());
        assertEquals("VALOR_FIXO", response.tipoPromocaoAplicada());
        verify(pagamentoRepository).save(pagamentoCaptor.capture());
        assertEquals(0, new BigDecimal("50.00").compareTo(pagamentoCaptor.getValue().getValor()));
        assertEquals(StatusPagamento.PENDENTE, pagamentoCaptor.getValue().getStatus());
    }

    @Test
    void semCupomCalculaSemDescontoEPagamentoValorCheio() {
        preparaCriacao(new BigDecimal("100.00"), null);

        var response = agendamentoService.criar(requestBase(null));

        assertEquals(0, new BigDecimal("100.00").compareTo(response.valorOriginal()));
        assertEquals(0, new BigDecimal("0.00").compareTo(response.valorDesconto()));
        assertEquals(0, new BigDecimal("100.00").compareTo(response.valorFinal()));
        assertEquals(0, new BigDecimal("100.00").compareTo(response.valor()));
        org.junit.jupiter.api.Assertions.assertNull(response.cupomCodigo());
        verify(pagamentoRepository).save(pagamentoCaptor.capture());
        assertEquals(0, new BigDecimal("100.00").compareTo(pagamentoCaptor.getValue().getValor()));
    }

    @Test
    void cupomFixo150EmServico100ResultaFinalZero() {
        preparaCriacao(new BigDecimal("100.00"),
                new CupomAplicadoResult("TESTE150", "VALOR_FIXO", new BigDecimal("150.00"), new BigDecimal("100.00"), 999L));

        var response = agendamentoService.criar(requestBase("TESTE150"));

        assertEquals(0, new BigDecimal("100.00").compareTo(response.valorDesconto()));
        assertEquals(0, new BigDecimal("0.00").compareTo(response.valorFinal()));
        verify(pagamentoRepository).save(pagamentoCaptor.capture());
        assertEquals(0, new BigDecimal("0.00").compareTo(pagamentoCaptor.getValue().getValor()));
    }

    @Test
    void snapshotNaoEhAfetadoPorAlteracoesPosterioresNoServicoOuPromocao() {
        ServicoEntity servico = preparaCriacao(new BigDecimal("100.00"),
                new CupomAplicadoResult("TESTE50", "VALOR_FIXO", new BigDecimal("50.00"), new BigDecimal("50.00"), 999L));

        agendamentoService.criar(requestBase("TESTE50"));

        verify(agendamentoRepository, times(2)).save(agendamentoCaptor.capture());
        AgendamentoEntity salvo = agendamentoCaptor.getAllValues().get(1);
        assertEquals(0, new BigDecimal("100.00").compareTo(salvo.getValorOriginal()));
        assertEquals(0, new BigDecimal("50.00").compareTo(salvo.getValorDesconto()));
        assertEquals(0, new BigDecimal("50.00").compareTo(salvo.getValorFinal()));

        servico.setValor(new BigDecimal("250.00"));
        assertEquals(0, new BigDecimal("100.00").compareTo(salvo.getValorOriginal()));
        assertEquals(0, new BigDecimal("50.00").compareTo(salvo.getValorFinal()));
        assertEquals("TESTE50", salvo.getCupomCodigo());
    }

    @Test
    void falhaAoRegistrarCupomNaoImpedeCriacaoDeAgendamento() {
        preparaCriacao(new BigDecimal("100.00"), null);
        when(meuGendazPromocaoService.aplicarCupomAoAgendamento(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Voce ja usou este cupom."));

        var response = agendamentoService.criar(requestBase("USADO"));

        assertEquals(0, new BigDecimal("100.00").compareTo(response.valorOriginal()));
        assertEquals(0, new BigDecimal("0.00").compareTo(response.valorDesconto()));
        assertEquals(0, new BigDecimal("100.00").compareTo(response.valorFinal()));
        verify(pagamentoRepository).save(pagamentoCaptor.capture());
        assertEquals(0, new BigDecimal("100.00").compareTo(pagamentoCaptor.getValue().getValor()));
    }

    @Test
    void finalizarNaoRecalculaValorDoPagamento() {
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).timezone("America/Cuiaba").build();
        ClienteEntity cliente = ClienteEntity.builder().id(1L).nome("Ana").build();
        ServicoEntity servico = ServicoEntity.builder().id(1L).nome("Consulta").build();
        ProfissionalEntity profissional = ProfissionalEntity.builder().id(1L).nome("Dra. Marina").build();
        AgendamentoEntity agendamento = AgendamentoEntity.builder()
                .id(10L)
                .empresa(empresa)
                .cliente(cliente)
                .servico(servico)
                .profissional(profissional)
                .status(com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento.PENDENTE)
                .build();
        PagamentoEntity pagamento = PagamentoEntity.builder()
                .agendamento(agendamento)
                .valor(new BigDecimal("50.00"))
                .status(StatusPagamento.PENDENTE)
                .metodoPagamento(MetodoPagamento.OUTRO)
                .build();
        when(agendamentoRepository.findById(10L)).thenReturn(Optional.of(agendamento));
        when(agendamentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(pagamento));
        when(formaPagamentoEmpresaService.normalizarMetodoManual(MetodoPagamento.PIX)).thenReturn(MetodoPagamento.PIX);
        when(formaPagamentoEmpresaService.normalizarParcelas(MetodoPagamento.PIX, 2)).thenReturn(null);
        CompanyContext.setCompanyId(1L);

        agendamentoService.finalizar(10L, true, MetodoPagamento.PIX, 2);

        assertEquals(StatusPagamento.PAGO, pagamento.getStatus());
        assertEquals(0, new BigDecimal("50.00").compareTo(pagamento.getValor()));
        verify(formaPagamentoEmpresaService).validarPagamentoManual(eq(1L), eq(MetodoPagamento.PIX), eq(2));
    }

    @Test
    void criarNaoPermiteClienteExcluido() {
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).timezone("America/Cuiaba").build();
        ClienteEntity cliente = ClienteEntity.builder().id(1L).nome("Ana").empresa(empresa).status(com.minhaempresa.gendaz.shared.enums.StatusCadastro.EXCLUIDO).build();
        ServicoEntity servico = ServicoEntity.builder().id(1L).nome("Consulta").duracaoMinutos(60).empresa(empresa).valor(new BigDecimal("100.00")).build();
        ProfissionalEntity profissional = ProfissionalEntity.builder().id(1L).nome("Dra. Marina").status(com.minhaempresa.gendaz.shared.enums.StatusCadastro.ATIVO).diasTrabalho(java.util.EnumSet.allOf(com.minhaempresa.gendaz.profissional.enums.DiaSemana.class)).empresa(empresa).build();
        when(clienteService.buscarEntidadeOperacional(1L)).thenThrow(new BusinessException("Não é possível agendar para um cliente excluído."));
        when(servicoService.buscarEntidade(1L)).thenReturn(servico);
        when(profissionalService.buscarEntidade(1L)).thenReturn(profissional);
        when(empresaService.buscarEntidade(1L)).thenReturn(empresa);

        assertThrows(BusinessException.class, () -> agendamentoService.criar(requestBase(null)));
    }

    private AgendamentoEntity agendamentoCancelavel(Long id, Boolean empresaId2) {
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).timezone("America/Cuiaba").build();
        ClienteEntity cliente = ClienteEntity.builder().id(1L).nome("Ana").build();
        ServicoEntity servico = ServicoEntity.builder().id(1L).nome("Consulta").build();
        ProfissionalEntity profissional = ProfissionalEntity.builder().id(1L).nome("Dra. Marina").build();
        return AgendamentoEntity.builder()
                .id(id)
                .empresa(empresa)
                .cliente(cliente)
                .servico(servico)
                .profissional(profissional)
                .status(com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento.PENDENTE)
                .build();
    }

    @Test
    void cancelarAgendamentoComPagamentoPendenteChamaCancelamentoDoPagamento() {
        AgendamentoEntity agendamento = agendamentoCancelavel(10L, false);
        CompanyContext.setCompanyId(1L);
        when(agendamentoRepository.findById(10L)).thenReturn(Optional.of(agendamento));
        when(agendamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = agendamentoService.cancelar(10L);

        assertEquals(StatusAgendamento.CANCELADO, response.status());
        verify(pagamentoService).cancelarPagamentoPendenteDoAgendamento(10L, 1L);
    }

    @Test
    void cancelarAgendamentoComEmpresaIdChamaCancelamentoDoPagamento() {
        AgendamentoEntity agendamento = agendamentoCancelavel(10L, false);
        CompanyContext.setCompanyId(1L);
        when(agendamentoRepository.findById(10L)).thenReturn(Optional.of(agendamento));
        when(agendamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = agendamentoService.cancelar(10L, 1L);

        assertEquals(StatusAgendamento.CANCELADO, response.status());
        verify(pagamentoService).cancelarPagamentoPendenteDoAgendamento(10L, 1L);
    }

    @Test
    void cancelarAgendamentoJaCanceladoEhIdempotente() {
        AgendamentoEntity agendamento = agendamentoCancelavel(10L, false);
        agendamento.setStatus(StatusAgendamento.CANCELADO);
        CompanyContext.setCompanyId(1L);
        when(agendamentoRepository.findById(10L)).thenReturn(Optional.of(agendamento));
        when(agendamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AgendamentoResponse response = agendamentoService.cancelar(10L);

        assertEquals(StatusAgendamento.CANCELADO, response.status());
        verify(pagamentoService).cancelarPagamentoPendenteDoAgendamento(10L, 1L);
    }

    @Test
    void cancelarAgendamentoDeOutraEmpresaEhBloqueado() {
        AgendamentoEntity agendamento = agendamentoCancelavel(10L, false);
        agendamento.setEmpresa(EmpresaEntity.builder().id(99L).timezone("America/Cuiaba").build());
        CompanyContext.setCompanyId(1L);
        when(agendamentoRepository.findById(10L)).thenReturn(Optional.of(agendamento));

        assertThrows(ResourceNotFoundException.class, () -> agendamentoService.cancelar(10L));
        verify(pagamentoService, never()).cancelarPagamentoPendenteDoAgendamento(any(), any());
    }

    @Test
    void atualizarParaCanceladoChamaCancelamentoDoPagamento() {
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).timezone("America/Cuiaba").build();
        ClienteEntity cliente = ClienteEntity.builder().id(1L).nome("Ana").empresa(empresa).build();
        ServicoEntity servico = ServicoEntity.builder().id(1L).nome("Consulta").duracaoMinutos(60).empresa(empresa).valor(new BigDecimal("100.00")).build();
        ProfissionalEntity profissional = ProfissionalEntity.builder().id(1L).nome("Dra. Marina").status(com.minhaempresa.gendaz.shared.enums.StatusCadastro.ATIVO).diasTrabalho(java.util.EnumSet.allOf(com.minhaempresa.gendaz.profissional.enums.DiaSemana.class)).empresa(empresa).build();
        AgendamentoEntity agendamento = AgendamentoEntity.builder()
                .id(10L).empresa(empresa).cliente(cliente).servico(servico).profissional(profissional)
                .status(StatusAgendamento.PENDENTE).build();
        CompanyContext.setCompanyId(1L);
        when(agendamentoRepository.findById(10L)).thenReturn(Optional.of(agendamento));
        when(clienteService.buscarEntidadeOperacional(1L)).thenReturn(cliente);
        when(servicoService.buscarEntidade(1L)).thenReturn(servico);
        when(profissionalService.buscarEntidade(1L)).thenReturn(profissional);
        when(empresaService.buscarEntidade(1L)).thenReturn(empresa);
        when(agendamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(agendaBlockedDayService.diaBloqueado(any(), any(), any())).thenReturn(false);
        when(sanitizacaoService.texto(any())).thenAnswer(inv -> inv.getArgument(0));

        AtualizarAgendamentoRequest request = new AtualizarAgendamentoRequest(
                1L, 1L, 1L, 1L, LocalDate.now().plusDays(1), LocalTime.of(10, 0),
                StatusAgendamento.CANCELADO, null);

        AgendamentoResponse response = agendamentoService.atualizar(10L, request);

        assertEquals(StatusAgendamento.CANCELADO, response.status());
        verify(pagamentoService).cancelarPagamentoPendenteDoAgendamento(10L, 1L);
    }

    @Test
    void atualizarParaNaoCanceladoNaoCancelaPagamento() {
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).timezone("America/Cuiaba").build();
        ClienteEntity cliente = ClienteEntity.builder().id(1L).nome("Ana").empresa(empresa).build();
        ServicoEntity servico = ServicoEntity.builder().id(1L).nome("Consulta").duracaoMinutos(60).empresa(empresa).valor(new BigDecimal("100.00")).build();
        ProfissionalEntity profissional = ProfissionalEntity.builder().id(1L).nome("Dra. Marina").status(com.minhaempresa.gendaz.shared.enums.StatusCadastro.ATIVO).diasTrabalho(java.util.EnumSet.allOf(com.minhaempresa.gendaz.profissional.enums.DiaSemana.class)).empresa(empresa).build();
        AgendamentoEntity agendamento = AgendamentoEntity.builder()
                .id(10L).empresa(empresa).cliente(cliente).servico(servico).profissional(profissional)
                .status(StatusAgendamento.CONFIRMADO).build();
        CompanyContext.setCompanyId(1L);
        when(agendamentoRepository.findById(10L)).thenReturn(Optional.of(agendamento));
        when(clienteService.buscarEntidadeOperacional(1L)).thenReturn(cliente);
        when(servicoService.buscarEntidade(1L)).thenReturn(servico);
        when(profissionalService.buscarEntidade(1L)).thenReturn(profissional);
        when(empresaService.buscarEntidade(1L)).thenReturn(empresa);
        when(agendamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(agendaBlockedDayService.diaBloqueado(any(), any(), any())).thenReturn(false);
        when(sanitizacaoService.texto(any())).thenAnswer(inv -> inv.getArgument(0));
        when(agendamentoRepository.existeConflitoDeHorario(any(), any(), any(), any(), any(), any())).thenReturn(false);

        AtualizarAgendamentoRequest request = new AtualizarAgendamentoRequest(
                1L, 1L, 1L, 1L, LocalDate.now().plusDays(1), LocalTime.of(10, 0),
                StatusAgendamento.CONFIRMADO, null);

        agendamentoService.atualizar(10L, request);

        verify(pagamentoService, never()).cancelarPagamentoPendenteDoAgendamento(any(), any());
    }
}


package com.minhaempresa.gendaz.agendamento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.CriarAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
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
 * Parte 12 — agendamento e pagamento obrigatorio nascem na mesma transacao:
 * falha na criacao do pagamento invalida a criacao (excecao propaga para
 * rollback), nunca estado parcial silencioso.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CriacaoPagamentoAtomicaTest {
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
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;
    @InjectMocks AgendamentoService agendamentoService;

    @BeforeEach
    void setup() {
        CompanyContext.setCompanyId(1L);
        org.springframework.test.util.ReflectionTestUtils.setField(agendamentoService, "appTimezone", "America/Cuiaba");
        org.springframework.test.util.ReflectionTestUtils.setField(agendamentoService, "pagamentoService", pagamentoService);
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).timezone("America/Cuiaba").build();
        ClienteEntity cliente = ClienteEntity.builder().id(1L).nome("Ana").empresa(empresa).build();
        ServicoEntity servico = ServicoEntity.builder().id(1L).nome("Corte")
                .duracaoMinutos(30).valor(new BigDecimal("100.00")).empresa(empresa).build();
        ProfissionalEntity profissional = ProfissionalEntity.builder().id(1L).nome("Jo")
                .status(com.minhaempresa.gendaz.shared.enums.StatusCadastro.ATIVO)
                .diasTrabalho(EnumSet.allOf(com.minhaempresa.gendaz.profissional.enums.DiaSemana.class))
                .empresa(empresa).build();
        when(clienteService.buscarEntidadeOperacional(1L)).thenReturn(cliente);
        when(servicoService.buscarEntidadeOperacional(1L)).thenReturn(servico);
        when(profissionalService.buscarEntidade(1L)).thenReturn(profissional);
        when(profissionalService.buscarEntidadeParaReserva(any(), any())).thenReturn(profissional);
        when(empresaService.buscarEntidade(1L)).thenReturn(empresa);
        when(agendamentoRepository.existeConflitoDeHorario(any(), any(), any(), any(), any(), any())).thenReturn(false);
        when(agendaBlockedDayService.diaBloqueado(any(), any(), any())).thenReturn(false);
        when(agendamentoRepository.save(any())).thenAnswer(inv -> {
            AgendamentoEntity ag = inv.getArgument(0);
            if (ag.getId() == null) {
                ag.setId(10L);
            }
            return ag;
        });
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    void falhaNoPagamentoInvalidaCriacaoDoAgendamento() {
        when(pagamentoRepository.save(any(PagamentoEntity.class)))
                .thenThrow(new BusinessException("Falha simulada no pagamento"));

        assertThrows(BusinessException.class, () -> agendamentoService.criar(
                new CriarAgendamentoRequest(1L, 1L, 1L, 1L,
                        LocalDate.now().plusDays(1), LocalTime.of(9, 0), null, null)));

        // Emails sao efeitos colaterais best-effort e nao podem ter disparado:
        // a criacao falhou antes de qualquer notificacao de sucesso.
        verify(resendEmailService, never()).enviarEmailNovoAgendamento(any(), any());
    }

    @Test
    void sucessoCriaPagamentoPendenteNaMesmaOperacao() {
        when(pagamentoRepository.save(any(PagamentoEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = agendamentoService.criar(
                new CriarAgendamentoRequest(1L, 1L, 1L, 1L,
                        LocalDate.now().plusDays(1), LocalTime.of(9, 0), null, null));

        verify(pagamentoRepository).save(any(PagamentoEntity.class));
        assertEquals(10L, response.id());
    }
}

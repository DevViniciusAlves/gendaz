package com.minhaempresa.gendaz.agendamento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.pagamento.service.FormaPagamentoEmpresaService;
import com.minhaempresa.gendaz.pagamento.service.PagamentoService;
import com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity;
import com.minhaempresa.gendaz.profissional.service.ProfissionalService;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import com.minhaempresa.gendaz.servico.service.ServicoService;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.shared.SanitizacaoService;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
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
 * Parte 1 — prova de propriedade no Meu Gendaz (IDOR/BOLA).
 * Cenario: Empresa 1 com Maria (id 20, dona do agendamento 123) e
 * Joao (id 10). Empresa 2 existe para o teste cross-tenant.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MeuGendazPropriedadeServiceTest {
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

    private EmpresaEntity empresa1;
    private ClienteEntity maria;
    private AgendamentoEntity agendamentoMaria;

    @BeforeEach
    void setup() {
        CompanyContext.setCompanyId(1L);
        org.springframework.test.util.ReflectionTestUtils.setField(agendamentoService, "appTimezone", "America/Cuiaba");
        org.springframework.test.util.ReflectionTestUtils.setField(agendamentoService, "pagamentoService", pagamentoService);
        empresa1 = EmpresaEntity.builder().id(1L).timezone("America/Cuiaba").build();
        maria = ClienteEntity.builder().id(20L).nome("Maria").empresa(empresa1).status(StatusCadastro.ATIVO).build();
        ServicoEntity servico = ServicoEntity.builder().id(1L).nome("Corte").duracaoMinutos(30).empresa(empresa1).build();
        ProfissionalEntity profissional = ProfissionalEntity.builder().id(1L).nome("Jo")
                .status(StatusCadastro.ATIVO)
                .diasTrabalho(EnumSet.allOf(com.minhaempresa.gendaz.profissional.enums.DiaSemana.class))
                .empresa(empresa1).build();
        agendamentoMaria = AgendamentoEntity.builder()
                .id(123L).empresa(empresa1).cliente(maria).servico(servico).profissional(profissional)
                .data(LocalDate.now().plusDays(2)).horaInicio(LocalTime.of(9, 0)).horaFim(LocalTime.of(9, 30))
                .status(StatusAgendamento.PENDENTE).build();
        // Dona (Maria, id 20, empresa 1) encontra; qualquer outro par nao encontra.
        when(agendamentoRepository.findByIdAndEmpresaIdAndClienteId(123L, 1L, 20L))
                .thenReturn(Optional.of(agendamentoMaria));
        when(agendamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(empresaService.buscarEntidade(1L)).thenReturn(empresa1);
        when(agendaBlockedDayService.diaBloqueado(any(), any(), any())).thenReturn(false);
        when(agendamentoRepository.existeConflitoDeHorario(any(), any(), any(), any(), any(), any())).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private RemarcarAgendamentoRequest remarcarParaAmanha() {
        return new RemarcarAgendamentoRequest(LocalDate.now().plusDays(3), LocalTime.of(10, 0));
    }

    @Test
    void cenarioA_donaReagendaProprioAgendamento() {
        var response = agendamentoService.remarcarParaCliente(123L, remarcarParaAmanha(), 1L, 20L);

        assertEquals(123L, response.id());
        verify(agendamentoRepository).save(any());
    }

    @Test
    void cenarioA_donaCancelaProprioAgendamento() {
        agendamentoService.cancelarParaCliente(123L, 1L, 20L);

        assertEquals(StatusAgendamento.CANCELADO, agendamentoMaria.getStatus());
        verify(pagamentoService).cancelarPagamentoPendenteDoAgendamento(123L, 1L);
    }

    @Test
    void cenarioB_joaoNaoReagendaAgendamentoDeMariaMesmaEmpresa() {
        assertThrows(ResourceNotFoundException.class,
                () -> agendamentoService.remarcarParaCliente(123L, remarcarParaAmanha(), 1L, 10L));

        verify(agendamentoRepository, never()).save(any());
    }

    @Test
    void cenarioB_joaoNaoCancelaAgendamentoDeMariaMesmaEmpresa() {
        assertThrows(ResourceNotFoundException.class,
                () -> agendamentoService.cancelarParaCliente(123L, 1L, 10L));

        verify(pagamentoService, never()).cancelarPagamentoPendenteDoAgendamento(any(), any());
        assertEquals(StatusAgendamento.PENDENTE, agendamentoMaria.getStatus());
    }

    @Test
    void cenarioC_clienteDeOutraEmpresaNaoOpera() {
        assertThrows(ResourceNotFoundException.class,
                () -> agendamentoService.remarcarParaCliente(123L, remarcarParaAmanha(), 2L, 20L));
        assertThrows(ResourceNotFoundException.class,
                () -> agendamentoService.cancelarParaCliente(123L, 2L, 20L));

        verify(agendamentoRepository, never()).save(any());
    }

    @Test
    void cenarioD_idInexistenteRetornaNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> agendamentoService.remarcarParaCliente(999L, remarcarParaAmanha(), 1L, 20L));
        assertThrows(ResourceNotFoundException.class,
                () -> agendamentoService.cancelarParaCliente(999L, 1L, 20L));
    }
}

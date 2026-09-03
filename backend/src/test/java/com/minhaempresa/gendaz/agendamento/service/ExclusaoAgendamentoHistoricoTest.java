package com.minhaempresa.gendaz.agendamento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    void excluirAgendamentoFinalizadoPreservaRegistro() {
        AgendamentoEntity ag = agendamento(11L, StatusAgendamento.FINALIZADO);
        when(agendamentoRepository.findById(11L)).thenReturn(Optional.of(ag));
        when(pagamentoRepository.findByAgendamentoIdAndEmpresaId(11L, 1L)).thenReturn(Optional.empty());

        agendamentoService.excluir(11L, 1L);

        assertEquals(StatusAgendamento.CANCELADO, ag.getStatus());
        verify(agendamentoRepository, never()).delete(any());
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
    void bulkExcluirDelegaParaExclusaoIndividual() {
        AgendamentoBulkService bulk = new AgendamentoBulkService(
                agendamentoRepository, pagamentoRepository, pagamentoService, agendamentoService, logAtividadeService);
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

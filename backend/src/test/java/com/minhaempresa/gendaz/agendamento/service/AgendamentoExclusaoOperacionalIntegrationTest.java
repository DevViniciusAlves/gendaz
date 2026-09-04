package com.minhaempresa.gendaz.agendamento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AcaoEmMassaAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity;
import com.minhaempresa.gendaz.profissional.enums.DiaSemana;
import com.minhaempresa.gendaz.profissional.repository.ProfissionalRepository;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import com.minhaempresa.gendaz.servico.repository.ServicoRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * Exclusao operacional (soft delete) + cancelamento com pagamento, de ponta
 * a ponta com Spring real (H2): Agenda some, banco/Relatorios/Financeiro
 * preservam, horario libera, Caixa nunca mexe em PAGO.
 */
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.test.context.TestPropertySource(
        properties = "spring.datasource.hikari.maximum-pool-size=8")
class AgendamentoExclusaoOperacionalIntegrationTest {

    @Autowired AgendamentoService agendamentoService;
    @Autowired AgendamentoBulkService bulkService;
    @Autowired AgendamentoRepository agendamentoRepository;
    @Autowired PagamentoRepository pagamentoRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ServicoRepository servicoRepository;
    @Autowired ProfissionalRepository profissionalRepository;

    @MockBean AssinaturaService assinaturaService;

    @BeforeEach
    void setup() {
        when(assinaturaService.isPlanoComRecursosAvancados(anyLong())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private EmpresaEntity novaEmpresa() {
        EmpresaEntity empresa = empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia("Exc Op " + System.nanoTime())
                .email("exc" + System.nanoTime() + "@x.com")
                .status(StatusEmpresa.ATIVA)
                .caixaTotal(BigDecimal.ZERO)
                .despesasTotal(BigDecimal.ZERO)
                .build());
        when(assinaturaService.isPlanoComRecursosAvancados(empresa.getId())).thenReturn(true);
        return empresa;
    }

    private ClienteEntity novoCliente(EmpresaEntity empresa) {
        return clienteRepository.save(ClienteEntity.builder()
                .nome("Cli Exc").telefone("65983" + String.format("%06d", (int) (Math.random() * 1000000)))
                .email("ce" + System.nanoTime() + "@x.com")
                .empresa(empresa).status(StatusCadastro.ATIVO).build());
    }

    private ServicoEntity novoServico(EmpresaEntity empresa) {
        return servicoRepository.save(ServicoEntity.builder()
                .nome("Corte").duracaoMinutos(30).valor(new BigDecimal("200.00"))
                .status(StatusCadastro.ATIVO).empresa(empresa).build());
    }

    private ProfissionalEntity novoProfissional(EmpresaEntity empresa) {
        return profissionalRepository.save(ProfissionalEntity.builder()
                .nome("Prof").status(StatusCadastro.ATIVO)
                .diasTrabalho(EnumSet.allOf(DiaSemana.class))
                .empresa(empresa).build());
    }

    private AgendamentoEntity novoAgendamento(EmpresaEntity empresa, ClienteEntity cliente,
            ServicoEntity servico, ProfissionalEntity profissional, StatusAgendamento status, LocalTime hora) {
        return agendamentoRepository.save(AgendamentoEntity.builder()
                .cliente(cliente).servico(servico).profissional(profissional).empresa(empresa)
                .data(LocalDate.now().plusDays(1)).horaInicio(hora).horaFim(hora.plusMinutes(30))
                .status(status).build());
    }

    private PagamentoEntity novoPagamento(EmpresaEntity empresa, ClienteEntity cliente,
            AgendamentoEntity agendamento, StatusPagamento status) {
        PagamentoEntity pagamento = PagamentoEntity.builder()
                .cliente(cliente).empresa(empresa).agendamento(agendamento)
                .valor(new BigDecimal("200.00")).metodoPagamento(MetodoPagamento.PIX)
                .status(status).build();
        if (status == StatusPagamento.PAGO) {
            pagamento.setDataPagamento(java.time.LocalDateTime.of(2026, 8, 10, 10, 0));
        }
        return pagamentoRepository.save(pagamento);
    }

    private Runnable comEmpresa(Long empresaId, Runnable tarefa) {
        return () -> {
            CompanyContext.setCompanyId(empresaId);
            try {
                tarefa.run();
            } finally {
                CompanyContext.clear();
            }
        };
    }

    @Test
    void excluirPendenteSomeDaAgendaMasPreservaBancoEHistorico() {
        EmpresaEntity empresa = novaEmpresa();
        Long empresaId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.PENDENTE, LocalTime.of(9, 0));
        novoPagamento(empresa, cliente, ag, StatusPagamento.PENDENTE);
        Long agId = ag.getId();

        CompanyContext.setCompanyId(empresaId);
        try {
            agendamentoService.excluir(agId, empresaId);
        } finally {
            CompanyContext.clear();
        }

        AgendamentoEntity apos = agendamentoRepository.findById(agId).orElseThrow();
        assertEquals(StatusAgendamento.CANCELADO, apos.getStatus());
        assertTrue(apos.isExcluidoAgenda());
        // Agenda operacional nao mostra; consulta historica mostra.
        CompanyContext.setCompanyId(empresaId);
        try {
            assertTrue(agendamentoService.listarPorEmpresa(empresaId, true).stream()
                    .noneMatch(item -> item.id().equals(agId)));
            assertTrue(agendamentoService.listarPorEmpresa(empresaId, false).stream()
                    .anyMatch(item -> item.id().equals(agId)));
            assertTrue(agendamentoService.listarPorEmpresa(empresaId).stream()
                    .anyMatch(item -> item.id().equals(agId)));
        } finally {
            CompanyContext.clear();
        }
        // Pagamento pendente cancelado, Caixa zerado.
        assertEquals(StatusPagamento.CANCELADO,
                pagamentoRepository.findByAgendamentoIdAndEmpresaId(agId, empresaId).orElseThrow().getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(
                empresaRepository.findById(empresaId).orElseThrow().getCaixaTotal()));
    }

    @Test
    void excluirComPagamentoPagoPreservaPagamentoECaixa() {
        EmpresaEntity empresa = novaEmpresa();
        Long empresaId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.CONFIRMADO, LocalTime.of(10, 0));
        novoPagamento(empresa, cliente, ag, StatusPagamento.PAGO);
        Long agId = ag.getId();

        CompanyContext.setCompanyId(empresaId);
        try {
            agendamentoService.excluir(agId, empresaId);
        } finally {
            CompanyContext.clear();
        }

        PagamentoEntity pagamento = pagamentoRepository.findByAgendamentoIdAndEmpresaId(agId, empresaId).orElseThrow();
        assertEquals(StatusPagamento.PAGO, pagamento.getStatus());
        assertEquals(MetodoPagamento.PIX, pagamento.getMetodoPagamento());
        assertEquals(java.time.LocalDateTime.of(2026, 8, 10, 10, 0), pagamento.getDataPagamento());
        assertEquals(0, new BigDecimal("200.00").compareTo(pagamento.getValor()));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                empresaRepository.findById(empresaId).orElseThrow().getCaixaTotal()));
    }

    @Test
    void excluirCanceladoEIdempotenteESemDeleteFisico() {
        EmpresaEntity empresa = novaEmpresa();
        Long empresaId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.CANCELADO, LocalTime.of(11, 0));
        Long agId = ag.getId();
        long totalAntes = agendamentoRepository.count();

        CompanyContext.setCompanyId(empresaId);
        try {
            agendamentoService.excluir(agId, empresaId);
            agendamentoService.excluir(agId, empresaId);
        } finally {
            CompanyContext.clear();
        }

        assertTrue(agendamentoRepository.findById(agId).isPresent());
        assertTrue(agendamentoRepository.findById(agId).orElseThrow().isExcluidoAgenda());
        assertEquals(totalAntes, agendamentoRepository.count());
    }

    @Test
    void excluirEmAtendimentoPausadoEFinalizadoBloqueado() {
        EmpresaEntity empresa = novaEmpresa();
        Long empresaId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);
        for (StatusAgendamento bloqueado : List.of(StatusAgendamento.EM_ATENDIMENTO,
                StatusAgendamento.PAUSADO, StatusAgendamento.FINALIZADO)) {
            AgendamentoEntity ag = novoAgendamento(empresa, cliente, servico, profissional,
                    bloqueado, LocalTime.of(12, 0));
            Long agId = ag.getId();
            CompanyContext.setCompanyId(empresaId);
            try {
                assertThrows(BusinessException.class,
                        () -> agendamentoService.excluir(agId, empresaId), "excluir de " + bloqueado);
            } finally {
                CompanyContext.clear();
            }
            assertEquals(bloqueado, agendamentoRepository.findById(agId).orElseThrow().getStatus());
            assertFalse(agendamentoRepository.findById(agId).orElseThrow().isExcluidoAgenda());
        }
    }

    @Test
    void cancelarEmAtendimentoComPagoPreservaPagamentoECaixa() {
        EmpresaEntity empresa = novaEmpresa();
        Long empresaId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.EM_ATENDIMENTO, LocalTime.of(13, 0));
        novoPagamento(empresa, cliente, ag, StatusPagamento.PAGO);
        Long agId = ag.getId();

        CompanyContext.setCompanyId(empresaId);
        try {
            agendamentoService.cancelar(agId, empresaId);
        } finally {
            CompanyContext.clear();
        }

        assertEquals(StatusAgendamento.CANCELADO,
                agendamentoRepository.findById(agId).orElseThrow().getStatus());
        PagamentoEntity pagamento = pagamentoRepository.findByAgendamentoIdAndEmpresaId(agId, empresaId).orElseThrow();
        assertEquals(StatusPagamento.PAGO, pagamento.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(
                empresaRepository.findById(empresaId).orElseThrow().getCaixaTotal()));
    }

    @Test
    void horarioLiberaAposExcluirECancelar() {
        EmpresaEntity empresa = novaEmpresa();
        Long empresaId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.CONFIRMADO, LocalTime.of(14, 0));
        Long agId = ag.getId();
        LocalDate data = ag.getData();

        assertTrue(agendamentoRepository.existeConflitoDeHorario(profissional.getId(), data,
                LocalTime.of(14, 0), LocalTime.of(14, 30), StatusAgendamento.CANCELADO, null));

        CompanyContext.setCompanyId(empresaId);
        try {
            agendamentoService.excluir(agId, empresaId);
        } finally {
            CompanyContext.clear();
        }

        assertFalse(agendamentoRepository.existeConflitoDeHorario(profissional.getId(), data,
                LocalTime.of(14, 0), LocalTime.of(14, 30), StatusAgendamento.CANCELADO, null));

        AgendamentoEntity ag2 = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.PENDENTE, LocalTime.of(15, 0));
        Long ag2Id = ag2.getId();
        CompanyContext.setCompanyId(empresaId);
        try {
            agendamentoService.cancelar(ag2Id, empresaId);
        } finally {
            CompanyContext.clear();
        }
        assertFalse(agendamentoRepository.existeConflitoDeHorario(profissional.getId(), data,
                LocalTime.of(15, 0), LocalTime.of(15, 30), StatusAgendamento.CANCELADO, null));
    }

    @Test
    void bulkExcluirUsaSoftDeleteEBulkCancelarUsaRegraCentral() {
        EmpresaEntity empresa = novaEmpresa();
        Long empresaId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.EM_ATENDIMENTO, LocalTime.of(16, 0));
        novoPagamento(empresa, cliente, ag, StatusPagamento.PENDENTE);
        Long agId = ag.getId();

        CompanyContext.setCompanyId(empresaId);
        var respCancelar = bulkService.executar(
                new AcaoEmMassaAgendamentoRequest(List.of(agId), "CANCELAR", empresaId));
        CompanyContext.clear();

        assertEquals(1, respCancelar.totalProcessado());
        assertEquals(StatusAgendamento.CANCELADO,
                agendamentoRepository.findById(agId).orElseThrow().getStatus());

        CompanyContext.setCompanyId(empresaId);
        var respExcluir = bulkService.executar(
                new AcaoEmMassaAgendamentoRequest(List.of(agId), "EXCLUIR", empresaId));
        CompanyContext.clear();

        assertEquals(1, respExcluir.totalProcessado());
        AgendamentoEntity apos = agendamentoRepository.findById(agId).orElseThrow();
        assertEquals(StatusAgendamento.CANCELADO, apos.getStatus());
        assertTrue(apos.isExcluidoAgenda());
        assertEquals(StatusPagamento.CANCELADO,
                pagamentoRepository.findByAgendamentoIdAndEmpresaId(agId, empresaId).orElseThrow().getStatus());
    }

    @Test
    void cancelarXFinalizarConcorrentesResultaEmEstadoSerializavel() throws Exception {
        EmpresaEntity empresa = novaEmpresa();
        Long empresaId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.EM_ATENDIMENTO, LocalTime.of(17, 0));
        novoPagamento(empresa, cliente, ag, StatusPagamento.PENDENTE);
        Long agId = ag.getId();

        AtomicInteger sucessos = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> erros = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch inicio = new CountDownLatch(1);
            Future<?> f1 = executor.submit(comEmpresa(empresaId, () -> {
                try {
                    inicio.await();
                    agendamentoService.cancelar(agId, empresaId);
                    sucessos.incrementAndGet();
                } catch (Throwable t) {
                    erros.add(t);
                }
            }));
            Future<?> f2 = executor.submit(comEmpresa(empresaId, () -> {
                try {
                    inicio.await();
                    agendamentoService.finalizar(agId, true, MetodoPagamento.PIX, null);
                    sucessos.incrementAndGet();
                } catch (Throwable t) {
                    erros.add(t);
                }
            }));
            inicio.countDown();
            f1.get(60, TimeUnit.SECONDS);
            f2.get(60, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        StatusAgendamento statusFinal =
                agendamentoRepository.findById(agId).orElseThrow().getStatus();
        BigDecimal caixaFinal = empresaRepository.findById(empresaId).orElseThrow().getCaixaTotal();
        StatusPagamento pagFinal =
                pagamentoRepository.findByAgendamentoIdAndEmpresaId(agId, empresaId).orElseThrow().getStatus();
        if (statusFinal == StatusAgendamento.FINALIZADO) {
            assertEquals(StatusPagamento.PAGO, pagFinal);
            assertEquals(0, new BigDecimal("200.00").compareTo(caixaFinal));
        } else if (statusFinal == StatusAgendamento.CANCELADO) {
            assertEquals(StatusPagamento.CANCELADO, pagFinal);
            assertEquals(0, BigDecimal.ZERO.compareTo(caixaFinal));
        } else {
            throw new AssertionError("Estado final invalido: " + statusFinal);
        }
    }
}

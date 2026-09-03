package com.minhaempresa.gendaz.agendamento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AtualizarAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.RemarcarAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.financeiro.caixadespesas.enums.TipoCaixaDespesasLog;
import com.minhaempresa.gendaz.financeiro.caixadespesas.repository.CaixaDespesasLogRepository;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity;
import com.minhaempresa.gendaz.profissional.enums.DiaSemana;
import com.minhaempresa.gendaz.profissional.repository.ProfissionalRepository;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import com.minhaempresa.gendaz.servico.repository.ServicoRepository;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Concorrencia do PROPRIO agendamento: dois writers simultaneos do mesmo
 * agendamento precisam se comportar como alguma execucao SEQUENCIAL valida da
 * maquina de estados. Todos os writers carregam o agendamento com
 * PESSIMISTIC_WRITE e so leem o status depois do lock.
 *
 * <p>Testes com Spring real, threads e transacoes independentes, sem
 * {@code @Transactional} nos metodos de teste.
 *
 * <p>Banco: H2 (Docker/Testcontainers indisponivel neste ambiente — daemon
 * Docker nao esta em execucao — entao o bloqueio fisico
 * {@code SELECT ... FOR UPDATE} e exercitado na semantica do H2, nao na do
 * PostgreSQL de producao. O teste deterministico de bloqueio abaixo prova que
 * o metodo {@code ForUpdate} realmente adquire lock; a prova completa do
 * comportamento sob PostgreSQL deve rodar com Testcontainers em CI com
 * Docker).
 */
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.test.context.TestPropertySource(
        properties = "spring.datasource.hikari.maximum-pool-size=8")
class AgendamentoConcorrenciaIntegrationTest {

    @Autowired AgendamentoService agendamentoService;
    @Autowired AgendamentoRepository agendamentoRepository;
    @Autowired PagamentoRepository pagamentoRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ServicoRepository servicoRepository;
    @Autowired ProfissionalRepository profissionalRepository;
    @Autowired CaixaDespesasLogRepository logRepository;
    @Autowired TransactionTemplate transactionTemplate;

    @MockBean AssinaturaService assinaturaService;

    @BeforeEach
    void setup() {
        when(assinaturaService.isPlanoComRecursosAvancados(anyLong())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    // ---------- infra ----------

    private EmpresaEntity novaEmpresa() {
        EmpresaEntity empresa = empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia("Conc Ag " + System.nanoTime())
                .email("cag" + System.nanoTime() + "@x.com")
                .status(StatusEmpresa.ATIVA)
                .caixaTotal(BigDecimal.ZERO)
                .despesasTotal(BigDecimal.ZERO)
                .build());
        when(assinaturaService.isPlanoComRecursosAvancados(empresa.getId())).thenReturn(true);
        return empresa;
    }

    private ClienteEntity novoCliente(EmpresaEntity empresa) {
        return clienteRepository.save(ClienteEntity.builder()
                .nome("Cli Conc").telefone("65982" + String.format("%06d", (int) (Math.random() * 1000000)))
                .email("cc" + System.nanoTime() + "@x.com")
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

    private LocalDate proximoDiaUtil() {
        LocalDate data = LocalDate.now().plusDays(1);
        while (data.getDayOfWeek() == DayOfWeek.SATURDAY || data.getDayOfWeek() == DayOfWeek.SUNDAY) {
            data = data.plusDays(1);
        }
        return data;
    }

    private AgendamentoEntity novoAgendamento(EmpresaEntity empresa, ClienteEntity cliente,
            ServicoEntity servico, ProfissionalEntity profissional, StatusAgendamento status, LocalTime hora) {
        return agendamentoRepository.save(AgendamentoEntity.builder()
                .cliente(cliente).servico(servico).profissional(profissional).empresa(empresa)
                .data(proximoDiaUtil()).horaInicio(hora).horaFim(hora.plusMinutes(30))
                .status(status).build());
    }

    private PagamentoEntity novoPagamento(EmpresaEntity empresa, ClienteEntity cliente,
            AgendamentoEntity agendamento, StatusPagamento status) {
        return pagamentoRepository.save(PagamentoEntity.builder()
                .cliente(cliente).empresa(empresa).agendamento(agendamento)
                .valor(new BigDecimal("200.00")).metodoPagamento(MetodoPagamento.OUTRO)
                .status(status).build());
    }

    private StatusAgendamento statusDe(Long agendamentoId) {
        return agendamentoRepository.findById(agendamentoId).orElseThrow().getStatus();
    }

    private StatusPagamento pagamentoDoAgendamento(Long agendamentoId, Long empresaId) {
        return pagamentoRepository.findByAgendamentoIdAndEmpresaId(agendamentoId, empresaId)
                .orElseThrow().getStatus();
    }

    private long contarAprovados(Long empresaId) {
        return logRepository.findAll().stream()
                .filter(l -> l.getTipo() == TipoCaixaDespesasLog.PAGAMENTO_APROVADO
                        && l.getBusiness() != null && empresaId.equals(l.getBusiness().getId()))
                .count();
    }

    private BigDecimal caixaDe(Long empresaId) {
        return empresaRepository.findById(empresaId).orElseThrow().getCaixaTotal();
    }

    /** Roda tarefas com inicio simultaneo (latch); cada uma gerencia a propria transacao. */
    private Duplex executarConcorrente(Runnable tarefaA, Runnable tarefaB) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch inicio = new CountDownLatch(1);
            AtomicInteger sucessosA = new AtomicInteger();
            AtomicInteger sucessosB = new AtomicInteger();
            ConcurrentLinkedQueue<Throwable> erros = new ConcurrentLinkedQueue<>();
            Future<?> fa = executor.submit(() -> {
                try {
                    inicio.await();
                    tarefaA.run();
                    sucessosA.incrementAndGet();
                } catch (Throwable t) {
                    erros.add(t);
                }
            });
            Future<?> fb = executor.submit(() -> {
                try {
                    inicio.await();
                    tarefaB.run();
                    sucessosB.incrementAndGet();
                } catch (Throwable t) {
                    erros.add(t);
                }
            });
            inicio.countDown();
            fa.get(60, TimeUnit.SECONDS);
            fb.get(60, TimeUnit.SECONDS);
            return new Duplex(sucessosA.get(), sucessosB.get(), erros);
        } finally {
            executor.shutdownNow();
        }
    }

    private record Duplex(int sucessosA, int sucessosB, ConcurrentLinkedQueue<Throwable> erros) {}

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

    // ---------- TESTE 1: iniciar x cancelar (CONFIRMADO) ----------

    @Test
    void iniciarXCancelarResultaEmAlgumaOrdemSerialValida() throws Exception {
        EmpresaEntity empresa = novaEmpresa();
        Long empresaId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.CONFIRMADO, LocalTime.of(9, 0));
        novoPagamento(empresa, cliente, ag, StatusPagamento.PENDENTE);
        Long agId = ag.getId();

        // Overload de producao (o mesmo invocado pelo AgendamentoController):
        // cancelar(id, empresaId). O overload single-arg possui uma checagem
        // de conflito pre-existente que casa com o proprio registro em
        // CONFIRMADO e nao representa o fluxo real de UI.
        Duplex resultado = executarConcorrente(
                comEmpresa(empresaId, () -> agendamentoService.iniciar(agId)),
                comEmpresa(empresaId, () -> agendamentoService.cancelar(agId, empresaId)));

        // Exatamente um vence; o outro e rejeitado pela maquina sobre o estado protegido.
        assertEquals(1, resultado.sucessosA() + resultado.sucessosB());

        StatusAgendamento fim = statusDe(agId);
        StatusPagamento pagFim = pagamentoDoAgendamento(agId, empresaId);
        if (fim == StatusAgendamento.EM_ATENDIMENTO) {
            // iniciar venceu: o cancelar posterior leu EM_ATENDIMENTO e foi bloqueado.
            assertEquals(StatusPagamento.PENDENTE, pagFim);
        } else {
            // cancelar venceu: o iniciar posterior leu CANCELADO e foi bloqueado.
            assertEquals(StatusAgendamento.CANCELADO, fim);
            assertEquals(StatusPagamento.CANCELADO, pagFim);
        }
    }

    // ---------- TESTE 2: finalizar x pausar (EM_ATENDIMENTO) ----------

    @Test
    void finalizarXPausarNuncaGeraHibridoPausadoPagoComCaixa() throws Exception {
        EmpresaEntity empresa = novaEmpresa();
        Long empresaId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.EM_ATENDIMENTO, LocalTime.of(9, 0));
        novoPagamento(empresa, cliente, ag, StatusPagamento.PENDENTE);
        Long agId = ag.getId();

        executarConcorrente(
                comEmpresa(empresaId, () -> agendamentoService.finalizar(agId, true, MetodoPagamento.DINHEIRO, null)),
                comEmpresa(empresaId, () -> agendamentoService.pausar(agId)));

        // Qualquer ordem serial valida termina em FINALIZADO+PAGO+Caixa 1x.
        // O hibrido impossivel (PAUSADO + PAGO + Caixa) indicaria stale overwrite.
        assertEquals(StatusAgendamento.FINALIZADO, statusDe(agId));
        assertEquals(StatusPagamento.PAGO, pagamentoDoAgendamento(agId, empresaId));
        assertEquals(0, new BigDecimal("200.00").compareTo(caixaDe(empresaId)));
        assertEquals(1, contarAprovados(empresaId));
    }

    // ---------- TESTE 3: remarcar x cancelar (CONFIRMADO) ----------

    @Test
    void remarcarXCancelarNuncaRessuscitaCancelado() throws Exception {
        EmpresaEntity empresa = novaEmpresa();
        Long empresaId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.CONFIRMADO, LocalTime.of(9, 0));
        novoPagamento(empresa, cliente, ag, StatusPagamento.PENDENTE);
        Long agId = ag.getId();
        RemarcarAgendamentoRequest remarcar = new RemarcarAgendamentoRequest(proximoDiaUtil(), LocalTime.of(10, 0));

        Duplex resultado = executarConcorrente(
                comEmpresa(empresaId, () -> agendamentoService.remarcar(agId, remarcar)),
                comEmpresa(empresaId, () -> agendamentoService.cancelar(agId, empresaId)));

        // O cancelar sempre completa (vale para PENDENTE e CONFIRMADO); o
        // remarcar posterior a um cancelamento le CANCELADO e falha — nunca
        // ressuscita para PENDENTE com nova data (stale write).
        assertEquals(1, resultado.sucessosB(), "cancelar deve completar em qualquer ordem. erros=" + resultado.erros());
        assertEquals(StatusAgendamento.CANCELADO, statusDe(agId));
    }

    // ---------- TESTE 4: atualizar x finalizar (EM_ATENDIMENTO) ----------

    @Test
    void atualizarXFinalizarNaoReverteFinalizadoComCaixa() throws Exception {
        EmpresaEntity empresa = novaEmpresa();
        Long empresaId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.EM_ATENDIMENTO, LocalTime.of(9, 0));
        novoPagamento(empresa, cliente, ag, StatusPagamento.PENDENTE);
        Long agId = ag.getId();
        AtualizarAgendamentoRequest edicao = new AtualizarAgendamentoRequest(
                cliente.getId(), servico.getId(), profissional.getId(), empresaId,
                ag.getData(), ag.getHoraInicio(), StatusAgendamento.EM_ATENDIMENTO, "edicao concorrente");

        executarConcorrente(
                comEmpresa(empresaId, () -> agendamentoService.finalizar(agId, true, MetodoPagamento.DINHEIRO, null)),
                comEmpresa(empresaId, () -> agendamentoService.atualizar(agId, edicao)));

        // Sem lock no update, o save stale poderia reverter FINALIZADO para
        // EM_ATENDIMENTO mantendo PAGO + Caixa (lost update). Com lock, o
        // update ou commita antes (e o finalizar prossegue) ou falha depois.
        assertEquals(StatusAgendamento.FINALIZADO, statusDe(agId));
        assertEquals(StatusPagamento.PAGO, pagamentoDoAgendamento(agId, empresaId));
        assertEquals(0, new BigDecimal("200.00").compareTo(caixaDe(empresaId)));
        assertEquals(1, contarAprovados(empresaId));
    }

    // ---------- TESTE 5: reabrir x acao incompativel (FINALIZADO) ----------

    @Test
    void reabrirComAcaoIncompativelDecideSobreEstadoProtegido() throws Exception {
        EmpresaEntity empresa = novaEmpresa();
        Long empresaId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.FINALIZADO, LocalTime.of(9, 0));
        novoPagamento(empresa, cliente, ag, StatusPagamento.PAGO);
        Long agId = ag.getId();

        Duplex resultado = executarConcorrente(
                comEmpresa(empresaId, () -> agendamentoService.reabrir(agId)),
                comEmpresa(empresaId, () -> agendamentoService.iniciar(agId)));

        // iniciar nunca se aplica a FINALIZADO; reabrir sempre completa no fim.
        assertEquals(1, resultado.sucessosA(), "reabrir deve completar. erros=" + resultado.erros());
        assertEquals(0, resultado.sucessosB());
        assertEquals(StatusAgendamento.EM_ATENDIMENTO, statusDe(agId));
        // Reabrir nao toca em pagamento/Caixa.
        assertEquals(StatusPagamento.PAGO, pagamentoDoAgendamento(agId, empresaId));
        assertEquals(0, BigDecimal.ZERO.compareTo(caixaDe(empresaId)));
    }

    // ---------- TESTE 6: dupla acao igual ----------

    @Test
    void duploIniciarSomenteUmExecutaTransicaoReal() throws Exception {
        EmpresaEntity empresa = novaEmpresa();
        Long empresaId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.CONFIRMADO, LocalTime.of(9, 0));
        novoPagamento(empresa, cliente, ag, StatusPagamento.PENDENTE);
        Long agId = ag.getId();

        Duplex resultado = executarConcorrente(
                comEmpresa(empresaId, () -> agendamentoService.iniciar(agId)),
                comEmpresa(empresaId, () -> agendamentoService.iniciar(agId)));

        assertEquals(1, resultado.sucessosA() + resultado.sucessosB(),
                "segundo iniciar deve ler EM_ATENDIMENTO pos-lock e ser rejeitado. erros=" + resultado.erros());
        assertEquals(StatusAgendamento.EM_ATENDIMENTO, statusDe(agId));
    }

    // ---------- TESTE 7 (deterministico): lock bloqueia ate o commit ----------

    @Test
    void lockPessimistaBloqueiaSegundaTransacaoAteCommit() throws Exception {
        EmpresaEntity empresa = novaEmpresa();
        Long empresaId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.CONFIRMADO, LocalTime.of(9, 0));
        Long agId = ag.getId();

        CountDownLatch lockAdquirido = new CountDownLatch(1);
        CountDownLatch podeCommittar = new CountDownLatch(1);
        List<String> ordem = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> transacaoA = executor.submit(() -> transactionTemplate.execute(status -> {
                agendamentoRepository.findByIdAndEmpresaIdForUpdate(agId, empresaId).orElseThrow();
                ordem.add("A-lock");
                lockAdquirido.countDown();
                try {
                    if (!podeCommittar.await(15, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timeout aguardando liberacao do commit");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                ordem.add("A-commit");
                return null;
            }));
            assertTrue(lockAdquirido.await(15, TimeUnit.SECONDS), "A deveria adquirir o lock");

            Future<?> transacaoB = executor.submit(() -> transactionTemplate.execute(status -> {
                agendamentoRepository.findByIdAndEmpresaIdForUpdate(agId, empresaId).orElseThrow();
                ordem.add("B-lock");
                return null;
            }));

            // Janela de observacao (nao e o mecanismo de sincronizacao — os
            // latches acima sao): B deve continuar bloqueada enquanto A detem o lock.
            Thread.sleep(500);
            assertFalse(transacaoB.isDone(),
                    "B nao poderia adquirir o lock enquanto A nao commitou (lock nao efetivo?)");

            podeCommittar.countDown();
            transacaoB.get(15, TimeUnit.SECONDS);
            transacaoA.get(15, TimeUnit.SECONDS);
            assertEquals(List.of("A-lock", "A-commit", "B-lock"), ordem);
        } finally {
            executor.shutdownNow();
        }
    }
}

package com.minhaempresa.gendaz.financeiro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

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
import com.minhaempresa.gendaz.financeiro.caixadespesas.service.CaixaDespesasService;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.AtualizarStatusPagamentoRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.MarcarPagamentoPagoRequest;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.pagamento.service.PagamentoService;
import com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity;
import com.minhaempresa.gendaz.profissional.repository.ProfissionalRepository;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import com.minhaempresa.gendaz.servico.repository.ServicoRepository;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * Concorrencia financeira — Problema 1 (cancelamento x confirmacao) e
 * Problema 2 (caixa manual x pagamento), mais ordem de locks.
 *
 * Todos os testes usam 2+ threads com latch de inicio simultaneo e
 * transacoes independentes, sobre H2 com pool &gt; 1 para exercitar de
 * verdade os locks pessimistas. Nenhum teste sequencial e considerado
 * prova de concorrencia aqui.
 *
 * Ordem oficial de locks: PAGAMENTO -&gt; EMPRESA. Os fluxos manuais usam
 * apenas EMPRESA. Nenhum caminho faz EMPRESA -&gt; PAGAMENTO.
 */
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.test.context.TestPropertySource(
        properties = "spring.datasource.hikari.maximum-pool-size=8")
class ConcorrenciaFinanceiraIntegrationTest {

    @Autowired PagamentoService pagamentoService;
    @Autowired CaixaDespesasService caixaDespesasService;
    @Autowired PagamentoRepository pagamentoRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ServicoRepository servicoRepository;
    @Autowired ProfissionalRepository profissionalRepository;
    @Autowired AgendamentoRepository agendamentoRepository;
    @Autowired CaixaDespesasLogRepository logRepository;

    @MockBean AssinaturaService assinaturaService;

    // ---------- infra ----------

    private EmpresaEntity novaEmpresa(BigDecimal caixa, BigDecimal despesas) {
        when(assinaturaService.isPlanoComRecursosAvancados(anyLong())).thenReturn(true);
        EmpresaEntity empresa = empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia("Empresa Conc " + System.nanoTime())
                .email("conc" + System.nanoTime() + "@x.com")
                .status(StatusEmpresa.ATIVA)
                .caixaTotal(caixa)
                .despesasTotal(despesas)
                .build());
        when(assinaturaService.isPlanoComRecursosAvancados(empresa.getId())).thenReturn(true);
        return empresa;
    }

    private ClienteEntity novoCliente(EmpresaEntity empresa) {
        return clienteRepository.save(ClienteEntity.builder()
                .nome("Cli Conc").telefone("6599000" + String.format("%04d", (int) (Math.random() * 10000)))
                .email("c" + System.nanoTime() + "@x.com")
                .empresa(empresa).status(StatusCadastro.ATIVO).build());
    }

    private PagamentoEntity novoPagamento(EmpresaEntity empresa, ClienteEntity cliente,
            AgendamentoEntity agendamento, String valor) {
        return pagamentoRepository.save(PagamentoEntity.builder()
                .cliente(cliente).empresa(empresa).agendamento(agendamento)
                .valor(new BigDecimal(valor)).metodoPagamento(MetodoPagamento.OUTRO)
                .status(StatusPagamento.PENDENTE).build());
    }

    private AgendamentoEntity novoAgendamento(EmpresaEntity empresa, ClienteEntity cliente) {
        ServicoEntity servico = servicoRepository.save(ServicoEntity.builder()
                .nome("Corte").duracaoMinutos(30).valor(new BigDecimal("200.00"))
                .status(StatusCadastro.ATIVO).empresa(empresa).build());
        ProfissionalEntity profissional = profissionalRepository.save(ProfissionalEntity.builder()
                .nome("Prof").status(StatusCadastro.ATIVO).empresa(empresa).build());
        return agendamentoRepository.save(AgendamentoEntity.builder()
                .cliente(cliente).servico(servico).profissional(profissional).empresa(empresa)
                .data(LocalDate.now().plusDays(1)).horaInicio(LocalTime.of(9, 0)).horaFim(LocalTime.of(9, 30))
                .status(StatusAgendamento.PENDENTE).build());
    }

    private long contarLogs(Long empresaId, TipoCaixaDespesasLog tipo) {
        return logRepository.findAll().stream()
                .filter(l -> l.getTipo() == tipo
                        && l.getBusiness() != null && empresaId.equals(l.getBusiness().getId()))
                .count();
    }

    private BigDecimal caixaDe(Long empresaId) {
        return empresaRepository.findById(empresaId).orElseThrow().getCaixaTotal();
    }

    private BigDecimal despesasDe(Long empresaId) {
        return empresaRepository.findById(empresaId).orElseThrow().getDespesasTotal();
    }

    /** Executa tarefas em paralelo com inicio simultaneo; falha se estourar o timeout. */
    private void executarConcorrente(List<Runnable> tarefas) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tarefas.size());
        try {
            CountDownLatch inicio = new CountDownLatch(1);
            ConcurrentLinkedQueue<Throwable> erros = new ConcurrentLinkedQueue<>();
            List<Future<?>> futures = new ArrayList<>();
            for (Runnable tarefa : tarefas) {
                futures.add(executor.submit(() -> {
                    try {
                        inicio.await();
                        tarefa.run();
                    } catch (Throwable t) {
                        erros.add(t);
                        throw new RuntimeException(t);
                    }
                }));
            }
            inicio.countDown();
            for (Future<?> f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }
            assertTrue(erros.isEmpty(), "nenhuma thread deveria falhar: " + erros);
        } finally {
            executor.shutdownNow();
        }
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

    // ---------- TESTE 1: cancelamento x confirmacao ----------

    @Test
    void cancelamentoXConfirmacaoNuncaGeraHibrido() throws Exception {
        EmpresaEntity empresa = novaEmpresa(BigDecimal.ZERO, BigDecimal.ZERO);
        ClienteEntity cliente = novoCliente(empresa);
        AgendamentoEntity agendamento = novoAgendamento(empresa, cliente);
        PagamentoEntity pagamento = novoPagamento(empresa, cliente, agendamento, "200.00");
        Long pagamentoId = pagamento.getId();
        Long agendamentoId = agendamento.getId();
        Long empresaId = empresa.getId();
        BigDecimal caixaInicial = caixaDe(empresaId);

        executarConcorrente(List.of(
                // Thread A: cancelamento do agendamento/pagamento (lock PAGAMENTO).
                () -> pagamentoService.cancelarPagamentoPendenteDoAgendamento(agendamentoId, empresaId),
                // Thread B: confirmacao do mesmo pagamento (lock PAGAMENTO -> EMPRESA).
                comEmpresa(empresaId, () -> pagamentoService.marcarPago(pagamentoId,
                        new MarcarPagamentoPagoRequest(MetodoPagamento.PIX, null)))));

        PagamentoEntity fim = pagamentoRepository.findById(pagamentoId).orElseThrow();
        BigDecimal caixaFim = caixaDe(empresaId);
        long aprovados = contarLogs(empresaId, TipoCaixaDespesasLog.PAGAMENTO_APROVADO);

        if (fim.getStatus() == StatusPagamento.CANCELADO) {
            assertEquals(0, caixaInicial.compareTo(caixaFim),
                    "CANCELADO nao pode ter recebido o dinheiro da corrida");
            assertEquals(0, aprovados, "CANCELADO nao pode ter lancamento PAGAMENTO_APROVADO");
        } else {
            assertEquals(StatusPagamento.PAGO, fim.getStatus());
            assertEquals(0, new BigDecimal("200.00").compareTo(caixaFim.subtract(caixaInicial)),
                    "PAGO deve ter exatamente +R$200 uma vez");
            assertEquals(1, aprovados, "PAGO deve ter exatamente 1 movimentacao");
        }
    }

    // ---------- TESTE 2: confirmacao x confirmacao (via caminhos distintos) ----------

    @Test
    void duasConfirmacoesPorCaminhosDistintosGeramUmaUnicaEntrada() throws Exception {
        EmpresaEntity empresa = novaEmpresa(BigDecimal.ZERO, BigDecimal.ZERO);
        ClienteEntity cliente = novoCliente(empresa);
        PagamentoEntity pagamento = novoPagamento(empresa, cliente, null, "200.00");
        Long pagamentoId = pagamento.getId();
        Long empresaId = empresa.getId();

        executarConcorrente(List.of(
                comEmpresa(empresaId, () -> pagamentoService.marcarPago(pagamentoId,
                        new MarcarPagamentoPagoRequest(MetodoPagamento.PIX, null))),
                comEmpresa(empresaId, () -> pagamentoService.atualizarStatus(pagamentoId,
                        new AtualizarStatusPagamentoRequest(StatusPagamento.PAGO)))));

        assertEquals(StatusPagamento.PAGO, pagamentoRepository.findById(pagamentoId).orElseThrow().getStatus());
        assertEquals(0, new BigDecimal("200.00").compareTo(caixaDe(empresaId)),
                "caixa deve conter UMA unica entrada de R$200");
        assertEquals(1, contarLogs(empresaId, TipoCaixaDespesasLog.PAGAMENTO_APROVADO));
    }

    // ---------- TESTE 3: pagamento x adicao manual de caixa ----------

    @Test
    void pagamentoXAdicaoManualDeCaixaSemLostUpdate() throws Exception {
        EmpresaEntity empresa = novaEmpresa(new BigDecimal("1000.00"), BigDecimal.ZERO);
        ClienteEntity cliente = novoCliente(empresa);
        PagamentoEntity pagamento = novoPagamento(empresa, cliente, null, "200.00");
        Long pagamentoId = pagamento.getId();
        Long empresaId = empresa.getId();

        executarConcorrente(List.of(
                comEmpresa(empresaId, () -> pagamentoService.marcarPago(pagamentoId,
                        new MarcarPagamentoPagoRequest(MetodoPagamento.PIX, null))),
                () -> caixaDespesasService.adicionarCaixaManual(empresaId, new BigDecimal("100.00"), "obs", null)));

        assertEquals(0, new BigDecimal("1300.00").compareTo(caixaDe(empresaId)),
                "1000 + 200 pagamento + 100 manual = 1300, independente da ordem do lock");
        assertEquals(1, contarLogs(empresaId, TipoCaixaDespesasLog.PAGAMENTO_APROVADO));
        assertEquals(1, contarLogs(empresaId, TipoCaixaDespesasLog.ADICAO_MANUAL_CAIXA));
    }

    // ---------- TESTE 4: pagamento x remocao manual ----------

    @Test
    void pagamentoXRemocaoManualDeCaixaSemLostUpdate() throws Exception {
        EmpresaEntity empresa = novaEmpresa(new BigDecimal("1000.00"), BigDecimal.ZERO);
        ClienteEntity cliente = novoCliente(empresa);
        PagamentoEntity pagamento = novoPagamento(empresa, cliente, null, "200.00");
        Long pagamentoId = pagamento.getId();
        Long empresaId = empresa.getId();

        executarConcorrente(List.of(
                comEmpresa(empresaId, () -> pagamentoService.marcarPago(pagamentoId,
                        new MarcarPagamentoPagoRequest(MetodoPagamento.PIX, null))),
                () -> caixaDespesasService.removerValorCaixaManual(empresaId, new BigDecimal("100.00"), "obs", null)));

        assertEquals(0, new BigDecimal("1100.00").compareTo(caixaDe(empresaId)),
                "1000 + 200 pagamento - 100 manual = 1100");
    }

    // ---------- TESTE 5: duas operacoes manuais de caixa ----------

    @Test
    void duasAdicoesManuaisDeCaixaSemLostUpdate() throws Exception {
        EmpresaEntity empresa = novaEmpresa(new BigDecimal("1000.00"), BigDecimal.ZERO);
        Long empresaId = empresa.getId();

        executarConcorrente(List.of(
                () -> caixaDespesasService.adicionarCaixaManual(empresaId, new BigDecimal("100.00"), "a", null),
                () -> caixaDespesasService.adicionarCaixaManual(empresaId, new BigDecimal("50.00"), "b", null)));

        assertEquals(0, new BigDecimal("1150.00").compareTo(caixaDe(empresaId)));
        assertEquals(2, contarLogs(empresaId, TipoCaixaDespesasLog.ADICAO_MANUAL_CAIXA));
    }

    // ---------- TESTE 6: despesas concorrentes ----------

    @Test
    void duasAdicoesManuaisDeDespesasSemLostUpdate() throws Exception {
        EmpresaEntity empresa = novaEmpresa(BigDecimal.ZERO, new BigDecimal("500.00"));
        Long empresaId = empresa.getId();

        executarConcorrente(List.of(
                () -> caixaDespesasService.adicionarDespesasManual(empresaId, new BigDecimal("100.00"), "a", null),
                () -> caixaDespesasService.adicionarDespesasManual(empresaId, new BigDecimal("50.00"), "b", null)));

        assertEquals(0, new BigDecimal("650.00").compareTo(despesasDe(empresaId)),
                "500 + 100 + 50 = 650");
        assertEquals(2, contarLogs(empresaId, TipoCaixaDespesasLog.ADICAO_MANUAL_DESPESAS));
    }

    // ---------- TESTE 7: ordenacao de locks (misto, anti-deadlock) ----------

    @Test
    void cargaMistaPagamentoMaisManualTerminaSemDeadlockESemLostUpdate() throws Exception {
        EmpresaEntity empresa = novaEmpresa(new BigDecimal("1000.00"), BigDecimal.ZERO);
        ClienteEntity cliente = novoCliente(empresa);
        PagamentoEntity p1 = novoPagamento(empresa, cliente, null, "200.00");
        PagamentoEntity p2 = novoPagamento(empresa, cliente, null, "200.00");
        Long empresaId = empresa.getId();

        // Fluxos Pagamento->Empresa misturados com fluxos so-Empresa.
        // Ordem inconsistente travaria aqui (deadlock/timeout) em vez de passar.
        executarConcorrente(List.of(
                comEmpresa(empresaId, () -> pagamentoService.marcarPago(p1.getId(),
                        new MarcarPagamentoPagoRequest(MetodoPagamento.PIX, null))),
                comEmpresa(empresaId, () -> pagamentoService.marcarPago(p2.getId(),
                        new MarcarPagamentoPagoRequest(MetodoPagamento.PIX, null))),
                () -> caixaDespesasService.adicionarCaixaManual(empresaId, new BigDecimal("100.00"), "a", null),
                () -> caixaDespesasService.adicionarCaixaManual(empresaId, new BigDecimal("50.00"), "b", null)));

        assertEquals(0, new BigDecimal("1550.00").compareTo(caixaDe(empresaId)),
                "1000 + 200 + 200 + 100 + 50 = 1550");
        assertEquals(2, contarLogs(empresaId, TipoCaixaDespesasLog.PAGAMENTO_APROVADO));
        assertEquals(2, contarLogs(empresaId, TipoCaixaDespesasLog.ADICAO_MANUAL_CAIXA));
    }

    // ---------- TESTE 8: decisao usa estado lido DEPOIS do lock ----------

    @Test
    void decisaoPosteriorAoLockEnxergaEstadoAtualizado() {
        EmpresaEntity empresa = novaEmpresa(BigDecimal.ZERO, BigDecimal.ZERO);
        ClienteEntity cliente = novoCliente(empresa);
        Long empresaId = empresa.getId();

        // Pagamento 1: confirma, commita PAGO; cancelamento posterior deve virar no-op.
        PagamentoEntity p1 = novoPagamento(empresa, cliente, null, "200.00");
        CompanyContext.setCompanyId(empresaId);
        try {
            pagamentoService.marcarPago(p1.getId(), new MarcarPagamentoPagoRequest(MetodoPagamento.PIX, null));
        } finally {
            CompanyContext.clear();
        }
        AgendamentoEntity ag1 = novoAgendamento(empresa, cliente);
        // Re-carrega: a instancia p1 em memoria esta stale (PENDENTE); o estado
        // commitado e PAGO e deve ser ele a receber o vinculo com o agendamento.
        p1 = pagamentoRepository.findById(p1.getId()).orElseThrow();
        p1.setAgendamento(ag1);
        pagamentoRepository.save(p1);

        pagamentoService.cancelarPagamentoPendenteDoAgendamento(ag1.getId(), empresaId);

        assertEquals(StatusPagamento.PAGO, pagamentoRepository.findById(p1.getId()).orElseThrow().getStatus(),
                "cancelamento apos PAGO commitado deve preservar PAGO (leu estado novo, nao o antigo PENDENTE)");
        assertEquals(0, new BigDecimal("200.00").compareTo(caixaDe(empresaId)));

        // Pagamento 2: cancela, commita CANCELADO; segundo cancelamento e idempotente.
        AgendamentoEntity ag2 = novoAgendamento(empresa, cliente);
        PagamentoEntity p2 = novoPagamento(empresa, cliente, ag2, "200.00");
        pagamentoService.cancelarPagamentoPendenteDoAgendamento(ag2.getId(), empresaId);
        pagamentoService.cancelarPagamentoPendenteDoAgendamento(ag2.getId(), empresaId);

        assertEquals(StatusPagamento.CANCELADO, pagamentoRepository.findById(p2.getId()).orElseThrow().getStatus());
        assertEquals(0, new BigDecimal("200.00").compareTo(caixaDe(empresaId)),
                "nenhum caixa extra apos cancelamentos");
    }
}

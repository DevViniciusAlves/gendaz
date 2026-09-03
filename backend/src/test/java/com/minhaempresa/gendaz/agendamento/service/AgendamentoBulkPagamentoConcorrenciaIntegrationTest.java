package com.minhaempresa.gendaz.agendamento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.minhaempresa.gendaz.financeiro.caixadespesas.enums.TipoCaixaDespesasLog;
import com.minhaempresa.gendaz.financeiro.caixadespesas.repository.CaixaDespesasLogRepository;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.AtualizarStatusPagamentoRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.MarcarPagamentoPagoRequest;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.pagamento.service.PagamentoService;
import com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity;
import com.minhaempresa.gendaz.profissional.enums.DiaSemana;
import com.minhaempresa.gendaz.profissional.repository.ProfissionalRepository;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import com.minhaempresa.gendaz.servico.repository.ServicoRepository;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import com.minhaempresa.gendaz.shared.security.UsuarioAutenticadoProvider;
import java.math.BigDecimal;
import java.time.DayOfWeek;
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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * Hardening do TOCTOU Bulk FINALIZAR x Pagamento: o bulk NUNCA le o pagamento
 * fora de transacao para decidir {@code pago}. A decisao PAGO/PENDENTE ocorre
 * dentro de {@code finalizarPreservandoPagamento}, depois dos locks
 * Agendamento -&gt; Pagamento. Estes testes provam, com Spring real e
 * transacoes independentes, que uma alteracao financeira concorrente legitima
 * nunca e desfeita por snapshot stale do bulk.
 *
 * <p>Banco: H2 (Docker indisponivel neste ambiente — vide relatorio). Os
 * invariantes sao deterministicos no codigo correto (ambas as threads sempre
 * completam; o final so depende da ordem serial, nunca de leitura stale).
 */
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.test.context.TestPropertySource(
        properties = "spring.datasource.hikari.maximum-pool-size=8")
class AgendamentoBulkPagamentoConcorrenciaIntegrationTest {

    @Autowired AgendamentoBulkService bulkService;
    @Autowired AgendamentoService agendamentoService;
    @Autowired PagamentoService pagamentoService;
    @Autowired AgendamentoRepository agendamentoRepository;
    @Autowired PagamentoRepository pagamentoRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ServicoRepository servicoRepository;
    @Autowired ProfissionalRepository profissionalRepository;
    @Autowired CaixaDespesasLogRepository logRepository;

    @MockBean AssinaturaService assinaturaService;
    @MockBean UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    @BeforeEach
    void setup() {
        when(assinaturaService.isPlanoComRecursosAvancados(anyLong())).thenReturn(true);
        when(usuarioAutenticadoProvider.exigirUsuarioId()).thenReturn(1L);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    // ---------- infra ----------

    private EmpresaEntity novaEmpresa(BigDecimal caixa) {
        EmpresaEntity empresa = empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia("BulkPag " + System.nanoTime())
                .email("bp" + System.nanoTime() + "@x.com")
                .status(StatusEmpresa.ATIVA)
                .caixaTotal(caixa)
                .despesasTotal(BigDecimal.ZERO)
                .build());
        when(assinaturaService.isPlanoComRecursosAvancados(empresa.getId())).thenReturn(true);
        return empresa;
    }

    private ClienteEntity novoCliente(EmpresaEntity empresa) {
        return clienteRepository.save(ClienteEntity.builder()
                .nome("Cli").telefone("65973" + String.format("%06d", (int) (Math.random() * 1000000)))
                .email("bp" + System.nanoTime() + "@x.com")
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
            ServicoEntity servico, ProfissionalEntity profissional, StatusAgendamento status) {
        return agendamentoRepository.save(AgendamentoEntity.builder()
                .cliente(cliente).servico(servico).profissional(profissional).empresa(empresa)
                .data(proximoDiaUtil()).horaInicio(LocalTime.of(9, 0)).horaFim(LocalTime.of(9, 30))
                .status(status).build());
    }

    private PagamentoEntity novoPagamento(EmpresaEntity empresa, ClienteEntity cliente,
            AgendamentoEntity agendamento, StatusPagamento status, MetodoPagamento metodo) {
        return pagamentoRepository.save(PagamentoEntity.builder()
                .cliente(cliente).empresa(empresa).agendamento(agendamento)
                .valor(new BigDecimal("200.00")).metodoPagamento(metodo)
                .status(status).build());
    }

    private long contarLogs(Long empresaId, TipoCaixaDespesasLog tipo) {
        return logRepository.findAll().stream()
                .filter(l -> l.getTipo() == tipo
                        && l.getBusiness() != null && empresaId.equals(l.getBusiness().getId()))
                .count();
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

    // ---------- CENARIO A: PAGO -> PENDENTE concorrente ----------

    @Test
    void bulkFinalizarXEstornoNuncaRessuscitaPagoStale() throws Exception {
        for (int iteracao = 0; iteracao < 10; iteracao++) {
            EmpresaEntity empresa = novaEmpresa(new BigDecimal("200.00"));
            Long empresaId = empresa.getId();
            ClienteEntity cliente = novoCliente(empresa);
            ServicoEntity servico = novoServico(empresa);
            ProfissionalEntity profissional = novoProfissional(empresa);
            AgendamentoEntity ag = novoAgendamento(empresa, cliente, servico, profissional, StatusAgendamento.PAUSADO);
            PagamentoEntity pag = novoPagamento(empresa, cliente, ag, StatusPagamento.PAGO, MetodoPagamento.DINHEIRO);
            Long agId = ag.getId();
            Long pagId = pag.getId();

            AtomicReference<Object> respostaBulk = new AtomicReference<>();
            AtomicInteger sucessos = new AtomicInteger();
            ConcurrentLinkedQueue<Throwable> erros = new ConcurrentLinkedQueue<>();
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                CountDownLatch inicio = new CountDownLatch(1);
                Future<?> fa = executor.submit(() -> {
                    try {
                        CompanyContext.setCompanyId(empresaId);
                        inicio.await();
                        respostaBulk.set(bulkService.executar(new AcaoEmMassaAgendamentoRequest(
                                List.of(agId), "FINALIZAR", empresaId)));
                        sucessos.incrementAndGet();
                    } catch (Throwable t) {
                        erros.add(t);
                    } finally {
                        CompanyContext.clear();
                    }
                });
                Future<?> fb = executor.submit(comEmpresa(empresaId, () -> {
                    try {
                        inicio.await();
                        pagamentoService.atualizarStatus(pagId,
                                new AtualizarStatusPagamentoRequest(StatusPagamento.PENDENTE));
                        sucessos.incrementAndGet();
                    } catch (Throwable t) {
                        erros.add(t);
                        throw new RuntimeException(t);
                    }
                }));
                inicio.countDown();
                fa.get(60, TimeUnit.SECONDS);
                fb.get(60, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }

            assertEquals(2, sucessos.get(), "iteracao " + iteracao + ": ambas concluem. erros=" + erros);
            // Invariante serial: o estorno sempre completa (PAGO->PENDENTE e
            // valido em qualquer ordem) e o bulk preserva o estado sob lock.
            // Com o TOCTOU antigo, o bulk poderia ressuscitar PAGO+Caixa aqui.
            assertEquals(StatusPagamento.PENDENTE,
                    pagamentoRepository.findById(pagId).orElseThrow().getStatus(), "iteracao " + iteracao);
            assertEquals(StatusAgendamento.FINALIZADO,
                    agendamentoRepository.findById(agId).orElseThrow().getStatus(), "iteracao " + iteracao);
            assertEquals(0, BigDecimal.ZERO.compareTo(
                    empresaRepository.findById(empresaId).orElseThrow().getCaixaTotal()), "iteracao " + iteracao);
            assertEquals(0, contarLogs(empresaId, TipoCaixaDespesasLog.PAGAMENTO_APROVADO), "iteracao " + iteracao);
            assertEquals(1, contarLogs(empresaId, TipoCaixaDespesasLog.PAGAMENTO_REMOVIDO), "iteracao " + iteracao);
        }
    }

    // ---------- CENARIO B: PENDENTE -> PAGO concorrente ----------

    @Test
    void bulkFinalizarXConfirmacaoNuncaPerdeNemDuplicaPagamento() throws Exception {
        for (int iteracao = 0; iteracao < 3; iteracao++) {
            EmpresaEntity empresa = novaEmpresa(BigDecimal.ZERO);
            Long empresaId = empresa.getId();
            ClienteEntity cliente = novoCliente(empresa);
            ServicoEntity servico = novoServico(empresa);
            ProfissionalEntity profissional = novoProfissional(empresa);
            AgendamentoEntity ag = novoAgendamento(empresa, cliente, servico, profissional, StatusAgendamento.PAUSADO);
            PagamentoEntity pag = novoPagamento(empresa, cliente, ag, StatusPagamento.PENDENTE, MetodoPagamento.OUTRO);
            Long agId = ag.getId();
            Long pagId = pag.getId();

            AtomicInteger sucessos = new AtomicInteger();
            ConcurrentLinkedQueue<Throwable> erros = new ConcurrentLinkedQueue<>();
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                CountDownLatch inicio = new CountDownLatch(1);
                Future<?> fa = executor.submit(() -> {
                    try {
                        CompanyContext.setCompanyId(empresaId);
                        inicio.await();
                        bulkService.executar(new AcaoEmMassaAgendamentoRequest(
                                List.of(agId), "FINALIZAR", empresaId));
                        sucessos.incrementAndGet();
                    } catch (Throwable t) {
                        erros.add(t);
                    } finally {
                        CompanyContext.clear();
                    }
                });
                Future<?> fb = executor.submit(comEmpresa(empresaId, () -> {
                    try {
                        inicio.await();
                        pagamentoService.marcarPago(pagId,
                                new MarcarPagamentoPagoRequest(MetodoPagamento.PIX, null));
                        sucessos.incrementAndGet();
                    } catch (Throwable t) {
                        erros.add(t);
                        throw new RuntimeException(t);
                    }
                }));
                inicio.countDown();
                fa.get(60, TimeUnit.SECONDS);
                fb.get(60, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }

            assertEquals(2, sucessos.get(), "iteracao " + iteracao + ". erros=" + erros);
            // Qualquer ordem serial termina em PAGO com exatamente +200 uma vez.
            // Com o TOCTOU antigo, o bulk poderia sobrescrever PAGO->PENDENTE.
            assertEquals(StatusAgendamento.FINALIZADO,
                    agendamentoRepository.findById(agId).orElseThrow().getStatus(), "iteracao " + iteracao);
            assertEquals(StatusPagamento.PAGO,
                    pagamentoRepository.findById(pagId).orElseThrow().getStatus(), "iteracao " + iteracao);
            assertEquals(0, new BigDecimal("200.00").compareTo(
                    empresaRepository.findById(empresaId).orElseThrow().getCaixaTotal()), "iteracao " + iteracao);
            assertEquals(1, contarLogs(empresaId, TipoCaixaDespesasLog.PAGAMENTO_APROVADO), "iteracao " + iteracao);
        }
    }

    // ---------- TESTE C: bulk preserva PAGO sem duplicar Caixa ----------

    @Test
    void bulkFinalizarPreservaPagoSemNovoLancamento() {
        EmpresaEntity empresa = novaEmpresa(new BigDecimal("200.00"));
        Long empresaId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente, servico, profissional, StatusAgendamento.PAUSADO);
        PagamentoEntity pag = novoPagamento(empresa, cliente, ag, StatusPagamento.PAGO, MetodoPagamento.DINHEIRO);

        CompanyContext.setCompanyId(empresaId);
        var resposta = bulkService.executar(new AcaoEmMassaAgendamentoRequest(
                List.of(ag.getId()), "FINALIZAR", empresaId));
        CompanyContext.clear();

        assertEquals(1, resposta.totalProcessado());
        assertEquals(StatusAgendamento.FINALIZADO,
                agendamentoRepository.findById(ag.getId()).orElseThrow().getStatus());
        assertEquals(StatusPagamento.PAGO,
                pagamentoRepository.findById(pag.getId()).orElseThrow().getStatus());
        assertEquals(0, new BigDecimal("200.00").compareTo(
                empresaRepository.findById(empresaId).orElseThrow().getCaixaTotal()));
        assertEquals(0, contarLogs(empresaId, TipoCaixaDespesasLog.PAGAMENTO_APROVADO));
    }

    // ---------- TESTE D: bulk preserva PENDENTE sem inventar recebimento ----------

    @Test
    void bulkFinalizarPreservaPendenteSemInventarRecebimento() {
        EmpresaEntity empresa = novaEmpresa(BigDecimal.ZERO);
        Long empresaId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.EM_ATENDIMENTO);
        PagamentoEntity pag = novoPagamento(empresa, cliente, ag, StatusPagamento.PENDENTE, MetodoPagamento.OUTRO);

        CompanyContext.setCompanyId(empresaId);
        var resposta = bulkService.executar(new AcaoEmMassaAgendamentoRequest(
                List.of(ag.getId()), "FINALIZAR", empresaId));
        CompanyContext.clear();

        assertEquals(1, resposta.totalProcessado());
        assertEquals(StatusAgendamento.FINALIZADO,
                agendamentoRepository.findById(ag.getId()).orElseThrow().getStatus());
        assertEquals(StatusPagamento.PENDENTE,
                pagamentoRepository.findById(pag.getId()).orElseThrow().getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(
                empresaRepository.findById(empresaId).orElseThrow().getCaixaTotal()));
        assertEquals(0, contarLogs(empresaId, TipoCaixaDespesasLog.PAGAMENTO_APROVADO));
    }

    // ---------- TESTE E: pagamento CANCELADO nao ressuscita ----------

    @Test
    void bulkFinalizarComPagamentoCanceladoFalhaSemRessuscitar() {
        EmpresaEntity empresa = novaEmpresa(BigDecimal.ZERO);
        Long empresaId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.EM_ATENDIMENTO);
        PagamentoEntity pag = novoPagamento(empresa, cliente, ag, StatusPagamento.CANCELADO, MetodoPagamento.OUTRO);

        CompanyContext.setCompanyId(empresaId);
        var resposta = bulkService.executar(new AcaoEmMassaAgendamentoRequest(
                List.of(ag.getId()), "FINALIZAR", empresaId));
        CompanyContext.clear();

        assertEquals(0, resposta.totalProcessado());
        assertEquals(1, resposta.falhas().size());
        assertEquals(StatusAgendamento.EM_ATENDIMENTO,
                agendamentoRepository.findById(ag.getId()).orElseThrow().getStatus());
        assertEquals(StatusPagamento.CANCELADO,
                pagamentoRepository.findById(pag.getId()).orElseThrow().getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(
                empresaRepository.findById(empresaId).orElseThrow().getCaixaTotal()));
    }
}

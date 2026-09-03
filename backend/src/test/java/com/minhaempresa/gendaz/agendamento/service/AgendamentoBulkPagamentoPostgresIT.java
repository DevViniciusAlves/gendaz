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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Versao PostgreSQL real (Testcontainers) do hardening Bulk x Pagamento.
 *
 * <p>EXECUCAO: somente em CI/ambiente com Docker (classe {@code *IT} nao e
 * executada pelo surefire no {@code mvn test} padrao). NAO foi executada
 * localmente nesta tarefa porque o daemon Docker estava indisponivel; os
 * mesmos cenarios estao cobertos em H2 por
 * {@code AgendamentoBulkPagamentoConcorrenciaIntegrationTest}.
 */
@Testcontainers
@ActiveProfiles("test")
@org.springframework.test.context.TestPropertySource(
        properties = {
                "spring.datasource.hikari.maximum-pool-size=10",
                "JWT_SECRET=super_secret_key_for_jwt_tokens_testing_123456789",
                "SUPER_ADMIN_PASSWORD=super_secret_admin_pass_123456789"
        })
class AgendamentoBulkPagamentoPostgresIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired AgendamentoBulkService bulkService;
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

    private EmpresaEntity novaEmpresa(BigDecimal caixa) {
        EmpresaEntity empresa = empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia("IT " + System.nanoTime())
                .email("it" + System.nanoTime() + "@x.com")
                .status(StatusEmpresa.ATIVA)
                .caixaTotal(caixa)
                .despesasTotal(BigDecimal.ZERO)
                .build());
        when(assinaturaService.isPlanoComRecursosAvancados(empresa.getId())).thenReturn(true);
        return empresa;
    }

    private ClienteEntity novoCliente(EmpresaEntity empresa) {
        return clienteRepository.save(ClienteEntity.builder()
                .nome("Cli").telefone("65974" + String.format("%06d", (int) (Math.random() * 1000000)))
                .email("it" + System.nanoTime() + "@x.com")
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

    @Test
    void bulkFinalizarXEstornoNoPostgresNuncaRessuscitaPagoStale() throws Exception {
        EmpresaEntity empresa = novaEmpresa(new BigDecimal("200.00"));
        Long empresaId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);
        AgendamentoEntity ag = agendamentoRepository.save(AgendamentoEntity.builder()
                .cliente(cliente).servico(servico).profissional(profissional).empresa(empresa)
                .data(proximoDiaUtil()).horaInicio(LocalTime.of(9, 0)).horaFim(LocalTime.of(9, 30))
                .status(StatusAgendamento.PAUSADO).build());
        PagamentoEntity pag = pagamentoRepository.save(PagamentoEntity.builder()
                .cliente(cliente).empresa(empresa).agendamento(ag)
                .valor(new BigDecimal("200.00")).metodoPagamento(MetodoPagamento.DINHEIRO)
                .status(StatusPagamento.PAGO).build());
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
            Future<?> fb = executor.submit(() -> {
                try {
                    CompanyContext.setCompanyId(empresaId);
                    inicio.await();
                    pagamentoService.atualizarStatus(pagId,
                            new AtualizarStatusPagamentoRequest(StatusPagamento.PENDENTE));
                    sucessos.incrementAndGet();
                } catch (Throwable t) {
                    erros.add(t);
                    throw new RuntimeException(t);
                } finally {
                    CompanyContext.clear();
                }
            });
            inicio.countDown();
            fa.get(60, TimeUnit.SECONDS);
            fb.get(60, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(2, sucessos.get(), "erros=" + erros);
        assertEquals(StatusPagamento.PENDENTE,
                pagamentoRepository.findById(pagId).orElseThrow().getStatus());
        assertEquals(StatusAgendamento.FINALIZADO,
                agendamentoRepository.findById(agId).orElseThrow().getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(
                empresaRepository.findById(empresaId).orElseThrow().getCaixaTotal()));
        assertEquals(0, logRepository.findAll().stream()
                .filter(l -> l.getTipo() == TipoCaixaDespesasLog.PAGAMENTO_APROVADO
                        && empresaId.equals(l.getBusiness() != null ? l.getBusiness().getId() : null))
                .count());
    }
}

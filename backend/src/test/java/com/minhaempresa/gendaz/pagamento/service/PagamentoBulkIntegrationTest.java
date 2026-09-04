package com.minhaempresa.gendaz.pagamento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.AcaoEmMassaPagamentoRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.AtualizarStatusPagamentoRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.MarcarPagamentoPagoRequest;
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
import com.minhaempresa.gendaz.shared.security.UsuarioAutenticadoProvider;
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
 * Integration Tests para PagamentoBulkService (PB-01 a PB-08).
 * Testa semantica de sucesso parcial, transacoes por item sem rollback global,
 * idempotencia do Caixa, cross-tenant e concorrencia sob Spring real.
 */
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.test.context.TestPropertySource(
        properties = {
                "spring.datasource.hikari.maximum-pool-size=10",
                "JWT_SECRET=super_secret_key_for_jwt_tokens_testing_123456789",
                "SUPER_ADMIN_PASSWORD=super_secret_admin_pass_123456789"
        })
class PagamentoBulkIntegrationTest {

    @Autowired PagamentoBulkService bulkService;
    @Autowired PagamentoService pagamentoService;
    @Autowired PagamentoRepository pagamentoRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired ClienteRepository clienteRepository;

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

    // ---------- infra de teste ----------

    private EmpresaEntity novaEmpresa() {
        EmpresaEntity empresa = empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia("PB Empresa " + System.nanoTime())
                .email("pb" + System.nanoTime() + "@test.com")
                .status(StatusEmpresa.ATIVA)
                .caixaTotal(BigDecimal.ZERO)
                .despesasTotal(BigDecimal.ZERO)
                .build());
        when(assinaturaService.isPlanoComRecursosAvancados(empresa.getId())).thenReturn(true);
        return empresa;
    }

    private ClienteEntity novoCliente(EmpresaEntity empresa) {
        return clienteRepository.save(ClienteEntity.builder()
                .nome("Cli Bulk")
                .telefone("659" + String.format("%07d", (int) (Math.random() * 10000000)))
                .email("pbcli" + System.nanoTime() + "@test.com")
                .empresa(empresa)
                .status(StatusCadastro.ATIVO)
                .build());
    }

    private PagamentoEntity novoPagamento(EmpresaEntity empresa, ClienteEntity cliente, BigDecimal valor, StatusPagamento status) {
        return pagamentoRepository.save(PagamentoEntity.builder()
                .empresa(empresa)
                .cliente(cliente)
                .valor(valor)
                .status(status)
                .metodoPagamento(MetodoPagamento.OUTRO)
                .build());
    }

    private Runnable comEmpresa(Long empresaId, Runnable action) {
        return () -> {
            CompanyContext.setCompanyId(empresaId);
            try {
                action.run();
            } finally {
                CompanyContext.clear();
            }
        };
    }

    // ---------- TESTE PB-01: SUCESSO PARCIAL REAL ----------

    @Test
    void pb01_sucessoParcialReal() {
        EmpresaEntity empresa = novaEmpresa();
        Long empId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);

        PagamentoEntity pagA = novoPagamento(empresa, cliente, new BigDecimal("100.00"), StatusPagamento.PENDENTE);
        Long pagBInvalidoId = 999999L; // inexistente
        PagamentoEntity pagC = novoPagamento(empresa, cliente, new BigDecimal("150.00"), StatusPagamento.PENDENTE);

        CompanyContext.setCompanyId(empId);
        AcaoEmMassaPagamentoRequest req = new AcaoEmMassaPagamentoRequest(
                List.of(pagA.getId(), pagBInvalidoId, pagC.getId()), "MARCAR_COMO_PAGO", empId, MetodoPagamento.PIX, null);
        var response = bulkService.executar(req);
        CompanyContext.clear();

        assertEquals(3, response.totalSolicitado());
        assertEquals(2, response.totalProcessado());
        assertEquals(1, response.falhas().size());
        assertEquals(pagBInvalidoId, response.falhas().get(0).id());

        // Reconsulta no banco
        assertEquals(StatusPagamento.PAGO, pagamentoRepository.findById(pagA.getId()).orElseThrow().getStatus());
        assertEquals(StatusPagamento.PAGO, pagamentoRepository.findById(pagC.getId()).orElseThrow().getStatus());
        assertEquals(0, new BigDecimal("250.00").compareTo(empresaRepository.findById(empId).orElseThrow().getCaixaTotal()));
    }

    // ---------- TESTE PB-02: ERRO NO MEIO ----------

    @Test
    void pb02_erroNoMeio_itemAnteriorCommitadoEItemPosteriorExecuta() {
        EmpresaEntity empresa = novaEmpresa();
        Long empId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);

        PagamentoEntity pagA = novoPagamento(empresa, cliente, new BigDecimal("100.00"), StatusPagamento.PENDENTE);
        Long pagBInvalidoId = 888888L;
        PagamentoEntity pagC = novoPagamento(empresa, cliente, new BigDecimal("200.00"), StatusPagamento.PENDENTE);

        CompanyContext.setCompanyId(empId);
        var response = bulkService.executar(new AcaoEmMassaPagamentoRequest(
                List.of(pagA.getId(), pagBInvalidoId, pagC.getId()), "MARCAR_COMO_PAGO", empId, MetodoPagamento.DINHEIRO, null));
        CompanyContext.clear();

        assertEquals(2, response.totalProcessado());
        assertEquals(1, response.falhas().size());
        assertEquals(StatusPagamento.PAGO, pagamentoRepository.findById(pagA.getId()).orElseThrow().getStatus());
        assertEquals(StatusPagamento.PAGO, pagamentoRepository.findById(pagC.getId()).orElseThrow().getStatus());
    }

    // ---------- TESTE PB-03: FALHA PRIMEIRO ----------

    @Test
    void pb03_falhaPrimeiro_itemSegundoPersiste() {
        EmpresaEntity empresa = novaEmpresa();
        Long empId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);

        Long pagAInvalidoId = 777777L;
        PagamentoEntity pagB = novoPagamento(empresa, cliente, new BigDecimal("300.00"), StatusPagamento.PENDENTE);

        CompanyContext.setCompanyId(empId);
        var response = bulkService.executar(new AcaoEmMassaPagamentoRequest(
                List.of(pagAInvalidoId, pagB.getId()), "MARCAR_COMO_PAGO", empId, MetodoPagamento.PIX, null));
        CompanyContext.clear();

        assertEquals(1, response.totalProcessado());
        assertEquals(1, response.falhas().size());
        assertEquals(StatusPagamento.PAGO, pagamentoRepository.findById(pagB.getId()).orElseThrow().getStatus());
        assertEquals(0, new BigDecimal("300.00").compareTo(empresaRepository.findById(empId).orElseThrow().getCaixaTotal()));
    }

    // ---------- TESTE PB-04: MARCAR PAGO PARCIAL (Valido / Cancelado / Valido) ----------

    @Test
    void pb04_marcarPagoParcial_canceladoViraFalhaECaixaIncrementaSoPelosValidos() {
        EmpresaEntity empresa = novaEmpresa();
        Long empId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);

        PagamentoEntity pag1 = novoPagamento(empresa, cliente, new BigDecimal("100.00"), StatusPagamento.PENDENTE);
        PagamentoEntity pag2Cancelado = novoPagamento(empresa, cliente, new BigDecimal("500.00"), StatusPagamento.CANCELADO);
        PagamentoEntity pag3 = novoPagamento(empresa, cliente, new BigDecimal("100.00"), StatusPagamento.PENDENTE);

        CompanyContext.setCompanyId(empId);
        // Regra obrigatoria: CANCELADO no lote impede toda a operacao (ZERO alteracoes).
        try {
            org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class, () -> bulkService.executar(new AcaoEmMassaPagamentoRequest(
                    List.of(pag1.getId(), pag2Cancelado.getId(), pag3.getId()), "MARCAR_COMO_PAGO", empId, MetodoPagamento.PIX, null)));
        } finally {
            CompanyContext.clear();
        }

        // Prova ausencia de atualizacao parcial: nenhum dos tres foi alterado.
        assertEquals(StatusPagamento.PENDENTE, pagamentoRepository.findById(pag1.getId()).orElseThrow().getStatus());
        assertEquals(StatusPagamento.CANCELADO, pagamentoRepository.findById(pag2Cancelado.getId()).orElseThrow().getStatus());
        assertEquals(StatusPagamento.PENDENTE, pagamentoRepository.findById(pag3.getId()).orElseThrow().getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(empresaRepository.findById(empId).orElseThrow().getCaixaTotal()));
    }

    // ---------- TESTE PB-05: DUPLICATE ID ----------

    @Test
    void pb05_duplicateId_caixaCalculadoApenasUmaVez() {
        EmpresaEntity empresa = novaEmpresa();
        Long empId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);

        PagamentoEntity pag = novoPagamento(empresa, cliente, new BigDecimal("200.00"), StatusPagamento.PENDENTE);

        CompanyContext.setCompanyId(empId);
        // Lista contendo ID duplicado [pag.getId(), pag.getId()]
        var response = bulkService.executar(new AcaoEmMassaPagamentoRequest(
                List.of(pag.getId(), pag.getId()), "MARCAR_COMO_PAGO", empId, MetodoPagamento.PIX, null));
        CompanyContext.clear();

        assertEquals(2, response.totalProcessado());
        assertEquals(0, response.falhas().size());
        assertEquals(StatusPagamento.PAGO, pagamentoRepository.findById(pag.getId()).orElseThrow().getStatus());
        // Caixa deve ter incrementado apenas R$ 200.00, NUNCA R$ 400.00
        assertEquals(0, new BigDecimal("200.00").compareTo(empresaRepository.findById(empId).orElseThrow().getCaixaTotal()));
    }

    // ---------- TESTE PB-06: CROSS TENANT ----------

    @Test
    void pb06_crossTenant_bloqueadoSemVazarNemAlterarCaixa() {
        EmpresaEntity empresaA = novaEmpresa();
        EmpresaEntity empresaB = novaEmpresa();

        ClienteEntity clienteA = novoCliente(empresaA);
        PagamentoEntity pagEmpresaA = novoPagamento(empresaA, clienteA, new BigDecimal("100.00"), StatusPagamento.PENDENTE);

        // Executa bulk no contexto da Empresa B tentando alterar o pagamento da Empresa A
        CompanyContext.setCompanyId(empresaB.getId());
        var response = bulkService.executar(new AcaoEmMassaPagamentoRequest(
                List.of(pagEmpresaA.getId()), "MARCAR_COMO_PAGO", empresaB.getId(), MetodoPagamento.PIX, null));
        CompanyContext.clear();

        assertEquals(0, response.totalProcessado());
        assertEquals(1, response.falhas().size());

        // Pagamento da empresa A continua intocado
        assertEquals(StatusPagamento.PENDENTE, pagamentoRepository.findById(pagEmpresaA.getId()).orElseThrow().getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(empresaRepository.findById(empresaA.getId()).orElseThrow().getCaixaTotal()));
        assertEquals(0, BigDecimal.ZERO.compareTo(empresaRepository.findById(empresaB.getId()).orElseThrow().getCaixaTotal()));
    }

    // ---------- TESTE PB-07: CONCORRÊNCIA BULK x INDIVIDUAL ----------

    @Test
    void pb07_concorrenciaBulkXIndividual_caixaUmaUnicaVez() throws Exception {
        EmpresaEntity empresa = novaEmpresa();
        Long empId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);
        PagamentoEntity pag = novoPagamento(empresa, cliente, new BigDecimal("200.00"), StatusPagamento.PENDENTE);
        Long pagId = pag.getId();

        AtomicInteger sucessos = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> erros = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?> f1 = executor.submit(comEmpresa(empId, () -> {
                try {
                    start.await();
                    bulkService.executar(new AcaoEmMassaPagamentoRequest(
                            List.of(pagId), "MARCAR_COMO_PAGO", empId, MetodoPagamento.PIX, null));
                    sucessos.incrementAndGet();
                } catch (Throwable t) {
                    erros.add(t);
                }
            }));
            Future<?> f2 = executor.submit(comEmpresa(empId, () -> {
                try {
                    start.await();
                    pagamentoService.marcarPago(pagId, new MarcarPagamentoPagoRequest(MetodoPagamento.PIX, null));
                    sucessos.incrementAndGet();
                } catch (Throwable t) {
                    erros.add(t);
                }
            }));

            start.countDown();
            f1.get(10, TimeUnit.SECONDS);
            f2.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(2, sucessos.get(), "Ambas as operacoes devem concluir com sucesso. Erros: " + erros);
        assertEquals(StatusPagamento.PAGO, pagamentoRepository.findById(pagId).orElseThrow().getStatus());
        assertEquals(0, new BigDecimal("200.00").compareTo(empresaRepository.findById(empId).orElseThrow().getCaixaTotal()));
    }

    // ---------- TESTE PB-08: BULK x CANCELAMENTO ----------

    @Test
    void pb08_bulkXCancelamento_preservaConsistenciaSerial() throws Exception {
        EmpresaEntity empresa = novaEmpresa();
        Long empId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);
        PagamentoEntity pag = novoPagamento(empresa, cliente, new BigDecimal("200.00"), StatusPagamento.PENDENTE);
        Long pagId = pag.getId();

        AtomicInteger sucessos = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> erros = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?> f1 = executor.submit(comEmpresa(empId, () -> {
                try {
                    start.await();
                    bulkService.executar(new AcaoEmMassaPagamentoRequest(
                            List.of(pagId), "MARCAR_COMO_PAGO", empId, MetodoPagamento.PIX, null));
                    sucessos.incrementAndGet();
                } catch (Throwable t) {
                    erros.add(t);
                }
            }));
            Future<?> f2 = executor.submit(comEmpresa(empId, () -> {
                try {
                    start.await();
                    pagamentoService.atualizarStatus(pagId, new AtualizarStatusPagamentoRequest(StatusPagamento.CANCELADO));
                    sucessos.incrementAndGet();
                } catch (Throwable t) {
                    erros.add(t);
                }
            }));

            start.countDown();
            f1.get(10, TimeUnit.SECONDS);
            f2.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        StatusPagamento statusFinal = pagamentoRepository.findById(pagId).orElseThrow().getStatus();
        BigDecimal caixaFinal = empresaRepository.findById(empId).orElseThrow().getCaixaTotal();

        if (statusFinal == StatusPagamento.PAGO) {
            assertEquals(0, new BigDecimal("200.00").compareTo(caixaFinal));
        } else if (statusFinal == StatusPagamento.CANCELADO) {
            assertEquals(0, BigDecimal.ZERO.compareTo(caixaFinal));
        } else {
            throw new AssertionError("Status final invalido: " + statusFinal);
        }
    }
}

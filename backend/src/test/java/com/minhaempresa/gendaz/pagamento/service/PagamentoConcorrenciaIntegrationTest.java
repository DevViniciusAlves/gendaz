package com.minhaempresa.gendaz.pagamento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.financeiro.caixadespesas.enums.TipoCaixaDespesasLog;
import com.minhaempresa.gendaz.financeiro.caixadespesas.repository.CaixaDespesasLogRepository;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.MarcarPagamentoPagoRequest;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * Parte 13 (comportamental) — duas confirmacoes simultaneas do mesmo
 * pagamento PENDENTE geram NO MAXIMO UMA entrada no Caixa. O lock
 * pessimista serializa as transacoes: a segunda enxerga o status PAGO
 * e o guard de idempotencia impede o segundo lancamento.
 */
@SpringBootTest
@ActiveProfiles("test")
// Pool maior apenas neste teste: com 1 conexao as transacoes serializariam
// na aquisicao de conexao e o teste nao exercitaria o lock pessimista.
@org.springframework.test.context.TestPropertySource(
        properties = "spring.datasource.hikari.maximum-pool-size=5")
class PagamentoConcorrenciaIntegrationTest {

    @Autowired PagamentoService pagamentoService;
    @Autowired PagamentoRepository pagamentoRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired CaixaDespesasLogRepository logRepository;

    @MockBean AssinaturaService assinaturaService;

    @Test
    void duasConfirmacoesSimultaneasGeramUmaUnicaEntradaNoCaixa() throws Exception {
        when(assinaturaService.isPlanoComRecursosAvancados(anyLong())).thenReturn(true);
        EmpresaEntity empresa = empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia("Loja Caixa").email("caixa@iron.com").status(StatusEmpresa.ATIVA).build());
        ClienteEntity cliente = clienteRepository.save(ClienteEntity.builder()
                .nome("Ana").telefone("65990000111").email("anacaixa@x.com")
                .empresa(empresa).status(StatusCadastro.ATIVO).build());
        PagamentoEntity pagamento = pagamentoRepository.save(PagamentoEntity.builder()
                .cliente(cliente).empresa(empresa)
                .valor(new BigDecimal("100.00")).metodoPagamento(MetodoPagamento.OUTRO)
                .status(StatusPagamento.PENDENTE).build());
        Long pagamentoId = pagamento.getId();
        Long empresaId = empresa.getId();

        // Duas threads: o lock pessimista serializa as transacoes mesmo no H2
        // de teste (pool de 1 conexao, lock em nivel de tabela). Sem o lock,
        // ambas poderiam ler PENDENTE e gerar 2 lancamentos.
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);
        AtomicInteger sucessos = new AtomicInteger();
        AtomicInteger falhas = new AtomicInteger();
        java.util.concurrent.ConcurrentLinkedQueue<Throwable> erros = new java.util.concurrent.ConcurrentLinkedQueue<>();
        Runnable confirma = () -> {
            try {
                CompanyContext.setCompanyId(empresaId);
                inicio.await();
                pagamentoService.marcarPago(pagamentoId,
                        new MarcarPagamentoPagoRequest(MetodoPagamento.PIX, null));
                sucessos.incrementAndGet();
            } catch (Throwable t) {
                erros.add(t);
                falhas.incrementAndGet();
            } finally {
                CompanyContext.clear();
            }
        };
        Future<?>[] futures = new Future[2];
        for (int i = 0; i < 2; i++) {
            futures[i] = executor.submit(confirma);
        }
        inicio.countDown();
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        executor.shutdownNow();

        assertEquals(2, sucessos.get(), "confirmacao e idempotente: repetir nao falha. erros=" + erros);
        assertEquals(0, falhas.get());
        assertEquals(0, falhas.get());
        PagamentoEntity final_ = pagamentoRepository.findById(pagamentoId).orElseThrow();
        assertEquals(StatusPagamento.PAGO, final_.getStatus());
        EmpresaEntity empresaFinal = empresaRepository.findById(empresaId).orElseThrow();
        assertEquals(0, new BigDecimal("100.00").compareTo(empresaFinal.getCaixaTotal()),
                "Caixa deve conter UMA unica entrada de R$ 100,00");
        long lancamentos = logRepository.findAll().stream()
                .filter(l -> l.getTipo() == TipoCaixaDespesasLog.PAGAMENTO_APROVADO
                        && empresaId.equals(l.getBusiness() != null ? l.getBusiness().getId() : null))
                .count();
        assertEquals(1, lancamentos, "Uma unica movimentacao PAGAMENTO_APROVADO");
    }
}

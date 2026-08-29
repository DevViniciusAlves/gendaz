package com.minhaempresa.gendaz.meugendazpromocao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.meugendazpromocao.entity.MeuGendazPromocaoEntity;
import com.minhaempresa.gendaz.meugendazpromocao.repository.MeuGendazPromocaoRepository;
import com.minhaempresa.gendaz.promocao.entity.PromocaoEntity;
import com.minhaempresa.gendaz.promocao.enums.TipoPromocao;
import com.minhaempresa.gendaz.promocao.repository.PromocaoRepository;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class LockDiagnosticTest {

    @Autowired
    private EmpresaRepository empresaRepository;
    @Autowired
    private MeuGendazPromocaoRepository promocaoRepository;
    @Autowired
    private PromocaoRepository promocaoAdminRepository;

    @Test
    void inicioDeOutraTransacaoDeveEsperarLock() throws Exception {
        EmpresaEntity empresa = empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia("Lock Diag").email("lock@diag.com").status(StatusEmpresa.ATIVA).build());
        promocaoAdminRepository.save(PromocaoEntity.builder()
                .empresa(empresa).codigo("LOCK").descrição("x")
                .tipo(TipoPromocao.VALOR_FIXO).valor(new BigDecimal("10.00"))
                .dataInicio(LocalDateTime.now().minusDays(1)).dataFim(LocalDateTime.now().plusDays(1))
                .quantidadeLimite(1).quantidadeUsada(0).status(StatusCadastro.ATIVO)
                .aplicarTodosServicos(true).build());
        MeuGendazPromocaoEntity mirror = promocaoRepository.save(MeuGendazPromocaoEntity.builder()
                .empresa(empresa).codigo("LOCK").descrição("x")
                .tipo("VALOR_FIXO").valor(new BigDecimal("10.00"))
                .dataInicio(LocalDateTime.now().minusDays(1)).dataFim(LocalDateTime.now().plusDays(1))
                .quantidadeLimite(1).quantidadeUsada(0).status(StatusCadastro.ATIVO)
                .aplicarTodosServicos(true).build());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch primeiroLockou = new CountDownLatch(1);
        AtomicReference<Integer> segundoQuantidade = new AtomicReference<>(-1);
        CountDownLatch segundoTerminou = new CountDownLatch(1);

        Future<?> f1 = executor.submit(() -> {
            try {
                inicio.await();
                promocaoRepository.findByIdComLock(mirror.getId()).ifPresent(m -> {
                    m.setQuantidadeUsada(1);
                    promocaoRepository.save(m);
                });
                primeiroLockou.countDown();
                Thread.sleep(500);
            } catch (Exception t) {
                throw new RuntimeException(t);
            }
        });
        Future<?> f2 = executor.submit(() -> {
            try {
                inicio.await();
                promocaoRepository.findByIdComLock(mirror.getId()).ifPresent(m -> {
                    segundoQuantidade.set(m.getQuantidadeUsada());
                    segundoTerminou.countDown();
                });
            } catch (Throwable t) {
                segundoQuantidade.set(999);
                segundoTerminou.countDown();
            }
        });
        inicio.countDown();
        assertEquals(true, primeiroLockou.await(5, TimeUnit.SECONDS), "primeiro não lockou");
        boolean segundoBloqueou = !segundoTerminou.await(3, TimeUnit.SECONDS);
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdownNow();
        System.out.println("DIAG segundoTerminouNoInicio=" + segundoBloqueou
                + " segundoQuantidadeLida=" + segundoQuantidade.get());
        com.minhaempresa.gendaz.meugendazpromocao.entity.MeuGendazPromocaoEntity atual =
                promocaoRepository.findById(mirror.getId()).orElseThrow();
        assertEquals(1, atual.getQuantidadeUsada());
    }
}
package com.minhaempresa.gendaz.whatsapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import com.minhaempresa.gendaz.assinatura.repository.AssinaturaRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.plano.entity.PlanoEntity;
import com.minhaempresa.gendaz.plano.repository.PlanoRepository;
import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppNotificacaoEntity;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppStatusNotificacao;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppTipoNotificacao;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppNotificacaoRepository;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppEnvioWorker;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppFilaService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * Versao PostgreSQL real (Testcontainers) do claim concorrente: dois
 * workers disputando a mesma notificacao, somente um obtem (FOR UPDATE
 * SKIP LOCKED).
 *
 * <p>EXECUCAO: somente em CI/ambiente com Docker (classe {@code *IT} nao e
 * executada pelo surefire no {@code mvn test} padrao; roda via failsafe no
 * {@code mvn verify}). NAO foi executada localmente nesta tarefa porque o
 * daemon Docker estava indisponivel; o mesmo cenario esta coberto em H2 por
 * {@code WhatsAppEnvioWorkerTest}.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.test.context.TestPropertySource(
        properties = {
                "spring.datasource.hikari.maximum-pool-size=10",
                "JWT_SECRET=super_secret_key_for_jwt_tokens_testing_123456789",
                "SUPER_ADMIN_PASSWORD=super_secret_admin_pass_123456789"
        })
class WhatsAppFilaPostgresIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private WhatsAppProvider provider;

    @Autowired
    private WhatsAppEnvioWorker worker;
    @Autowired
    private WhatsAppFilaService filaService;
    @Autowired
    private WhatsAppNotificacaoRepository notificacaoRepository;
    @Autowired
    private EmpresaRepository empresaRepository;
    @Autowired
    private PlanoRepository planoRepository;
    @Autowired
    private AssinaturaRepository assinaturaRepository;

    @Test
    void doisWorkersMesmaNotificacaoApenasUmClaim() throws Exception {
        EmpresaEntity empresa = empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia("Wpp PG Claim " + System.nanoTime())
                .email("wpp-pg-claim-" + System.nanoTime() + "@teste.com")
                .status(StatusEmpresa.ATIVA)
                .build());
        PlanoEntity plano = planoRepository.findByNome("PRO").orElseThrow();
        LocalDate hoje = LocalDate.now();
        assinaturaRepository.save(AssinaturaEntity.builder()
                .empresa(empresa).plano(plano).status(StatusAssinatura.ATIVA)
                .dataInicio(hoje.minusDays(5)).dataFim(hoje.plusDays(25)).build());

        when(provider.enviarTexto(any(), any(), any(), any()))
                .thenReturn(WhatsAppSendResult.sent("WAMID-PG"));

        WhatsAppNotificacaoEntity criada = filaService.enfileirar(
                empresa.getId(), WhatsAppTipoNotificacao.CRM_RESGATE,
                "pg-claim-" + System.nanoTime(),
                LocalDateTime.now().minusMinutes(1), null, null, "5511999999999", "Texto");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch largada = new CountDownLatch(1);
            List<Throwable> erros = new CopyOnWriteArrayList<>();
            Future<?> f1 = executor.submit(() -> {
                try {
                    largada.await();
                    worker.processarLote(1);
                } catch (Throwable t) {
                    erros.add(t);
                }
            });
            Future<?> f2 = executor.submit(() -> {
                try {
                    largada.await();
                    worker.processarLote(1);
                } catch (Throwable t) {
                    erros.add(t);
                }
            });
            largada.countDown();
            f1.get(120, TimeUnit.SECONDS);
            f2.get(120, TimeUnit.SECONDS);

            assertTrue(erros.isEmpty());
            verify(provider, times(1)).enviarTexto(any(), any(), any(), any());
            WhatsAppNotificacaoEntity finalizada =
                    notificacaoRepository.findById(criada.getId()).orElseThrow();
            assertEquals(WhatsAppStatusNotificacao.ENVIADO, finalizada.getStatus());
            assertEquals(1, finalizada.getAttempts());
        } finally {
            executor.shutdownNow();
        }
    }
}

package com.minhaempresa.gendaz.whatsapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import com.minhaempresa.gendaz.assinatura.repository.AssinaturaRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.plano.entity.PlanoEntity;
import com.minhaempresa.gendaz.plano.repository.PlanoRepository;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppCategoriaCota;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppQuotaService;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppReserva;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppUsoResponse;
import java.time.LocalDate;
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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Versao PostgreSQL real (Testcontainers) da corrida na criacao inicial do
 * registro de uso do ciclo.
 *
 * <p>Cobre a semantica critica que o H2 nao reproduz: no PostgreSQL, a
 * transacao que viola a UNIQUE nao pode continuar, entao a criacao sob
 * demanda roda em transacao propria (REQUIRES_NEW) e a chamadora apenas
 * rele o registro vencedor com lock pessimista.
 *
 * <p>EXECUCAO: somente em CI/ambiente com Docker (classe {@code *IT} nao e
 * executada pelo surefire no {@code mvn test} padrao; roda via failsafe no
 * {@code mvn verify}). NAO foi executada localmente nesta tarefa porque o
 * daemon Docker estava indisponivel; o mesmo cenario esta coberto em H2 por
 * {@code WhatsAppQuotaConcorrenciaTest}.
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
class WhatsAppQuotaPostgresIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private WhatsAppQuotaService quotaService;
    @Autowired
    private EmpresaRepository empresaRepository;
    @Autowired
    private PlanoRepository planoRepository;
    @Autowired
    private AssinaturaRepository assinaturaRepository;

    @Test
    void duasReservasSimultaneasSemRegistroCriamUmUnicoRegistroComTotalDois() throws Exception {
        EmpresaEntity empresa = empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia("Wpp PG Init " + System.nanoTime())
                .email("wpp-pg-" + System.nanoTime() + "@teste.com")
                .status(StatusEmpresa.ATIVA)
                .build());
        PlanoEntity plano = planoRepository.findByNome("PRO").orElseThrow();
        LocalDate hoje = LocalDate.now();
        assinaturaRepository.save(AssinaturaEntity.builder()
                .empresa(empresa)
                .plano(plano)
                .status(StatusAssinatura.ATIVA)
                .dataInicio(hoje.minusDays(5))
                .dataFim(hoje.plusDays(25))
                .build());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch largada = new CountDownLatch(1);
            List<WhatsAppReserva> resultados = new CopyOnWriteArrayList<>();
            List<Throwable> erros = new CopyOnWriteArrayList<>();

            Future<?> primeira = executor.submit(() -> {
                try {
                    largada.await();
                    resultados.add(quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.CRM));
                } catch (Throwable t) {
                    erros.add(t);
                }
            });
            Future<?> segunda = executor.submit(() -> {
                try {
                    largada.await();
                    resultados.add(quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.CRM));
                } catch (Throwable t) {
                    erros.add(t);
                }
            });
            largada.countDown();
            primeira.get(120, TimeUnit.SECONDS);
            segunda.get(120, TimeUnit.SECONDS);

            assertTrue(erros.isEmpty(), "reservas concorrentes nao podem lancar excecao");
            assertEquals(2, resultados.size());
            assertEquals(2, resultados.stream().filter(r -> r == WhatsAppReserva.RESERVADA).count());

            WhatsAppUsoResponse uso = quotaService.consultarUso(empresa.getId());
            assertEquals(2, uso.crmReservados());
            assertEquals(0, uso.crmEnviados());
            assertEquals(8, uso.crmDisponiveis());
        } finally {
            executor.shutdownNow();
        }
    }
}

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
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WhatsAppQuotaConcorrenciaTest {

    private static final AtomicLong SEQUENCIA = new AtomicLong();

    @Autowired
    private WhatsAppQuotaService quotaService;
    @Autowired
    private EmpresaRepository empresaRepository;
    @Autowired
    private PlanoRepository planoRepository;
    @Autowired
    private AssinaturaRepository assinaturaRepository;

    @Test
    void duasReservasSimultaneasNaUltimaVagaNaoUltrapassamOLimite() throws Exception {
        long seq = SEQUENCIA.incrementAndGet();
        EmpresaEntity empresa = empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia("Wpp Concorrencia " + seq)
                .email("wpp-conc-" + seq + "-" + System.nanoTime() + "@teste.com")
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

        // PRO CRM: limite 10. Preenche 9 para restar exatamente 1 vaga.
        quotaService.consultarUso(empresa.getId());
        for (int i = 0; i < 9; i++) {
            assertEquals(
                    WhatsAppReserva.RESERVADA,
                    quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.CRM));
        }

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
            primeira.get(60, TimeUnit.SECONDS);
            segunda.get(60, TimeUnit.SECONDS);

            assertTrue(erros.isEmpty(), "reservas concorrentes nao podem lancar excecao");
            assertEquals(2, resultados.size());
            assertEquals(1, resultados.stream().filter(r -> r == WhatsAppReserva.RESERVADA).count());
            assertEquals(
                    1,
                    resultados.stream().filter(r -> r == WhatsAppReserva.LIMITE_ATINGIDO).count());

            WhatsAppUsoResponse uso = quotaService.consultarUso(empresa.getId());
            assertEquals(10, uso.crmReservados() + uso.crmEnviados());
            assertEquals(0, uso.crmDisponiveis());
        } finally {
            executor.shutdownNow();
        }
    }
}

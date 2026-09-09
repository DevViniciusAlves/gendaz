package com.minhaempresa.gendaz.whatsapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppCategoriaCota;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppStatusNotificacao;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppTipoNotificacao;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppNotificacaoRepository;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppUsoCicloRepository;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppFilaService;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppQuotaService;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppEnvioWorker;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppReserva;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WhatsAppEnvioWorkerTest {

    private static final AtomicLong SEQUENCIA = new AtomicLong();

    @MockBean
    private WhatsAppProvider provider;

    @Autowired
    private WhatsAppEnvioWorker worker;
    @Autowired
    private WhatsAppFilaService filaService;
    @Autowired
    private WhatsAppQuotaService quotaService;
    @Autowired
    private WhatsAppNotificacaoRepository notificacaoRepository;
    @Autowired
    private WhatsAppUsoCicloRepository usoRepository;
    @Autowired
    private EmpresaRepository empresaRepository;
    @Autowired
    private PlanoRepository planoRepository;
    @Autowired
    private AssinaturaRepository assinaturaRepository;

    private EmpresaEntity empresaProNova(String prefixo) {
        long seq = SEQUENCIA.incrementAndGet();
        EmpresaEntity empresa = empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia(prefixo + " " + seq)
                .email(prefixo + "-" + seq + "-" + System.nanoTime() + "@teste.com")
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
        return empresa;
    }

    private WhatsAppNotificacaoEntity enfileirarVencida(EmpresaEntity empresa, WhatsAppTipoNotificacao tipo) {
        long seq = SEQUENCIA.incrementAndGet();
        return filaService.enfileirar(
                empresa.getId(), tipo, "w-" + seq + "-" + System.nanoTime(),
                LocalDateTime.now().minusMinutes(1), null, null, "5511999999999", "Texto " + seq);
    }

    private WhatsAppNotificacaoEntity recarregar(Long id) {
        return notificacaoRepository.findById(id).orElseThrow();
    }

    private void forcarVencida(Long id) {
        WhatsAppNotificacaoEntity entidade = recarregar(id);
        entidade.setScheduledAt(LocalDateTime.now().minusMinutes(5));
        entidade.setNextAttemptAt(LocalDateTime.now().minusMinutes(5));
        notificacaoRepository.save(entidade);
    }

    @Test
    void sucessoCompletoConverteUmaReserva() {
        EmpresaEntity empresa = empresaProNova("wpp-wok");
        when(provider.enviarTexto(any(), any(), any(), any()))
                .thenReturn(WhatsAppSendResult.sent("WAMID-OK"));

        WhatsAppNotificacaoEntity criada = enfileirarVencida(empresa, WhatsAppTipoNotificacao.CRM_RESGATE);
        assertEquals(1, worker.processarLote(10));

        WhatsAppNotificacaoEntity finalizada = recarregar(criada.getId());
        assertEquals(WhatsAppStatusNotificacao.ENVIADO, finalizada.getStatus());
        assertNotNull(finalizada.getSentAt());
        assertEquals("WAMID-OK", finalizada.getProviderMessageId());
        assertNull(finalizada.getLastError());
        verify(provider, times(1)).enviarTexto(
                eq(String.valueOf(empresa.getId())), eq("5511999999999"),
                eq(finalizada.getMessageBody()), eq(criada.getIdempotencyKey()));

        assertEquals(1, quotaService.consultarUso(empresa.getId()).crmEnviados());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).crmReservados());
    }

    @Test
    void pendenteFuturaNaoEClaimada() {
        EmpresaEntity empresa = empresaProNova("wpp-wfut");
        when(provider.enviarTexto(any(), any(), any(), any()))
                .thenReturn(WhatsAppSendResult.sent("WAMID"));

        WhatsAppNotificacaoEntity criada = filaService.enfileirar(
                empresa.getId(), WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO,
                "wf-" + SEQUENCIA.incrementAndGet() + "-" + System.nanoTime(),
                LocalDateTime.now().plusHours(2), null, null, "5511999999999", "Futura");

        assertEquals(0, worker.processarLote(10));
        assertEquals(WhatsAppStatusNotificacao.PENDENTE, recarregar(criada.getId()).getStatus());
        verify(provider, never()).enviarTexto(any(), any(), any(), any());
    }

    @Test
    void duasThreadsNaoClaimamMesmaNotificacao() throws Exception {
        EmpresaEntity empresa = empresaProNova("wpp-wclaim");
        when(provider.enviarTexto(any(), any(), any(), any()))
                .thenReturn(WhatsAppSendResult.sent("WAMID"));

        WhatsAppNotificacaoEntity criada = enfileirarVencida(empresa, WhatsAppTipoNotificacao.CRM_RESGATE);

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
            f1.get(60, TimeUnit.SECONDS);
            f2.get(60, TimeUnit.SECONDS);

            assertTrue(erros.isEmpty());
            verify(provider, times(1)).enviarTexto(any(), any(), any(), any());
            WhatsAppNotificacaoEntity finalizada = recarregar(criada.getId());
            assertEquals(WhatsAppStatusNotificacao.ENVIADO, finalizada.getStatus());
            assertEquals(1, finalizada.getAttempts());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void retryAgenda1MinDepois5MinEFalhaNaTerceiraSemReservaExtra() {
        EmpresaEntity empresa = empresaProNova("wpp-wretry");
        when(provider.enviarTexto(any(), any(), any(), any()))
                .thenReturn(WhatsAppSendResult.erro(WhatsAppSendStatus.SESSION_NOT_CONNECTED));

        WhatsAppNotificacaoEntity criada = enfileirarVencida(empresa, WhatsAppTipoNotificacao.CRM_RESGATE);
        LocalDateTime antes = LocalDateTime.now();

        worker.processarLote(10);
        WhatsAppNotificacaoEntity tentativa1 = recarregar(criada.getId());
        assertEquals(WhatsAppStatusNotificacao.PENDENTE, tentativa1.getStatus());
        assertTrue(!tentativa1.getNextAttemptAt().isBefore(antes.plusMinutes(1)));
        assertTrue(!tentativa1.getNextAttemptAt().isAfter(antes.plusMinutes(2)));
        assertTrue(tentativa1.isQuotaReserved());
        assertEquals(1, quotaService.consultarUso(empresa.getId()).crmReservados());

        forcarVencida(criada.getId());
        worker.processarLote(10);
        WhatsAppNotificacaoEntity tentativa2 = recarregar(criada.getId());
        assertEquals(WhatsAppStatusNotificacao.PENDENTE, tentativa2.getStatus());
        assertTrue(!tentativa2.getNextAttemptAt().isBefore(LocalDateTime.now().plusMinutes(4)));
        assertTrue(tentativa2.isQuotaReserved());
        assertEquals(1, quotaService.consultarUso(empresa.getId()).crmReservados());

        forcarVencida(criada.getId());
        worker.processarLote(10);
        WhatsAppNotificacaoEntity falha = recarregar(criada.getId());
        assertEquals(WhatsAppStatusNotificacao.FALHOU, falha.getStatus());
        assertEquals("SESSION_NOT_CONNECTED_MAX_RETRIES", falha.getLastError());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).crmReservados());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).crmEnviados());
        verify(provider, times(3)).enviarTexto(any(), any(), any(), any());
    }

    @Test
    void falhaTerminalLiberaReserva() {
        EmpresaEntity empresa = empresaProNova("wpp-wterm");
        when(provider.enviarTexto(any(), any(), any(), any()))
                .thenReturn(WhatsAppSendResult.erro(WhatsAppSendStatus.INVALID_RECIPIENT));

        WhatsAppNotificacaoEntity criada = enfileirarVencida(empresa, WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO);
        worker.processarLote(10);

        WhatsAppNotificacaoEntity falha = recarregar(criada.getId());
        assertEquals(WhatsAppStatusNotificacao.FALHOU, falha.getStatus());
        assertEquals("INVALID_RECIPIENT", falha.getLastError());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).lembretesReservados());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).lembretesEnviados());
    }

    @Test
    void limiteAtingidoCancelaSemChamarProvider() {
        EmpresaEntity empresa = empresaProNova("wpp-wlim");
        for (int i = 0; i < 10; i++) {
            assertEquals(WhatsAppReserva.RESERVADA,
                    quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.CRM));
        }

        WhatsAppNotificacaoEntity criada = enfileirarVencida(empresa, WhatsAppTipoNotificacao.CRM_RESGATE);
        worker.processarLote(10);

        WhatsAppNotificacaoEntity cancelada = recarregar(criada.getId());
        assertEquals(WhatsAppStatusNotificacao.CANCELADO, cancelada.getStatus());
        assertEquals("QUOTA_LIMIT_EXCEEDED", cancelada.getLastError());
        verify(provider, never()).enviarTexto(any(), any(), any(), any());
    }

    @Test
    void planoSemWhatsAppCancelaSemChamarProvider() {
        long seq = SEQUENCIA.incrementAndGet();
        EmpresaEntity empresa = empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia("Wpp Wbas " + seq)
                .email("wpp-wbas-" + seq + "-" + System.nanoTime() + "@teste.com")
                .status(StatusEmpresa.ATIVA)
                .build());
        PlanoEntity basico = planoRepository.findByNome("BASICO").orElseThrow();
        LocalDate hoje = LocalDate.now();
        assinaturaRepository.save(AssinaturaEntity.builder()
                .empresa(empresa).plano(basico).status(StatusAssinatura.ATIVA)
                .dataInicio(hoje.minusDays(5)).dataFim(hoje.plusDays(25)).build());

        WhatsAppNotificacaoEntity criada = filaService.enfileirar(
                empresa.getId(), WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO,
                "wb-" + seq + "-" + System.nanoTime(),
                LocalDateTime.now().minusMinutes(1), null, null, "5511999999999", "Texto");
        worker.processarLote(10);

        WhatsAppNotificacaoEntity cancelada = recarregar(criada.getId());
        assertEquals(WhatsAppStatusNotificacao.CANCELADO, cancelada.getStatus());
        assertEquals("PLAN_WITHOUT_WHATSAPP", cancelada.getLastError());
        verify(provider, never()).enviarTexto(any(), any(), any(), any());
    }

    @Test
    void deliveryUnknownFalhaSemRetry() {
        EmpresaEntity empresa = empresaProNova("wpp-wunk");
        when(provider.enviarTexto(any(), any(), any(), any()))
                .thenReturn(WhatsAppSendResult.erro(WhatsAppSendStatus.DELIVERY_UNKNOWN));

        WhatsAppNotificacaoEntity criada = enfileirarVencida(empresa, WhatsAppTipoNotificacao.CRM_RESGATE);
        worker.processarLote(10);

        WhatsAppNotificacaoEntity falha = recarregar(criada.getId());
        assertEquals(WhatsAppStatusNotificacao.FALHOU, falha.getStatus());
        assertEquals("DELIVERY_UNKNOWN", falha.getLastError());
        assertEquals(0, worker.processarLote(10));
        verify(provider, times(1)).enviarTexto(any(), any(), any(), any());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).crmReservados());
    }

    @Test
    void providerSendFailedMapeadoNaoGeraRetryNoWorker() {
        // O provider mapeia "500 provider_send_failed" para DELIVERY_UNKNOWN
        // (ver WhatsAppSendProviderTest): o worker deve falhar sem reagendar.
        EmpresaEntity empresa = empresaProNova("wpp-wpsf");
        when(provider.enviarTexto(any(), any(), any(), any()))
                .thenReturn(WhatsAppSendResult.erro(WhatsAppSendStatus.DELIVERY_UNKNOWN));

        WhatsAppNotificacaoEntity criada = enfileirarVencida(empresa, WhatsAppTipoNotificacao.CRM_RESGATE);
        worker.processarLote(10);

        WhatsAppNotificacaoEntity falha = recarregar(criada.getId());
        assertEquals(WhatsAppStatusNotificacao.FALHOU, falha.getStatus());
        assertEquals("DELIVERY_UNKNOWN", falha.getLastError());
        assertEquals(1, falha.getAttempts());
        assertEquals(0, worker.processarLote(10));
        verify(provider, times(1)).enviarTexto(any(), any(), any(), any());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).crmReservados());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).crmEnviados());
    }

    @Test
    void segundaFinalizacaoDeSucessoNaoDuplicaConsumo() {
        EmpresaEntity empresa = empresaProNova("wpp-widem");
        when(provider.enviarTexto(any(), any(), any(), any()))
                .thenReturn(WhatsAppSendResult.sent("WAMID"));

        WhatsAppNotificacaoEntity criada = enfileirarVencida(empresa, WhatsAppTipoNotificacao.CRM_RESGATE);
        worker.processarLote(10);
        worker.finalizar(criada.getId(), WhatsAppSendResult.sent("WAMID"));

        assertEquals(1, quotaService.consultarUso(empresa.getId()).crmEnviados());
        assertEquals(WhatsAppStatusNotificacao.ENVIADO, recarregar(criada.getId()).getStatus());
    }

    @Test
    void enviandoPresoSemSendStartedVoltaParaPendente() {
        EmpresaEntity empresa = empresaProNova("wpp-wstale");
        WhatsAppNotificacaoEntity criada = enfileirarVencida(empresa, WhatsAppTipoNotificacao.CRM_RESGATE);
        quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.CRM);

        WhatsAppNotificacaoEntity presa = recarregar(criada.getId());
        presa.setStatus(WhatsAppStatusNotificacao.ENVIANDO);
        presa.setAttempts(1);
        presa.setProcessingStartedAt(LocalDateTime.now().minusMinutes(10));
        presa.setSendStartedAt(null);
        presa.setQuotaReserved(true);
        presa.setQuotaCycleStart(LocalDate.now().minusDays(5));
        notificacaoRepository.save(presa);

        assertEquals(1, worker.recuperarPresos(120));

        WhatsAppNotificacaoEntity recuperada = recarregar(criada.getId());
        assertEquals(WhatsAppStatusNotificacao.PENDENTE, recuperada.getStatus());
        assertTrue(recuperada.isQuotaReserved());
        assertEquals(1, quotaService.consultarUso(empresa.getId()).crmReservados());
    }

    @Test
    void enviandoPresoComSendStartedViraFalhaDeliveryUnknown() {
        EmpresaEntity empresa = empresaProNova("wpp-wstale2");
        WhatsAppNotificacaoEntity criada = enfileirarVencida(empresa, WhatsAppTipoNotificacao.CRM_RESGATE);
        quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.CRM);

        WhatsAppNotificacaoEntity presa = recarregar(criada.getId());
        presa.setStatus(WhatsAppStatusNotificacao.ENVIANDO);
        presa.setAttempts(1);
        presa.setProcessingStartedAt(LocalDateTime.now().minusMinutes(10));
        presa.setSendStartedAt(LocalDateTime.now().minusMinutes(9));
        presa.setQuotaReserved(true);
        presa.setQuotaCycleStart(LocalDate.now().minusDays(5));
        notificacaoRepository.save(presa);

        assertEquals(1, worker.recuperarPresos(120));

        WhatsAppNotificacaoEntity falha = recarregar(criada.getId());
        assertEquals(WhatsAppStatusNotificacao.FALHOU, falha.getStatus());
        assertEquals("DELIVERY_UNKNOWN", falha.getLastError());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).crmReservados());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).crmEnviados());
    }

    @Test
    void viradaDeCicloConfirmaNoCicloReservado() {
        EmpresaEntity empresa = empresaProNova("wpp-wciclo");
        LocalDate hoje = LocalDate.now();
        LocalDate cicloA = hoje.minusDays(40);

        AssinaturaEntity assinaturaA = assinaturaRepository.save(AssinaturaEntity.builder()
                .empresa(empresa).plano(planoRepository.findByNome("PRO").orElseThrow())
                .status(StatusAssinatura.ATIVA).dataInicio(cicloA).dataFim(hoje.plusDays(20)).build());
        // A empresa do helper ja tem assinatura vigente; expira ela para usar o ciclo A.
        assinaturaRepository.findAll().stream()
                .filter(a -> a.getEmpresa().getId().equals(empresa.getId()) && !a.getId().equals(assinaturaA.getId()))
                .forEach(a -> {
                    a.setStatus(StatusAssinatura.EXPIRADA);
                    assinaturaRepository.save(a);
                });

        when(provider.enviarTexto(any(), any(), any(), any()))
                .thenReturn(WhatsAppSendResult.erro(WhatsAppSendStatus.SERVICE_UNAVAILABLE))
                .thenReturn(WhatsAppSendResult.sent("WAMID-B"));

        WhatsAppNotificacaoEntity criada = enfileirarVencida(empresa, WhatsAppTipoNotificacao.CRM_RESGATE);
        worker.processarLote(10);
        assertEquals(cicloA, recarregar(criada.getId()).getQuotaCycleStart());

        // Virada: ciclo A vence, ciclo B comeca hoje.
        assinaturaA.setDataFim(hoje.minusDays(1));
        assinaturaRepository.save(assinaturaA);
        assinaturaRepository.save(AssinaturaEntity.builder()
                .empresa(empresa).plano(planoRepository.findByNome("PRO").orElseThrow())
                .status(StatusAssinatura.ATIVA).dataInicio(hoje).dataFim(hoje.plusDays(30)).build());

        forcarVencida(criada.getId());
        worker.processarLote(10);

        WhatsAppNotificacaoEntity enviada = recarregar(criada.getId());
        assertEquals(WhatsAppStatusNotificacao.ENVIADO, enviada.getStatus());
        assertEquals(1, usoRepository.findByEmpresaIdAndCicloInicio(empresa.getId(), cicloA)
                .orElseThrow().getCrmEnviados());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).crmEnviados());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).crmReservados());
    }

    @Test
    void categoriasConsomemCotasCorretas() {
        EmpresaEntity empresa = empresaProNova("wpp-wcat");
        when(provider.enviarTexto(any(), any(), any(), any()))
                .thenReturn(WhatsAppSendResult.sent("WAMID"));

        enfileirarVencida(empresa, WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO);
        enfileirarVencida(empresa, WhatsAppTipoNotificacao.CRM_RESGATE);
        assertEquals(2, worker.processarLote(10));

        assertEquals(1, quotaService.consultarUso(empresa.getId()).lembretesEnviados());
        assertEquals(1, quotaService.consultarUso(empresa.getId()).crmEnviados());
    }
}

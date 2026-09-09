package com.minhaempresa.gendaz.whatsapp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Agendador do worker WhatsApp. Desabilitado por padrao
 * (WHATSAPP_WORKER_ENABLED=false): Agenda e CRM ainda nao estao integrados
 * e nao queremos disparo acidental durante desenvolvimento. Os testes
 * chamam {@link WhatsAppEnvioWorker} diretamente.
 */
@Component
@ConditionalOnProperty(name = "whatsapp.worker.enabled", havingValue = "true")
@Slf4j
public class WhatsAppEnvioScheduler {

    private final WhatsAppEnvioWorker worker;
    private final int batchSize;
    private final long staleSeconds;

    public WhatsAppEnvioScheduler(
            WhatsAppEnvioWorker worker,
            @Value("${whatsapp.worker.batch-size:10}") int batchSize,
            @Value("${whatsapp.worker.stale-seconds:120}") long staleSeconds) {
        this.worker = worker;
        this.batchSize = batchSize;
        this.staleSeconds = staleSeconds;
    }

    @Scheduled(fixedDelayString = "${whatsapp.worker.delay-ms:10000}")
    public void executar() {
        try {
            worker.recuperarPresos(staleSeconds);
            worker.processarLote(batchSize);
        } catch (Exception e) {
            log.error("[whatsapp-worker] ciclo falhou. erroTipo={}", e.getClass().getSimpleName());
        }
    }
}

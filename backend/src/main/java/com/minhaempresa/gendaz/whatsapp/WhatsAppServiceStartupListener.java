package com.minhaempresa.gendaz.whatsapp;

import com.minhaempresa.gendaz.whatsapp.service.WhatsAppServiceWakeService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Listener que dispara UMA tentativa assincrona de wake do whatsapp-service
 * apos o Spring estar READY (cold start no Render via GET /health).
 * Nao bloqueia a thread do evento nem o startup do Stage: delega para
 * {@link WhatsAppServiceWakeService#wakeAsync()} e termina. O Stage continua
 * READY mesmo que o WPP ainda esteja acordando; chamadas sob demanda passam
 * pelo single-flight de {@code ensureAvailable()}.
 */
@Component
public class WhatsAppServiceStartupListener implements ApplicationListener<ApplicationReadyEvent> {

    private final WhatsAppServiceWakeService wakeService;

    public WhatsAppServiceStartupListener(WhatsAppServiceWakeService wakeService) {
        this.wakeService = wakeService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // UMA tentativa assincrona por startup; nunca ensureAvailable() bloqueante aqui.
        wakeService.wakeAsync();
    }
}

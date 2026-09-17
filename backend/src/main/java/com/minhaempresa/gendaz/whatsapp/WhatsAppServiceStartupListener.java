package com.minhaempresa.gendaz.whatsapp;

import com.minhaempresa.gendaz.whatsapp.service.WhatsAppServiceWakeService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Listener que dispara o wake do whatsapp-service apos o Spring estar READY.
 * Nao bloqueia a thread do evento: delega para {@link WhatsAppServiceWakeService#wakeAsync()}.
 */
@Component
public class WhatsAppServiceStartupListener implements ApplicationListener<ApplicationReadyEvent> {

    private final WhatsAppServiceWakeService wakeService;

    public WhatsAppServiceStartupListener(WhatsAppServiceWakeService wakeService) {
        this.wakeService = wakeService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // Wake sob demanda via ensureAvailable() - nao acordar WPP automaticamente no boot.
        // Stage pode ficar READY enquanto WPP continua dormindo.
    }
}

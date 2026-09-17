package com.minhaempresa.gendaz.whatsapp;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.minhaempresa.gendaz.whatsapp.service.WhatsAppServiceWakeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;

class WhatsAppServiceStartupListenerTest {

    @Test
    void applicationReady_disparaWakeAsyncExatamenteUmaVez() {
        WhatsAppServiceWakeService wakeService = mock(WhatsAppServiceWakeService.class);
        WhatsAppServiceStartupListener listener = new WhatsAppServiceStartupListener(wakeService);

        listener.onApplicationEvent(mock(ApplicationReadyEvent.class));

        verify(wakeService, times(1)).wakeAsync();
        verifyNoMoreInteractions(wakeService);
    }
}

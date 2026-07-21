/*
  ╔══════════════════════════════════════════════╗
  ║    DESATIVADO - FUNCIONALIDADE WhatsApp    ║
  ║  Todo código comentado. Remova comentários   ║
  ║  para reativar.                              ║
  ╚══════════════════════════════════════════════╝
*/
package com.minhaempresa.agendapro.whatsapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WhatsappIntegrationProperties {
    @Value("${whatsapp-service.url:http://localhost:3000}")
    private String whatsappServiceUrl;

    @Value("${whatsapp.internal-token:}")
    private String internalToken;

    public String whatsappServiceUrl() {
        /*
        return clean(whatsappServiceUrl);
        */
        return "";
    }

    public String internalToken() {
        /*
        return clean(internalToken);
        */
        return "";
    }

    private String clean(String value) {
        /*
        return value == null ? "" : value.trim();
        */
        return "";
    }
}

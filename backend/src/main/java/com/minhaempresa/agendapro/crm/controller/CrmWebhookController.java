package com.minhaempresa.agendapro.crm.controller;

import com.minhaempresa.agendapro.crm.service.CrmService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/crm/webhook")
@RequiredArgsConstructor
@Slf4j
public class CrmWebhookController {
    private final CrmService crmService;

    @PostMapping("/resend-event")
    public ResponseEntity<?> receberEvento(@RequestBody Map<String, Object> payload) {
        try {
            String type = (String) payload.getOrDefault("type", "");
            if ("email.opened".equals(type)) {
                Map<String, Object> data = (Map<String, Object>) payload.get("data");
                if (data != null) {
                    String messageId = (String) data.get("email_id");
                    if (messageId != null) {
                        crmService.registrarAbertura(messageId);
                    }
                }
            }
            return ResponseEntity.ok(Map.of("received", true));
        } catch (Exception e) {
            log.warn("[crm-webhook] erro ao processar evento: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("received", true));
        }
    }
}

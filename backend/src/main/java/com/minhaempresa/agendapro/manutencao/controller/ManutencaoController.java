package com.minhaempresa.agendapro.manutencao.controller;

import com.minhaempresa.agendapro.whatsapp.service.WhatsappIntegrationProperties;
import com.minhaempresa.agendapro.manutencao.service.WhatsappMessageMaintenanceService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/internal/manutencao")
@RequiredArgsConstructor
public class ManutencaoController {
    private final WhatsappIntegrationProperties properties;
    private final WhatsappMessageMaintenanceService messageMaintenanceService;

    @DeleteMapping("/limpar-mensagens-antigas")
    public ResponseEntity<Map<String, Object>> limparMensagensAntigas(
            @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
            @RequestParam(defaultValue = "30") int dias) {
        validarTokenInterno(internalToken);
        long removidas = messageMaintenanceService.limparMensagensAntigas(dias);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "dias", Math.max(dias, 1),
                "removidas", removidas
        ));
    }

    private void validarTokenInterno(String recebido) {
        String esperado = properties.internalToken();
        if (esperado.isBlank()) {
            return;
        }
        if (recebido == null || recebido.isBlank() || !esperado.equals(recebido.trim())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook interno nao autorizado.");
        }
    }
}

package com.minhaempresa.agendapro.manutencao.controller;

// ⚠️ DESATIVADO — Esta classe contém endpoints exclusivos para manutenção de WhatsApp.
// ⚠️ DESATIVADO — Todos os endpoints WhatsApp estão desativados. Não utilizar em produção.

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

    // ⚠️ DESATIVADO — @DeleteMapping("/limpar-mensagens-antigas")
    // ⚠️ DESATIVADO — public ResponseEntity<Map<String, Object>> limparMensagensAntigas(
    // ⚠️ DESATIVADO —         @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
    // ⚠️ DESATIVADO —         @RequestParam(defaultValue = "30") int dias) {
    // ⚠️ DESATIVADO —     validarTokenInterno(internalToken);
    // ⚠️ DESATIVADO —     long removidas = messageMaintenanceService.limparMensagensAntigas(dias);
    // ⚠️ DESATIVADO —     return ResponseEntity.ok(Map.of(
    // ⚠️ DESATIVADO —             "success", true,
    // ⚠️ DESATIVADO —             "dias", Math.max(dias, 1),
    // ⚠️ DESATIVADO —             "removidas", removidas
    // ⚠️ DESATIVADO —     ));
    // ⚠️ DESATIVADO — }

    // ⚠️ DESATIVADO — private void validarTokenInterno(String recebido) {
    // ⚠️ DESATIVADO —     String esperado = properties.internalToken();
    // ⚠️ DESATIVADO —     if (esperado.isBlank()) {
    // ⚠️ DESATIVADO —         return;
    // ⚠️ DESATIVADO —     }
    // ⚠️ DESATIVADO —     if (recebido == null || recebido.isBlank() || !esperado.equals(recebido.trim())) {
    // ⚠️ DESATIVADO —         throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook interno nao autorizado.");
    // ⚠️ DESATIVADO —     }
    // ⚠️ DESATIVADO — }
}

package com.minhaempresa.gendaz.whatsapp.controller;

import com.minhaempresa.gendaz.whatsapp.service.WhatsAppEntregaService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Callback interno Node -&gt; Spring de entrega WhatsApp.
 *
 * <p>POST /internal/whatsapp/delivery (assincrono, nunca request longa no
 * envio): o whatsapp-service informa DELIVERY_ACK (ou READ/PLAYED
 * posteriores, que tambem comprovam entrega) observado via messages.update.
 *
 * <p>Payload minimo, sem dados pessoais: { companyId, messageId, status }.
 * Nunca trafega telefone, JID, texto, authState, QR, keys ou tokens no corpo.
 *
 * <p>Protecao: Bearer interno ({@code whatsapp.internal-token}, mesmo segredo
 * do POST de envio, sem hardcoded) em comparacao de tempo constante. Sem
 * token configurado, fail-closed (503). Rota liberada da sessao/CSRF em
 * SecurityHeadersConfig + GendazSessionAuthenticationFilter por ser
 * maquina-a-maquina.
 *
 * <p>Respostas nunca explodem 500 por corrida: receipt desconhecido e
 * persistido para reconciliacao (202 pending); duplicata e no-op (200
 * duplicate); empresa invalida e 400.
 */
@RestController
@RequestMapping("/internal/whatsapp")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppDeliveryCallbackController {

    private final WhatsAppEntregaService entregaService;

    @Value("${whatsapp.internal-token:${WHATSAPP_INTERNAL_TOKEN:}}")
    private String internalToken;

    public record DeliveryCallbackRequest(String companyId, String messageId, String status) {
    }

    @PostMapping("/delivery")
    public ResponseEntity<?> delivery(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) DeliveryCallbackRequest body) {
        if (internalToken == null || internalToken.isBlank()) {
            return ResponseEntity.status(503).body(Map.of("error", "service_unavailable"));
        }
        if (!autorizado(authorization)) {
            return ResponseEntity.status(401).body(Map.of("error", "unauthorized"));
        }
        if (body == null || body.companyId() == null || body.companyId().isBlank()
                || body.messageId() == null || body.messageId().isBlank()) {
            return ResponseEntity.status(400).body(Map.of("error", "invalid_request"));
        }
        Long empresaId;
        try {
            empresaId = Long.valueOf(body.companyId().trim());
        } catch (NumberFormatException e) {
            return ResponseEntity.status(400).body(Map.of("error", "invalid_company_id"));
        }
        String messageId = body.messageId().trim();
        if (messageId.length() > 120) {
            return ResponseEntity.status(400).body(Map.of("error", "invalid_request"));
        }
        try {
            WhatsAppEntregaService.ResultadoEntrega resultado =
                    entregaService.registrarEntrega(empresaId, messageId);
            log.info("[whatsapp-delivery] callback companyId={} messageIdPresent=true resultado={}",
                    empresaId, resultado);
            return switch (resultado) {
                case CONFIRMADO -> ResponseEntity.ok(Map.of("status", "delivered"));
                case JA_CONFIRMADO -> ResponseEntity.ok(Map.of("status", "duplicate"));
                case AGUARDANDO_NOTIFICACAO ->
                        ResponseEntity.status(202).body(Map.of("status", "pending"));
                case INVALIDO -> ResponseEntity.status(400).body(Map.of("error", "invalid_company_id"));
                case IGNORADO -> ResponseEntity.ok(Map.of("status", "ignored"));
                case ERRO_TRANSITORIO ->
                        ResponseEntity.status(503).body(Map.of("error", "service_unavailable"));
            };
} catch (Exception e) {
            // Erro na persistencia: BD indisponivel ou erro tecnico.
            // Retornar 503 para Node retry (nunca 200).
            log.error("[whatsapp-delivery] erro ao persistir receipt. tipo={}, mensagem={}",
                    e.getClass().getSimpleName(), e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error", "service_unavailable"));
        }
    }

    private boolean autorizado(String authorization) {
        if (authorization == null) {
            return false;
        }
        String esperado = "Bearer " + internalToken.trim();
        return MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8),
                authorization.getBytes(StandardCharsets.UTF_8));
    }
}

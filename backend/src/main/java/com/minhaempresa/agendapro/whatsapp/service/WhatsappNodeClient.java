/*
  ╔══════════════════════════════════════════════╗
  ║  ⚠️  DESATIVADO - FUNCIONALIDADE WhatsApp    ║
  ║  Todo código comentado. Remova comentários   ║
  ║  para reativar.                              ║
  ╚══════════════════════════════════════════════╝
*/
package com.minhaempresa.agendapro.whatsapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappConnectRequest;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappConnectResponse;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappStatusResponse;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WhatsappNodeClient {
    private final WhatsappIntegrationProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public WhatsappConnectResponse conectar(WhatsappConnectRequest request) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        JsonNode node = postJson("/connect", Map.of(
                "empresaId", request.empresaId(),
                "phone", request.phoneNumber(),
                "phoneNumber", request.phoneNumber()
        ));
        return new WhatsappConnectResponse(
                texto(node.path("status").asText("aguardando")),
                texto(node.path("statusLabel").asText("Aguardando codigo")),
                texto(node.path("message").asText("Use o codigo para conectar o WhatsApp desta empresa.")),
                texto(node.path("pairingCode").asText(node.path("code").asText(null))),
                node.path("expiresAt").isNull() ? null : toLocalDateTime(node.path("expiresAt").asText()),
                request.empresaId(),
                request.phoneNumber()
        );
        */
        return null;
    }

    public WhatsappStatusResponse status(Long empresaId) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        if (empresaId == null) {
            return toStatusResponse(objectMapper.createObjectNode(), null);
        }
        JsonNode node = getJson("/status/" + empresaId);
        if (node == null || node.isMissingNode()) {
            node = getJson("/status");
        }
        if (node == null || node.isMissingNode()) {
            node = objectMapper.createObjectNode();
        }
        return toStatusResponse(node, empresaId);
        */
        return null;
    }

    public WhatsappStatusResponse desconectar(Long empresaId) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        JsonNode node = postJson("/disconnect/" + empresaId, Map.of());
        if (node == null || node.isMissingNode()) {
            node = postJson("/disconnect", Map.of());
        }
        if (node == null || node.isMissingNode()) {
            node = objectMapper.createObjectNode();
        }
        return toStatusResponse(node, empresaId);
        */
        return null;
    }

    public WhatsappStatusResponse limparSessao(Long empresaId) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        JsonNode node = deleteJson("/session/" + empresaId);
        if (node == null || node.isMissingNode()) {
            node = objectMapper.createObjectNode();
        }
        return toStatusResponse(node, empresaId);
        */
        return null;
    }

    public void enviarAgendamento(Map<String, Object> payload) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        postJson("/webhook/agendamento", payload);
        */
        return;
    }

    public void enviarMensagem(Long empresaId, String phone, String message) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        postJson("/send", Map.of(
                "empresaId", empresaId,
                "phone", phone,
                "message", message
        ));
        */
        return;
    }

    public void enviarConfirmacaoPagamentoDono(Map<String, Object> payload) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        postJson("/payment-owner-reminder", payload);
        */
        return;
    }

    public void enviarLembrete(Map<String, Object> payload) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        postJson("/api/whatsapp/enviar-lembrete", payload);
        */
        return;
    }

    private WhatsappStatusResponse toStatusResponse(JsonNode node, Long empresaId) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        String status = texto(node.path("status").asText(
                node.path("connected").asBoolean(node.path("conectado").asBoolean(false))
                        ? "CONNECTED"
                        : "DISCONNECTED"
        ));
        String normalizedStatus = status.toUpperCase();
        String phoneNumber = texto(node.path("phoneNumber").asText(
                node.path("numeroConectado").asText(node.path("numero").asText(node.path("displayPhoneNumber").asText(null)))
        ));
        String pairingCode = texto(node.path("pairingCode").asText(node.path("code").asText(null)));
        boolean conectado = "CONNECTED".equals(normalizedStatus)
                || node.path("connected").asBoolean(false)
                || node.path("conectado").asBoolean(false)
                || node.path("whatsappConectado").asBoolean(false);
        return new WhatsappStatusResponse(
                node.path("connectionId").isNull() ? null : node.path("connectionId").asLong(),
                "BAILEYS",
                mapStatus(normalizedStatus),
                texto(node.path("statusLabel").asText(statusLabel(normalizedStatus))),
                phoneNumber,
                texto(node.path("phoneNumberId").asText(null)),
                texto(node.path("lastError").asText(null)),
                node.path("connectedAt").isNull() ? null : toLocalDateTime(node.path("connectedAt").asText()),
                node.path("disconnectedAt").isNull() ? null : toLocalDateTime(node.path("disconnectedAt").asText()),
                true,
                texto(node.path("message").asText(null)),
                pairingCode,
                node.path("expiresAt").isNull() ? null : toLocalDateTime(node.path("expiresAt").asText()),
                conectado,
                phoneNumber,
                node.path("notificationsEnabled").asBoolean(true),
                node.path("secretariaIaEnabled").asBoolean(true)
        );
        */
        return null;
    }

    private JsonNode postJson(String path, Map<String, Object> body) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        try {
            URI base = baseUri();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(base.resolve(path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Token", properties.internalToken())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            return sendJson(request);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("URL do servico de WhatsApp invalida: " + detalheErro(ex));
        } catch (Exception ex) {
            throw new BusinessException("Nao foi possivel comunicar com o servico de WhatsApp: " + detalheErro(ex));
        }
        */
        return objectMapper.createObjectNode();
    }

    private JsonNode getJson(String path) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        try {
            URI base = baseUri();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(base.resolve(path))
                    .timeout(Duration.ofSeconds(8))
                    .header("X-Internal-Token", properties.internalToken())
                    .GET()
                    .build();
            return sendJson(request);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("URL do servico de WhatsApp invalida: " + detalheErro(ex));
        } catch (Exception ex) {
            throw new BusinessException("Nao foi possivel consultar o servico de WhatsApp: " + detalheErro(ex));
        }
        */
        return objectMapper.createObjectNode();
    }

    private JsonNode deleteJson(String path) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        try {
            URI base = baseUri();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(base.resolve(path))
                    .timeout(Duration.ofSeconds(8))
                    .header("X-Internal-Token", properties.internalToken())
                    .method("DELETE", HttpRequest.BodyPublishers.noBody())
                    .build();
            return sendJson(request);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("URL do servico de WhatsApp invalida: " + detalheErro(ex));
        } catch (Exception ex) {
            throw new BusinessException("Nao foi possivel limpar a sessao do servico de WhatsApp: " + detalheErro(ex));
        }
        */
        return objectMapper.createObjectNode();
    }

    private JsonNode sendJson(HttpRequest request) throws Exception {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String mensagem = extrairMensagem(response.body());
            String detalhe = mensagem.isBlank() ? response.body() : mensagem;
            if (detalhe != null && (detalhe.contains("<!DOCTYPE") || detalhe.contains("<html") || detalhe.contains("<body") || detalhe.contains("502 Bad Gateway") || detalhe.contains("503 Service Temporarily Unavailable") || detalhe.contains("502 bad gateway"))) {
                detalhe = "Serviço temporariamente indisponível. Tente novamente em instantes.";
            }
            throw new BusinessException("WhatsApp service respondeu " + response.statusCode()
                    + " em " + request.uri().getPath()
                    + (detalhe == null || detalhe.isBlank() ? "" : ": " + detalhe));
        }
        String body = response.body();
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(body);
        */
        return objectMapper.createObjectNode();
    }

    private String extrairMensagem(String body) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        try {
            JsonNode node = objectMapper.readTree(body == null ? "{}" : body);
            return texto(node.path("message").asText(node.path("mensagem").asText("")));
        } catch (Exception ex) {
            return "";
        }
        */
        return "";
    }

    private URI baseUri() {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        String value = properties.whatsappServiceUrl();
        if (value.isBlank()) {
            throw new BusinessException("URL do servico de WhatsApp nao configurada.");
        }
        try {
            URI uri = new URI(value.endsWith("/") ? value : value + "/");
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new BusinessException("URL do servico de WhatsApp invalida: " + value);
            }
            return uri;
        } catch (URISyntaxException ex) {
            throw new BusinessException("URL do servico de WhatsApp invalida: " + value);
        }
        */
        return URI.create("http://localhost:0");
    }

    private String detalheErro(Exception ex) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        if (ex instanceof BusinessException businessException) {
            return texto(businessException.getMessage());
        }
        if (ex instanceof TimeoutException) return "timeout";
        if (ex instanceof ConnectException) return "connection refused";
        if (ex instanceof IllegalArgumentException) return "url invalida";
        String message = texto(ex.getMessage());
        if (!message.isBlank()) {
            return message;
        }
        return ex.getClass().getSimpleName();
        */
        return "";
    }

    private String texto(String valor) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        return valor == null ? "" : valor.trim();
        */
        return "";
    }

    private com.minhaempresa.agendapro.whatsapp.enums.WhatsappConnectionStatus mapStatus(String status) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        if ("CONECTADO".equalsIgnoreCase(status) || "CONNECTED".equalsIgnoreCase(status)) return com.minhaempresa.agendapro.whatsapp.enums.WhatsappConnectionStatus.CONNECTED;
        if ("RECONNECTING".equalsIgnoreCase(status)) return com.minhaempresa.agendapro.whatsapp.enums.WhatsappConnectionStatus.RECONNECTING;
        if ("GENERATING_CODE".equalsIgnoreCase(status) || "WAITING_PAIRING".equalsIgnoreCase(status) || "AGUARDANDO".equalsIgnoreCase(status) || "CONNECTING".equalsIgnoreCase(status) || "PAIRING_CODE".equalsIgnoreCase(status)) return com.minhaempresa.agendapro.whatsapp.enums.WhatsappConnectionStatus.CONNECTING;
        if ("PAIRING_FAILED".equalsIgnoreCase(status) || "PAIRING_EXPIRED".equalsIgnoreCase(status) || "SESSION_ERROR".equalsIgnoreCase(status) || "ERROR".equalsIgnoreCase(status)) return com.minhaempresa.agendapro.whatsapp.enums.WhatsappConnectionStatus.ERROR;
        if ("DISCONNECTED".equalsIgnoreCase(status)) return com.minhaempresa.agendapro.whatsapp.enums.WhatsappConnectionStatus.DISCONNECTED;
        if ("CONFIG_PENDING".equalsIgnoreCase(status)) return com.minhaempresa.agendapro.whatsapp.enums.WhatsappConnectionStatus.CONFIG_PENDING;
        return com.minhaempresa.agendapro.whatsapp.enums.WhatsappConnectionStatus.DISCONNECTED;
        */
        return com.minhaempresa.agendapro.whatsapp.enums.WhatsappConnectionStatus.DISCONNECTED;
    }

    private java.time.LocalDateTime toLocalDateTime(String valor) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        try {
            return java.time.Instant.parse(valor).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception ignored) {
            try {
                return java.time.LocalDateTime.parse(valor);
            } catch (Exception ignoredToo) {
                return null;
            }
        }
        */
        return null;
    }

    private String statusLabel(String status) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        return switch (String.valueOf(status).toLowerCase()) {
            case "conectado", "connected" -> "WhatsApp conectado";
            case "reconnecting" -> "Reconectando WhatsApp";
            case "waiting_pairing" -> "Aguardando pareamento";
            case "generating_code" -> "Gerando codigo";
            case "aguardando" -> "Aguardando codigo";
            case "config_pending" -> "Configuracao pendente";
            case "pairing_failed" -> "Pareamento falhou";
            case "pairing_expired" -> "Codigo expirado";
            case "session_error" -> "Sessão inválida";
            case "disconnected" -> "Desconectado";
            case "error" -> "Erro na conexao";
            default -> "Desconectado";
        };
        */
        return "Desconectado";
    }
}

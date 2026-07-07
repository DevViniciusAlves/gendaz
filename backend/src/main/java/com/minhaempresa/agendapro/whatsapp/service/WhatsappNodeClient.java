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
    }

    public WhatsappStatusResponse status(Long empresaId) {
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
    }

    public WhatsappStatusResponse desconectar(Long empresaId) {
        JsonNode node = postJson("/disconnect/" + empresaId, Map.of());
        if (node == null || node.isMissingNode()) {
            node = postJson("/disconnect", Map.of());
        }
        if (node == null || node.isMissingNode()) {
            node = objectMapper.createObjectNode();
        }
        return toStatusResponse(node, empresaId);
    }

    public WhatsappStatusResponse limparSessao(Long empresaId) {
        JsonNode node = deleteJson("/session/" + empresaId);
        if (node == null || node.isMissingNode()) {
            node = objectMapper.createObjectNode();
        }
        return toStatusResponse(node, empresaId);
    }

    public void enviarAgendamento(Map<String, Object> payload) {
        postJson("/webhook/agendamento", payload);
    }

    public void enviarMensagem(Long empresaId, String phone, String message) {
        postJson("/send", Map.of(
                "empresaId", empresaId,
                "phone", phone,
                "message", message
        ));
    }

    public void enviarConfirmacaoPagamentoDono(Map<String, Object> payload) {
        postJson("/payment-owner-reminder", payload);
    }

    public void enviarLembrete(Map<String, Object> payload) {
        postJson("/api/whatsapp/enviar-lembrete", payload);
    }

    private WhatsappStatusResponse toStatusResponse(JsonNode node, Long empresaId) {
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
    }

    private JsonNode postJson(String path, Map<String, Object> body) {
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
    }

    private JsonNode getJson(String path) {
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
    }

    private JsonNode deleteJson(String path) {
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
    }

    private JsonNode sendJson(HttpRequest request) throws Exception {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String mensagem = extrairMensagem(response.body());
            String detalhe = mensagem.isBlank() ? response.body() : mensagem;
            throw new BusinessException("WhatsApp service respondeu " + response.statusCode()
                    + " em " + request.uri().getPath()
                    + (detalhe == null || detalhe.isBlank() ? "" : ": " + detalhe));
        }
        String body = response.body();
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(body);
    }

    private String extrairMensagem(String body) {
        try {
            JsonNode node = objectMapper.readTree(body == null ? "{}" : body);
            return texto(node.path("message").asText(node.path("mensagem").asText("")));
        } catch (Exception ex) {
            return "";
        }
    }

    private URI baseUri() {
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
    }

    private String detalheErro(Exception ex) {
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
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private com.minhaempresa.agendapro.whatsapp.enums.WhatsappConnectionStatus mapStatus(String status) {
        if ("CONECTADO".equalsIgnoreCase(status) || "CONNECTED".equalsIgnoreCase(status)) return com.minhaempresa.agendapro.whatsapp.enums.WhatsappConnectionStatus.CONNECTED;
        if ("RECONNECTING".equalsIgnoreCase(status)) return com.minhaempresa.agendapro.whatsapp.enums.WhatsappConnectionStatus.RECONNECTING;
        if ("GENERATING_CODE".equalsIgnoreCase(status) || "WAITING_PAIRING".equalsIgnoreCase(status) || "AGUARDANDO".equalsIgnoreCase(status) || "CONNECTING".equalsIgnoreCase(status) || "PAIRING_CODE".equalsIgnoreCase(status)) return com.minhaempresa.agendapro.whatsapp.enums.WhatsappConnectionStatus.CONNECTING;
        if ("PAIRING_FAILED".equalsIgnoreCase(status) || "PAIRING_EXPIRED".equalsIgnoreCase(status) || "SESSION_ERROR".equalsIgnoreCase(status) || "ERROR".equalsIgnoreCase(status)) return com.minhaempresa.agendapro.whatsapp.enums.WhatsappConnectionStatus.ERROR;
        if ("DISCONNECTED".equalsIgnoreCase(status)) return com.minhaempresa.agendapro.whatsapp.enums.WhatsappConnectionStatus.DISCONNECTED;
        if ("CONFIG_PENDING".equalsIgnoreCase(status)) return com.minhaempresa.agendapro.whatsapp.enums.WhatsappConnectionStatus.CONFIG_PENDING;
        return com.minhaempresa.agendapro.whatsapp.enums.WhatsappConnectionStatus.DISCONNECTED;
    }

    private java.time.LocalDateTime toLocalDateTime(String valor) {
        try {
            return java.time.Instant.parse(valor).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception ignored) {
            try {
                return java.time.LocalDateTime.parse(valor);
            } catch (Exception ignoredToo) {
                return null;
            }
        }
    }

    private String statusLabel(String status) {
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
    }
}

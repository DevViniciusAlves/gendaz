package com.minhaempresa.agendapro.insights.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhaempresa.agendapro.shared.audit.OutboundTrafficAuditService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class GroqClient {
    private static final URI GROQ_URI = URI.create("https://api.groq.com/openai/v1/chat/completions");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final OutboundTrafficAuditService auditService;
    private final String apiKey;
    private final String model;

    public GroqClient(
            ObjectMapper objectMapper,
            OutboundTrafficAuditService auditService,
            @Value("${groq.api-key:${GROQ_API_KEY:}}") String apiKey,
            @Value("${groq.model:llama-3.3-70b-versatile}") String model
    ) {
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "llama-3.3-70b-versatile" : model.trim();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public boolean disponivel() {
        return !apiKey.isBlank();
    }

    public Optional<String> analisar(String systemPrompt, String userPrompt) {
        auditService.contarExecucao("GroqClient#analisar");
        if (!disponivel()) {
            return Optional.empty();
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("temperature", 0.2);
            payload.put("response_format", Map.of("type", "json_object"));
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            ));

            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(GROQ_URI)
                    .timeout(Duration.ofSeconds(40))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            Instant inicio = Instant.now();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            auditService.registrarHttp(
                    "Groq",
                    auditService.sanitizarBaseUrl(GROQ_URI.toString()),
                    "POST",
                    auditService.origem("GroqClient", "analisar"),
                    auditService.bytesUtf8(body),
                    auditService.headersBytes(Map.of(
                            "Authorization", "Bearer " + apiKey,
                            "Content-Type", "application/json"
                    )),
                    auditService.bytesUtf8(response.body()),
                    Duration.between(inicio, Instant.now()).toMillis(),
                    response.statusCode()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[insights-groq] resposta nao-sucedida status={}", response.statusCode());
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            String content = contentNode.isTextual() ? contentNode.asText() : null;
            return Optional.ofNullable(content);
        } catch (IOException e) {
            log.warn("[insights-groq] falha ao serializar ou ler resposta: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[insights-groq] falha ao analisar: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    public Optional<String> conversar(String systemPrompt, List<Map<String, String>> historico, String userPrompt) {
        auditService.contarExecucao("GroqClient#conversar");
        if (!disponivel()) {
            return Optional.empty();
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("temperature", 0.85);
            payload.put("max_tokens", 450);
            payload.put("top_p", 0.95);
            payload.put("messages", montarMensagens(systemPrompt, historico, userPrompt));

            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(GROQ_URI)
                    .timeout(Duration.ofSeconds(40))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            Instant inicio = Instant.now();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            auditService.registrarHttp(
                    "Groq",
                    auditService.sanitizarBaseUrl(GROQ_URI.toString()),
                    "POST",
                    auditService.origem("GroqClient", "conversar"),
                    auditService.bytesUtf8(body),
                    auditService.headersBytes(Map.of(
                            "Authorization", "Bearer " + apiKey,
                            "Content-Type", "application/json"
                    )),
                    auditService.bytesUtf8(response.body()),
                    Duration.between(inicio, Instant.now()).toMillis(),
                    response.statusCode()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[insights-groq] resposta nao-sucedida status={}", response.statusCode());
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            String content = contentNode.isTextual() ? contentNode.asText() : null;
            return Optional.ofNullable(content);
        } catch (IOException e) {
            log.warn("[insights-groq] falha ao serializar ou ler resposta: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[insights-groq] falha ao conversar: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    private List<Map<String, String>> montarMensagens(String systemPrompt, List<Map<String, String>> historico, String userPrompt) {
        List<Map<String, String>> mensagens = new ArrayList<>();
        mensagens.add(Map.of("role", "system", "content", systemPrompt));

        if (historico != null) {
            for (Map<String, String> mensagem : historico) {
                String role = normalizarRole(mensagem.get("role"));
                String content = mensagem.getOrDefault("content", "").trim();
                if (content.isBlank()) {
                    continue;
                }
                mensagens.add(Map.of("role", role, "content", content));
            }
        }

        mensagens.add(Map.of("role", "user", "content", userPrompt));
        return mensagens;
    }

    private String normalizarRole(String role) {
        String valor = role == null ? "" : role.trim().toLowerCase();
        return switch (valor) {
            case "assistant", "bot", "ia" -> "assistant";
            case "system" -> "system";
            default -> "user";
        };
    }
}

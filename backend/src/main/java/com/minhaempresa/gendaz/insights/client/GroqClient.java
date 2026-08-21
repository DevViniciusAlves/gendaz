package com.minhaempresa.gendaz.insights.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhaempresa.gendaz.shared.audit.OutboundTrafficAuditService;
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
    private final String fallbackModel;

    public GroqClient(
            ObjectMapper objectMapper,
            OutboundTrafficAuditService auditService,
            @Value("${groq.api-key:${GROQ_API_KEY:}}") String apiKey,
            @Value("${groq.model:llama-3.1-8b-instant}") String model,
            @Value("${groq.fallback-model:llama3-8b-8192}") String fallbackModel
    ) {
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "llama-3.1-8b-instant" : model.trim();
        this.fallbackModel = fallbackModel == null || fallbackModel.isBlank() ? "llama3-8b-8192" : fallbackModel.trim();
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
            List<Map<String, String>> mensagens = List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            );
            return enviarChat(mensagens, 0.2, null, Map.of("type", "json_object"), "analisar");
        } catch (IOException e) {
            log.warn("[insights-groq] falha ao serializar ou ler resposta. erroTipo={}", e.getClass().getSimpleName());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[insights-groq] falha ao analisar. erroTipo={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public Optional<String> conversar(String systemPrompt, List<Map<String, String>> historico, String userPrompt) {
        auditService.contarExecucao("GroqClient#conversar");
        if (!disponivel()) {
            return Optional.empty();
        }

        try {
            return enviarChat(montarMensagens(systemPrompt, historico, userPrompt), 0.85, 450, null, "conversar");
        } catch (IOException e) {
            log.warn("[insights-groq] falha ao serializar ou ler resposta. erroTipo={}", e.getClass().getSimpleName());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[insights-groq] falha ao conversar. erroTipo={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<String> enviarChat(List<Map<String, String>> mensagens, double temperature, Integer maxTokens, Map<String, String> responseFormat, String origem) throws IOException, InterruptedException {
        HttpResponse<String> response = enviarChat(model, mensagens, temperature, maxTokens, responseFormat, origem);
        if (response.statusCode() == 404 && !fallbackModel.equals(model)) {
            log.warn("[insights-groq] modelo indisponivel status=404 modelo={} tentandoFallback={}", model, fallbackModel);
            response = enviarChat(fallbackModel, mensagens, temperature, maxTokens, responseFormat, origem + "-fallback");
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("[insights-groq] resposta nao-sucedida status={} corpo={}", response.statusCode(), response.body());
            return Optional.empty();
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        String content = contentNode.isTextual() ? contentNode.asText() : null;
        return Optional.ofNullable(content);
    }

    private HttpResponse<String> enviarChat(String modelo, List<Map<String, String>> mensagens, double temperature, Integer maxTokens, Map<String, String> responseFormat, String origem) throws IOException, InterruptedException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelo);
        payload.put("temperature", temperature);
        payload.put("messages", mensagens);
        if (maxTokens != null) {
            payload.put("max_tokens", maxTokens);
            payload.put("top_p", 0.95);
        }
        if (responseFormat != null) {
            payload.put("response_format", responseFormat);
        }

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
                auditService.origem("GroqClient", origem),
                auditService.bytesUtf8(body),
                auditService.headersBytes(Map.of(
                        "Authorization", "Bearer " + apiKey,
                        "Content-Type", "application/json"
                )),
                auditService.bytesUtf8(response.body()),
                Duration.between(inicio, Instant.now()).toMillis(),
                response.statusCode()
        );
        return response;
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


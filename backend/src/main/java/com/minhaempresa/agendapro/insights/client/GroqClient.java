package com.minhaempresa.agendapro.insights.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    private final String apiKey;
    private final String model;

    public GroqClient(
            ObjectMapper objectMapper,
            @Value("${groq.api-key:${GROQ_API_KEY:}}") String apiKey,
            @Value("${groq.model:llama-3.3-70b-versatile}") String model
    ) {
        this.objectMapper = objectMapper;
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

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[insights-groq] resposta nao-sucedida status={} body={}", response.statusCode(), response.body());
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
        System.out.println("\n========== GROQ DEBUG ==========");
        System.out.println("[GROQ] disponivel(): " + disponivel());
        System.out.println("[GROQ] API Key setada? " + (apiKey != null && !apiKey.isBlank()));
        System.out.println("[GROQ] Model: " + model);
        System.out.println("[GROQ] System prompt length: " + (systemPrompt == null ? 0 : systemPrompt.length()));
        System.out.println("[GROQ] User prompt: " + userPrompt);
        System.out.println("[GROQ] Histórico size: " + (historico == null ? 0 : historico.size()));
        System.out.println("================================\n");

        if (!disponivel()) {
            return Optional.empty();
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("temperature", 0.7);
            payload.put("max_tokens", 500);
            payload.put("top_p", 0.9);
            payload.put("messages", montarMensagens(systemPrompt, historico, userPrompt));

            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(GROQ_URI)
                    .timeout(Duration.ofSeconds(40))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            System.out.println("[GROQ-RESPONSE] Status: " + response.statusCode());
            System.out.println("[GROQ-RESPONSE] Body: " + response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[insights-groq] resposta nao-sucedida status={} body={}", response.statusCode(), response.body());
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            String content = contentNode.isTextual() ? contentNode.asText() : null;
            Optional<String> resultado = Optional.ofNullable(content);
            System.out.println("[GROQ-RESULT] Retornando: " + (resultado.isPresent() ? "SIM" : "VAZIO"));
            return resultado;
        } catch (IOException e) {
            System.out.println("[GROQ-ERROR] " + e.getMessage());
            e.printStackTrace();
            log.warn("[insights-groq] falha ao serializar ou ler resposta: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            System.out.println("[GROQ-ERROR] " + e.getMessage());
            e.printStackTrace();
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

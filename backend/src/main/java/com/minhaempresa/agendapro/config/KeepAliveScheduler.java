package com.minhaempresa.agendapro.config;

import com.minhaempresa.agendapro.whatsapp.service.WhatsappIntegrationProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KeepAliveScheduler {

    private final WhatsappIntegrationProperties whatsappProperties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Scheduled(fixedRate = 5 * 60 * 1000, initialDelay = 30 * 1000)
    public void keepAliveTask() {
        pingWhatsappService();
    }

    private void pingWhatsappService() {
        String baseUrl = whatsappProperties.whatsappServiceUrl();
        if (baseUrl.isBlank()) {
            log.debug("[keep-alive] WHATSAPP_SERVICE_URL nao configurada, ping ignorado");
            return;
        }

        String healthUrl = (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + "health";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("[keep-alive] whatsapp ping ok status={} url={}", response.statusCode(), healthUrl);
            } else {
                log.warn("[keep-alive] whatsapp ping falhou status={} url={}", response.statusCode(), healthUrl);
            }
        } catch (Exception ex) {
            log.warn("[keep-alive] whatsapp ping erro url={} detalhe={}", healthUrl, ex.getMessage());
        }
    }
}

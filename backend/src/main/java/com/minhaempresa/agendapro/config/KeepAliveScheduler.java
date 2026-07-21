package com.minhaempresa.agendapro.config;

//  DESATIVADO — Esta classe é exclusiva para manter vivo o serviço WhatsApp.
//  DESATIVADO — O método keepalive está desativado. Não utilizar em produção.

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
        //  DESATIVADO — pingWhatsappService();
    }

    private void pingWhatsappService() {
        //  DESATIVADO — String baseUrl = whatsappProperties.whatsappServiceUrl();
        //  DESATIVADO — if (baseUrl.isBlank()) {
        //  DESATIVADO —     log.debug("[keep-alive] WHATSAPP_SERVICE_URL nao configurada, ping ignorado");
        //  DESATIVADO —     return;
        //  DESATIVADO — }
        //
        //  DESATIVADO — String healthUrl = (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + "health";
        //  DESATIVADO — try {
        //  DESATIVADO —     HttpRequest request = HttpRequest.newBuilder()
        //  DESATIVADO —             .uri(URI.create(healthUrl))
        //  DESATIVADO —             .timeout(Duration.ofSeconds(15))
        //  DESATIVADO —             .GET()
        //  DESATIVADO —             .build();
        //  DESATIVADO —     HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        //  DESATIVADO —     if (response.statusCode() >= 200 && response.statusCode() < 300) {
        //  DESATIVADO —         log.info("[keep-alive] whatsapp ping ok status={} url={}", response.statusCode(), healthUrl);
        //  DESATIVADO —     } else {
        //  DESATIVADO —         log.warn("[keep-alive] whatsapp ping falhou status={} url={}", response.statusCode(), healthUrl);
        //  DESATIVADO —     }
        //  DESATIVADO — } catch (Exception ex) {
        //  DESATIVADO —     log.warn("[keep-alive] whatsapp ping erro url={} detalhe={}", healthUrl, ex.getMessage());
        //  DESATIVADO — }
    }
}

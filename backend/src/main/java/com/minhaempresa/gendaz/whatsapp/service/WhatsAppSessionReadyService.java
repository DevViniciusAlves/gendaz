package com.minhaempresa.gendaz.whatsapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhaempresa.gendaz.whatsapp.WhatsAppSessionStatus;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class WhatsAppSessionReadyService {

    private final String serviceUrl;
    private final String internalToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;
    private final Duration maxWait;
    private final Sleeper sleeper;

    public WhatsAppSessionReadyService(
            ObjectMapper objectMapper,
            @Value("${whatsapp.service-url:${WHATSAPP_SERVICE_URL:}}") String serviceUrl,
            @Value("${whatsapp.internal-token:${WHATSAPP_INTERNAL_TOKEN:}}") String internalToken) {
        this(objectMapper, serviceUrl, internalToken,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                Duration.ofSeconds(10), Duration.ofSeconds(90),
                d -> Thread.sleep(d.toMillis()));
    }

    WhatsAppSessionReadyService(ObjectMapper objectMapper, String serviceUrl, String internalToken,
                                 HttpClient httpClient, Duration requestTimeout, Duration maxWait, Sleeper sleeper) {
        this.objectMapper = objectMapper;
        this.serviceUrl = serviceUrl == null ? "" : serviceUrl.trim().replaceAll("/+$", "");
        this.internalToken = internalToken == null ? "" : internalToken.trim();
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout == null ? Duration.ofSeconds(10) : requestTimeout;
        this.maxWait = maxWait == null ? Duration.ofSeconds(90) : maxWait;
        this.sleeper = sleeper == null ? d -> Thread.sleep(d.toMillis()) : sleeper;
    }

    public enum ReadyResult { CONNECTED, LOGGED_OUT, TIMEOUT, NOT_CONNECTED }

    public ReadyResult ensureSessionConnected(String companyId) {
        if (serviceUrl.isBlank() || internalToken.isBlank()) return ReadyResult.NOT_CONNECTED;
        Instant deadline = Instant.now().plus(maxWait);
        // backoff sequence 1s,2s,3s,5s,...
        Duration[] seq = { Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3), Duration.ofSeconds(5) };
        int idx = 0;
        while (Instant.now().isBefore(deadline)) {
            String state = fetchState(companyId);
            if (state == null) {
                // transient error -> backoff
            } else if ("CONNECTED".equalsIgnoreCase(state)) {
                return ReadyResult.CONNECTED;
            } else if ("LOGGED_OUT".equalsIgnoreCase(state)) {
                log.warn("[whatsapp-session] empresa={} LOGGED_OUT terminal", companyId);
                return ReadyResult.LOGGED_OUT;
            } else if ("DISCONNECTED".equalsIgnoreCase(state) || "NOT_CONNECTED".equalsIgnoreCase(state)) {
                // allow wait but log
            }
            // wait
            Duration delay = seq[Math.min(idx, seq.length - 1)];
            idx++;
            if (Instant.now().plus(delay).isAfter(deadline)) break;
            try { sleeper.sleep(delay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return ReadyResult.TIMEOUT; } catch (Exception ignored) { return ReadyResult.TIMEOUT; }
        }
        return ReadyResult.TIMEOUT;
    }

    private String fetchState(String companyId) {
        try {
            String url = serviceUrl + "/internal/whatsapp/sessions/" + companyId + "/status";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(requestTimeout)
                    .header("Authorization", "Bearer " + internalToken)
                    .header("Accept", "application/json")
                    .header("Cache-Control", "no-cache")
                    .GET().build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) return null;
            WhatsAppSessionStatus s = objectMapper.readValue(res.body(), WhatsAppSessionStatus.class);
            return s != null ? s.getState() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @FunctionalInterface
    interface Sleeper { void sleep(Duration d) throws InterruptedException; }
}

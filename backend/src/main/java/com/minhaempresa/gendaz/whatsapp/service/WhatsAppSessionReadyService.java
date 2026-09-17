package com.minhaempresa.gendaz.whatsapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhaempresa.gendaz.whatsapp.WhatsAppSessionStatus;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class WhatsAppSessionReadyService {

    private static final Duration MIN_BACKOFF = Duration.ofSeconds(2);

    private final String serviceUrl;
    private final String internalToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;
    private final Duration maxWait;
    private final Sleeper sleeper;

    private final ConcurrentHashMap<String, CompletableFuture<SessionReadyOutcome>> flights = new ConcurrentHashMap<>();

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

    public enum ReadyResult { CONNECTED, LOGGED_OUT, AUTH_ERROR, TIMEOUT, UNAVAILABLE, NOT_CONFIGURED }

    public record SessionReadyOutcome(ReadyResult result, WhatsAppSessionStatus status) {}

    /**
     * Backward compat: returns only ReadyResult.
     */
    public ReadyResult ensureSessionConnected(String companyId) {
        return ensureSessionReady(companyId).result();
    }

    /**
     * Single-flight per companyId using putIfAbsent pattern.
     */
    public SessionReadyOutcome ensureSessionReady(String companyId) {
        if (companyId == null || companyId.isBlank()) {
            return new SessionReadyOutcome(ReadyResult.UNAVAILABLE, null);
        }
        if (serviceUrl.isBlank() || internalToken.isBlank()) {
            return new SessionReadyOutcome(ReadyResult.NOT_CONFIGURED, null);
        }
        String key = companyId.trim();
        if (key.isBlank()) {
            return new SessionReadyOutcome(ReadyResult.UNAVAILABLE, null);
        }
        CompletableFuture<SessionReadyOutcome> created = new CompletableFuture<>();
        CompletableFuture<SessionReadyOutcome> existing = flights.putIfAbsent(key, created);
        if (existing != null) {
            try {
                return existing.get(maxWait.toMillis() + 5000, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                return new SessionReadyOutcome(ReadyResult.TIMEOUT, null);
            } catch (ExecutionException e) {
                return new SessionReadyOutcome(ReadyResult.UNAVAILABLE, null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new SessionReadyOutcome(ReadyResult.TIMEOUT, null);
            }
        }
        // leader
        try {
            SessionReadyOutcome outcome = doPoll(key);
            created.complete(outcome);
            return outcome;
        } catch (Exception e) {
            SessionReadyOutcome fallback = new SessionReadyOutcome(ReadyResult.UNAVAILABLE, null);
            created.complete(fallback);
            return fallback;
        } finally {
            flights.remove(key, created);
        }
    }

    private SessionReadyOutcome doPoll(String companyId) {
        Instant deadline = Instant.now().plus(maxWait);
        Duration[] seq = { Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3), Duration.ofSeconds(5) };
        int idx = 0;
        SessionReadyOutcome lastConnected = null;
        while (Instant.now().isBefore(deadline)) {
            PollOutcome outcome = fetchOnce(companyId);
            switch (outcome.kind) {
                case CONNECTED -> { return new SessionReadyOutcome(ReadyResult.CONNECTED, outcome.status); }
                case LOGGED_OUT -> { log.warn("[whatsapp-session] empresa={} LOGGED_OUT terminal", companyId); return new SessionReadyOutcome(ReadyResult.LOGGED_OUT, outcome.status); }
                case AUTH_ERROR -> { log.warn("[whatsapp-session] empresa={} AUTH_ERROR terminal", companyId); return new SessionReadyOutcome(ReadyResult.AUTH_ERROR, null); }
                case UNAVAILABLE -> { log.warn("[whatsapp-session] empresa={} UNAVAILABLE terminal", companyId); return new SessionReadyOutcome(ReadyResult.UNAVAILABLE, null); }
                case NOT_CONFIGURED -> { return new SessionReadyOutcome(ReadyResult.NOT_CONFIGURED, null); }
                case TIMEOUT -> { return new SessionReadyOutcome(ReadyResult.TIMEOUT, null); }
                case TRANSIENT -> { /* fall through to sleep */ }
            }
            Duration delay = outcome.retryAfter != null ? outcome.retryAfter : seq[Math.min(idx, seq.length - 1)];
            if (outcome.retryAfter == null) idx++;
            long remaining = Duration.between(Instant.now(), deadline).toMillis();
            if (remaining <= 0) break;
            if (delay.toMillis() > remaining) delay = Duration.ofMillis(remaining);
            if (Instant.now().plus(delay).isAfter(deadline)) break;
            try { sleeper.sleep(delay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return new SessionReadyOutcome(ReadyResult.TIMEOUT, null); } catch (Exception ignored) { return new SessionReadyOutcome(ReadyResult.TIMEOUT, null); }
        }
        return new SessionReadyOutcome(ReadyResult.TIMEOUT, null);
    }

    private enum Kind { CONNECTED, LOGGED_OUT, AUTH_ERROR, UNAVAILABLE, NOT_CONFIGURED, TRANSIENT, TIMEOUT }
    private record PollOutcome(Kind kind, Duration retryAfter, WhatsAppSessionStatus status) {}

    private PollOutcome fetchOnce(String companyId) {
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
            int status = res.statusCode();
            if (status >= 200 && status < 300) {
                try {
                    WhatsAppSessionStatus s = objectMapper.readValue(res.body(), WhatsAppSessionStatus.class);
                    String state = s != null ? s.getState() : null;
                    if ("CONNECTED".equalsIgnoreCase(state)) return new PollOutcome(Kind.CONNECTED, null, s);
                    if ("LOGGED_OUT".equalsIgnoreCase(state)) return new PollOutcome(Kind.LOGGED_OUT, null, s);
                    if ("CONNECTING".equalsIgnoreCase(state) || "RECONNECTING".equalsIgnoreCase(state)
                            || "DISCONNECTED".equalsIgnoreCase(state) || "NOT_CONNECTED".equalsIgnoreCase(state)) {
                        return new PollOutcome(Kind.TRANSIENT, null, null);
                    }
                    return new PollOutcome(Kind.TRANSIENT, null, null);
                } catch (Exception e) {
                    return new PollOutcome(Kind.TRANSIENT, null, null);
                }
            }
            if (status == 401 || status == 403) return new PollOutcome(Kind.AUTH_ERROR, null, null);
            if (status == 404) return new PollOutcome(Kind.UNAVAILABLE, null, null);
            if (status == 429) {
                String ra = res.headers().firstValue("Retry-After").orElse(null);
                Duration d = ra != null ? parseRetryAfter(ra).orElse(null) : null;
                return new PollOutcome(Kind.TRANSIENT, d, null);
            }
            if (status == 502 || status == 503 || status == 504 || (status >= 500 && status < 600)) {
                return new PollOutcome(Kind.TRANSIENT, null, null);
            }
            if (status >= 400 && status < 500) return new PollOutcome(Kind.UNAVAILABLE, null, null);
            return new PollOutcome(Kind.TRANSIENT, null, null);
        } catch (java.net.http.HttpTimeoutException e) {
            return new PollOutcome(Kind.TRANSIENT, null, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new PollOutcome(Kind.TIMEOUT, null, null);
        } catch (Exception e) {
            // ConnectException, UnknownHost, IOException etc -> TRANSIENT
            return new PollOutcome(Kind.TRANSIENT, null, null);
        }
    }

    static Optional<Duration> parseRetryAfter(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String v = raw.trim();
        try { long sec = Long.parseLong(v); if (sec <= 0) return Optional.of(MIN_BACKOFF); return Optional.of(Duration.ofSeconds(sec)); } catch (NumberFormatException ignored) {}
        try { java.time.ZonedDateTime zdt = java.time.ZonedDateTime.parse(v, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME); long diff = Duration.between(Instant.now(), zdt.toInstant()).toMillis(); if (diff <= 0) return Optional.of(MIN_BACKOFF); return Optional.of(Duration.ofMillis(diff)); } catch (Exception ignored) {}
        return Optional.empty();
    }

    @FunctionalInterface
    interface Sleeper { void sleep(Duration d) throws InterruptedException; }

    // package-private for tests: validate flights removidos
    int activeFlights() { return flights.size(); }
}

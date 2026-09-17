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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;
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

    private final ConcurrentHashMap<String, CompletableFuture<ReadyResult>> flights = new ConcurrentHashMap<>();

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

    /**
     * Garante sessão CONNECTED com single-flight por empresa.
     */
    public ReadyResult ensureSessionConnected(String companyId) {
        if (serviceUrl.isBlank() || internalToken.isBlank()) return ReadyResult.NOT_CONFIGURED;
        String key = companyId.trim();
        CompletableFuture<ReadyResult> future = flights.compute(key, (k, existing) -> {
            if (existing != null && !existing.isDone()) return existing;
            CompletableFuture<ReadyResult> f = new CompletableFuture<>();
            // run polling async-like but in current thread via supply; we just start it synchronously inside leader
            // To avoid blocking compute, launch polling in same thread after compute returns
            return f;
        });
        // If we are the leader (future not yet completed and no polling started), do polling
        // Detect leader by checking if future is newly created and not yet running
        // Use atomic check: if future is not done and we try to claim polling, the first caller wins
        // Simpler: synchronize per key polling
        synchronized (getLock(key)) {
            CompletableFuture<ReadyResult> current = flights.get(key);
            if (current == future && !current.isDone() && !isPolling(current)) {
                markPolling(current);
                try {
                    ReadyResult r = doPoll(key);
                    current.complete(r);
                } catch (Exception e) {
                    current.complete(ReadyResult.UNAVAILABLE);
                }
            }
        }
        try {
            return future.get(maxWait.toMillis() + 5000, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return ReadyResult.TIMEOUT;
        } catch (ExecutionException e) {
            return ReadyResult.UNAVAILABLE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ReadyResult.TIMEOUT;
        }
    }

    // Lightweight per-key lock objects
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private Object getLock(String key) { return locks.computeIfAbsent(key, k -> new Object()); }
    private final Set<CompletableFuture<ReadyResult>> pollingSet = ConcurrentHashMap.newKeySet();
    private boolean isPolling(CompletableFuture<ReadyResult> f) { return pollingSet.contains(f); }
    private void markPolling(CompletableFuture<ReadyResult> f) { pollingSet.add(f); }

    private ReadyResult doPoll(String companyId) {
        Instant deadline = Instant.now().plus(maxWait);
        Duration[] seq = { Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3), Duration.ofSeconds(5) };
        int idx = 0;
        while (Instant.now().isBefore(deadline)) {
            PollOutcome outcome = fetchOnce(companyId);
            switch (outcome.kind) {
                case CONNECTED -> { return ReadyResult.CONNECTED; }
                case LOGGED_OUT -> { log.warn("[whatsapp-session] empresa={} LOGGED_OUT terminal", companyId); return ReadyResult.LOGGED_OUT; }
                case AUTH_ERROR -> { log.warn("[whatsapp-session] empresa={} AUTH_ERROR terminal", companyId); return ReadyResult.AUTH_ERROR; }
                case UNAVAILABLE -> { log.warn("[whatsapp-session] empresa={} UNAVAILABLE terminal", companyId); return ReadyResult.UNAVAILABLE; }
                case NOT_CONFIGURED -> { return ReadyResult.NOT_CONFIGURED; }
                case TRANSIENT -> { /* fall through to sleep */ }
            }
            Duration delay = outcome.retryAfter != null ? outcome.retryAfter : seq[Math.min(idx, seq.length - 1)];
            if (outcome.retryAfter == null) idx++;
            // cap by remaining
            long remaining = Duration.between(Instant.now(), deadline).toMillis();
            if (remaining <= 0) break;
            if (delay.toMillis() > remaining) delay = Duration.ofMillis(remaining);
            if (Instant.now().plus(delay).isAfter(deadline)) break;
            try { sleeper.sleep(delay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return ReadyResult.TIMEOUT; } catch (Exception ignored) { return ReadyResult.TIMEOUT; }
        }
        return ReadyResult.TIMEOUT;
    }

    private enum Kind { CONNECTED, LOGGED_OUT, AUTH_ERROR, UNAVAILABLE, NOT_CONFIGURED, TRANSIENT }
    private record PollOutcome(Kind kind, Duration retryAfter) {}

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
                    if ("CONNECTED".equalsIgnoreCase(state)) return new PollOutcome(Kind.CONNECTED, null);
                    if ("LOGGED_OUT".equalsIgnoreCase(state)) return new PollOutcome(Kind.LOGGED_OUT, null);
                    if ("CONNECTING".equalsIgnoreCase(state) || "RECONNECTING".equalsIgnoreCase(state)
                            || "DISCONNECTED".equalsIgnoreCase(state) || "NOT_CONNECTED".equalsIgnoreCase(state)) {
                        return new PollOutcome(Kind.TRANSIENT, null);
                    }
                    return new PollOutcome(Kind.TRANSIENT, null);
                } catch (Exception e) {
                    return new PollOutcome(Kind.TRANSIENT, null);
                }
            }
            if (status == 401 || status == 403) return new PollOutcome(Kind.AUTH_ERROR, null);
            if (status == 404) return new PollOutcome(Kind.UNAVAILABLE, null);
            if (status == 429) {
                String ra = res.headers().firstValue("Retry-After").orElse(null);
                Duration d = ra != null ? parseRetryAfter(ra).orElse(null) : null;
                return new PollOutcome(Kind.TRANSIENT, d);
            }
            if (status == 502 || status == 503 || status == 504 || (status >= 500 && status < 600)) {
                return new PollOutcome(Kind.TRANSIENT, null);
            }
            if (status >= 400 && status < 500) return new PollOutcome(Kind.UNAVAILABLE, null);
            return new PollOutcome(Kind.TRANSIENT, null);
        } catch (java.net.http.HttpTimeoutException e) {
            return new PollOutcome(Kind.TRANSIENT, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new PollOutcome(Kind.TRANSIENT, null);
        } catch (Exception e) {
            return new PollOutcome(Kind.TRANSIENT, null);
        }
    }

    static Optional<Duration> parseRetryAfter(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String v = raw.trim();
        try { long sec = Long.parseLong(v); if (sec < 0) return Optional.empty(); if (sec > 300) sec = 300; return Optional.of(Duration.ofSeconds(sec)); } catch (NumberFormatException ignored) {}
        try { java.time.ZonedDateTime zdt = java.time.ZonedDateTime.parse(v, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME); long diff = Duration.between(Instant.now(), zdt.toInstant()).toMillis(); if (diff <= 0) return Optional.of(Duration.ofSeconds(2)); return Optional.of(Duration.ofMillis(diff)); } catch (Exception ignored) {}
        return Optional.empty();
    }

    @FunctionalInterface
    interface Sleeper { void sleep(Duration d) throws InterruptedException; }
}

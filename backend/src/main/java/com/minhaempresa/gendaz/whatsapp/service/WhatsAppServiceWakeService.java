package com.minhaempresa.gendaz.whatsapp.service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Garantia centralizada de disponibilidade do whatsapp-service (on-demand).
 *
 * <p>Corrige: single-flight real (sem pre-check fora do lock), sem dupla request,
 * exponential backoff com jitter, Retry-After, janela 150s, headers explicitos,
 * logs diagnosticos seguros e body limitado. /health nunca envia Authorization.
 */
@Service
@Slf4j
public class WhatsAppServiceWakeService implements DisposableBean {

    private static final Set<Integer> NON_TRANSIENT_STATUS = Set.of(400, 401, 403, 404);
    // retryable inclui 429 + 5xx; outros 5xx tambem sao retryable ate o deadline
    private static final Duration DEFAULT_MAX_DURATION = Duration.ofSeconds(150);
    private static final Duration MIN_BACKOFF = Duration.ofSeconds(2);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(4);
    private static final int MAX_BODY_LOG = 300;

    private final String serviceUrl;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final Duration maxDuration;
    private final Sleeper sleeper;
    private final ExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private final Object flightLock = new Object();
    private final AtomicReference<CompletableFuture<Void>> flight = new AtomicReference<>();

    @org.springframework.beans.factory.annotation.Autowired
    public WhatsAppServiceWakeService(
            @Value("${whatsapp.service-url:${WHATSAPP_SERVICE_URL:}}") String serviceUrl) {
        this(serviceUrl, Duration.ofSeconds(5), Duration.ofSeconds(10),
                Duration.ofSeconds(150),
                duration -> Thread.sleep(duration.toMillis()),
                createExecutor());
    }

    WhatsAppServiceWakeService(
            String serviceUrl,
            Duration connectTimeout,
            Duration requestTimeout,
            Duration maxDuration,
            Sleeper sleeper,
            ExecutorService executor) {
        this(serviceUrl, buildClient(connectTimeout), requestTimeout, maxDuration, sleeper, executor);
    }

    // backward compat for tests using 7-arg with retryInterval
    WhatsAppServiceWakeService(
            String serviceUrl,
            Duration connectTimeout,
            Duration requestTimeout,
            Duration retryIntervalIgnored,
            Duration maxDuration,
            Sleeper sleeper,
            ExecutorService executor) {
        this(serviceUrl, buildClient(connectTimeout), requestTimeout, maxDuration, sleeper, executor);
    }

    // Test-only constructor with injected HttpClient
    WhatsAppServiceWakeService(
            String serviceUrl,
            HttpClient httpClient,
            Duration requestTimeout,
            Duration retryIntervalIgnored,
            Duration maxDuration,
            Sleeper sleeper,
            ExecutorService executor) {
        this(serviceUrl, httpClient, requestTimeout, maxDuration, sleeper, executor);
    }

    private WhatsAppServiceWakeService(
            String serviceUrl,
            HttpClient httpClient,
            Duration requestTimeout,
            Duration maxDuration,
            Sleeper sleeper,
            ExecutorService executor) {
        this.serviceUrl = serviceUrl == null ? "" : serviceUrl.trim().replaceAll("/+$", "");
        this.requestTimeout = requestTimeout == null ? Duration.ofSeconds(10) : requestTimeout;
        this.maxDuration = maxDuration == null ? DEFAULT_MAX_DURATION : maxDuration;
        this.sleeper = sleeper == null ? duration -> Thread.sleep(duration.toMillis()) : sleeper;
        this.executor = executor == null ? createExecutor() : executor;
        this.httpClient = httpClient != null ? httpClient : buildClient(Duration.ofSeconds(5));
    }

    private static HttpClient buildClient(Duration connectTimeout) {
        Duration ct = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        return HttpClient.newBuilder().connectTimeout(ct).build();
    }

    private static ExecutorService createExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "whatsapp-wake");
            t.setDaemon(true);
            return t;
        });
    }

    public void wakeAsync() {
        if (!started.compareAndSet(false, true)) {
            log.debug("[whatsapp-availability] wake ja iniciado, ignorando chamada duplicada");
            return;
        }
        if (serviceUrl.isBlank()) {
            log.warn("[whatsapp-availability] whatsapp service url nao configurada, wake ignorado");
            return;
        }
        log.info("[whatsapp-availability] iniciando wake do servico");
        try {
            executor.submit(this::doWake);
        } catch (Exception e) {
            log.warn("[whatsapp-availability] falha ao submeter wake. erroTipo={}", e.getClass().getSimpleName());
        }
    }

    public void ensureAvailable() {
        if (serviceUrl.isBlank()) {
            throw new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.NOT_CONFIGURED, "whatsapp service url nao configurada");
        }
        // Single-flight protege TUDO: nao ha checkHealthOnce fora do lock
        CompletableFuture<Void> myFuture;
        boolean isLeader = false;
        synchronized (flightLock) {
            CompletableFuture<Void> existing = flight.get();
            if (existing != null && !existing.isDone()) {
                myFuture = existing;
                log.info("[whatsapp-availability] wake ja em andamento, aguardando");
            } else {
                // Se future anterior terminou com sucesso recentemente, podemos tentar fast-path?
                // Para nao criar rajada, leader sempre faz a verificacao. Seguidores esperam.
                // Se ultimo foi sucesso e ainda dentro de janela muito curta, poderiamos retornar direto,
                // mas simplicidade: sempre criar novo future e deixar leader verificar.
                // O custo quando READY e 1 request rapida (~200ms).
                myFuture = new CompletableFuture<>();
                flight.set(myFuture);
                isLeader = true;
            }
        }
        if (isLeader) {
            try {
                doWakeBlocking(myFuture);
            } catch (Throwable t) {
                if (!myFuture.isDone()) {
                    myFuture.completeExceptionally(t);
                }
            }
        }
        try {
            myFuture.get(maxDuration.toMillis() + 5000, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.TIMEOUT, "timeout aguardando WPP READY");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof WhatsAppAvailabilityException wae) {
                throw wae;
            }
            throw new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.UNAVAILABLE, cause != null ? cause.getMessage() : "unavailable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.UNAVAILABLE, "interrompido");
        }
    }

    private void doWakeBlocking(CompletableFuture<Void> future) {
        Instant deadline = Instant.now().plus(maxDuration);
        Instant start = Instant.now();
        int tentativa = 0;
        String healthUrl;
        try {
            healthUrl = serviceUrl + "/health";
            URI.create(healthUrl);
        } catch (IllegalArgumentException e) {
            log.warn("[whatsapp-availability] url invalida, abortando wake");
            future.completeExceptionally(new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.NOT_CONFIGURED, "url invalida"));
            return;
        }

        while (Instant.now().isBefore(deadline)) {
            tentativa++;
            if (Thread.currentThread().isInterrupted()) {
                log.info("[whatsapp-availability] wake interrompido");
                future.completeExceptionally(new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.UNAVAILABLE, "interrompido"));
                return;
            }
            if (executor.isShutdown()) {
                log.info("[whatsapp-availability] executor em shutdown, abortando");
                future.completeExceptionally(new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.UNAVAILABLE, "executor shutdown"));
                return;
            }
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(healthUrl))
                        .timeout(requestTimeout)
                        .header("Accept", "application/json")
                        .header("User-Agent", "Gendaz-Stage-Wake/1.0")
                        .header("Cache-Control", "no-cache")
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                String httpVersion = response.version() != null ? response.version().name() : "unknown";
                String server = response.headers().firstValue("server").orElse("-");
                String cfRay = response.headers().firstValue("cf-ray").orElse("-");
                String rndrId = response.headers().firstValue("x-render-routing").orElse(
                        response.headers().firstValue("rndr-id").orElse("-"));
                String retryAfterRaw = response.headers().firstValue("Retry-After").orElse(null);
                String bodySnippet = snippet(response.body());
                long elapsed = Duration.between(start, Instant.now()).toMillis();

                if (status >= 200 && status < 300) {
                    log.info("[whatsapp-availability] WPP READY tentativa={} status={} httpVersion={} elapsedMs={}", tentativa, status, httpVersion, elapsed);
                    future.complete(null);
                    return;
                }
                if (status == 401 || status == 403) {
                    log.warn("[whatsapp-availability] auth_error tentativa={} status={} httpVersion={} server={} elapsedMs={}", tentativa, status, httpVersion, server, elapsed);
                    future.completeExceptionally(new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.AUTH_ERROR, "auth error " + status));
                    return;
                }
                if (NON_TRANSIENT_STATUS.contains(status)) {
                    log.warn("[whatsapp-availability] resposta nao transitoria tentativa={} status={} httpVersion={} server={} elapsedMs={} body={}", tentativa, status, httpVersion, server, elapsed, bodySnippet);
                    future.completeExceptionally(new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.UNAVAILABLE, "non transient " + status));
                    return;
                }
                if (status >= 400 && status < 500 && status != 429) {
                    log.warn("[whatsapp-availability] resposta nao transitoria tentativa={} status={} httpVersion={} server={} elapsedMs={} body={}", tentativa, status, httpVersion, server, elapsed, bodySnippet);
                    future.completeExceptionally(new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.UNAVAILABLE, "client error " + status));
                    return;
                }
                // transient: 429, 502, 503, 504, 5xx
                Duration nextDelay = computeDelay(tentativa, retryAfterRaw, status);
                // cap by remaining time
                long remainingMs = Duration.between(Instant.now(), deadline).toMillis();
                if (remainingMs <= 0) break;
                if (nextDelay.toMillis() > remainingMs) {
                    nextDelay = Duration.ofMillis(remainingMs);
                }
                log.warn("[whatsapp-availability] tentativa={} status={} httpVersion={} retryAfter={} server={} cfRay={} rndrId={} elapsedMs={} nextRetryMs={} body={}",
                        tentativa, status, httpVersion, retryAfterRaw != null ? retryAfterRaw : "-", server, cfRay, rndrId, elapsed, nextDelay.toMillis(), bodySnippet);

                if (Instant.now().plus(nextDelay).isAfter(deadline)) {
                    break;
                }
                sleeper.sleep(nextDelay);
                continue;

            } catch (HttpTimeoutException e) {
                long elapsed = Duration.between(start, Instant.now()).toMillis();
                Duration nextDelay = computeDelay(tentativa, null, -1);
                log.warn("[whatsapp-availability] tentativa={} status=timeout httpVersion=- elapsedMs={} nextRetryMs={}", tentativa, elapsed, nextDelay.toMillis());
                if (Instant.now().plus(nextDelay).isAfter(deadline)) break;
                try { sleeper.sleep(nextDelay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); future.completeExceptionally(new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.UNAVAILABLE, "interrompido")); return; } catch (Exception ex) { future.completeExceptionally(new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.UNAVAILABLE, "sleep fail")); return; }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("[whatsapp-availability] wake interrompido");
                future.completeExceptionally(new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.UNAVAILABLE, "interrompido"));
                return;
            } catch (ConnectException e) {
                long elapsed = Duration.between(start, Instant.now()).toMillis();
                Duration nextDelay = computeDelay(tentativa, null, -1);
                log.warn("[whatsapp-availability] tentativa={} status=connect_error elapsedMs={} nextRetryMs={}", tentativa, elapsed, nextDelay.toMillis());
                if (Instant.now().plus(nextDelay).isAfter(deadline)) break;
                try { sleeper.sleep(nextDelay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); future.completeExceptionally(new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.UNAVAILABLE, "interrompido")); return; } catch (Exception ex) { future.completeExceptionally(new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.UNAVAILABLE, "sleep fail")); return; }
            } catch (IOException e) {
                Throwable cause = e.getCause();
                boolean isConnectFailure = e instanceof ConnectException
                        || cause instanceof ConnectException
                        || e instanceof java.net.UnknownHostException
                        || cause instanceof java.net.UnknownHostException;
                long elapsed = Duration.between(start, Instant.now()).toMillis();
                Duration nextDelay = computeDelay(tentativa, null, -1);
                if (isConnectFailure) {
                    log.warn("[whatsapp-availability] tentativa={} status=connect_error elapsedMs={} nextRetryMs={}", tentativa, elapsed, nextDelay.toMillis());
                } else {
                    log.warn("[whatsapp-availability] tentativa={} status=io_error elapsedMs={} nextRetryMs={} erroTipo={}", tentativa, elapsed, nextDelay.toMillis(), e.getClass().getSimpleName());
                }
                if (Instant.now().plus(nextDelay).isAfter(deadline)) break;
                try { sleeper.sleep(nextDelay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); future.completeExceptionally(new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.UNAVAILABLE, "interrompido")); return; } catch (Exception ex) { future.completeExceptionally(new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.UNAVAILABLE, "sleep fail")); return; }
            } catch (Exception e) {
                long elapsed = Duration.between(start, Instant.now()).toMillis();
                Duration nextDelay = computeDelay(tentativa, null, -1);
                log.warn("[whatsapp-availability] tentativa={} status=error elapsedMs={} nextRetryMs={} erroTipo={}", tentativa, elapsed, nextDelay.toMillis(), e.getClass().getSimpleName());
                if (Instant.now().plus(nextDelay).isAfter(deadline)) break;
                try { sleeper.sleep(nextDelay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); future.completeExceptionally(new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.UNAVAILABLE, "interrompido")); return; } catch (Exception ex) { future.completeExceptionally(new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.UNAVAILABLE, "sleep fail")); return; }
            }
        }
        log.warn("[whatsapp-availability] timeout tentativa={} - servico nao ficou disponivel elapsedMs={}", tentativa, Duration.between(start, Instant.now()).toMillis());
        future.completeExceptionally(new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.TIMEOUT, "timeout apos " + tentativa + " tentativas"));
    }

    Duration computeDelay(int attempt, String retryAfterRaw, int status) {
        // 429 with valid Retry-After: respeitar exatamente o servidor, sem jitter que reduza,
        // limitado apenas pelo deadline global (feito no caller)
        if (status == 429 && retryAfterRaw != null) {
            Optional<Duration> parsed = parseRetryAfter(retryAfterRaw);
            if (parsed.isPresent()) {
                Duration d = parsed.get();
                if (d.isNegative() || d.isZero()) d = MIN_BACKOFF;
                // NAO limitar a MAX_BACKOFF, NAO aplicar jitter que reduza o tempo
                return d;
            }
        }
        // exponential backoff: BASE * 2^(attempt-1) capped
        long baseMs = BASE_BACKOFF.toMillis();
        long exp = baseMs * (1L << Math.min(attempt - 1, 6));
        long capped = Math.min(exp, MAX_BACKOFF.toMillis());
        // ensure at least MIN
        capped = Math.max(capped, MIN_BACKOFF.toMillis());
        return withJitter(Duration.ofMillis(capped));
    }

    private Duration withJitter(Duration d) {
        long ms = d.toMillis();
        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1, ms / 4));
        // +/- up to 25% jitter, but keep positive
        boolean add = ThreadLocalRandom.current().nextBoolean();
        long result = add ? ms + jitter : Math.max(MIN_BACKOFF.toMillis(), ms - jitter);
        return Duration.ofMillis(result);
    }

    static Optional<Duration> parseRetryAfter(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String v = raw.trim();
        // seconds
        try {
            long sec = Long.parseLong(v);
            if (sec < 0) return Optional.empty();
            if (sec > 300) sec = 300;
            return Optional.of(Duration.ofSeconds(sec));
        } catch (NumberFormatException ignored) {}
        // HTTP-date
        try {
            Instant date = Instant.parse(v);
            long diff = Duration.between(Instant.now(), date).toMillis();
            if (diff <= 0) return Optional.of(MIN_BACKOFF);
            return Optional.of(Duration.ofMillis(diff));
        } catch (DateTimeParseException ignored) {}
        try {
            java.time.ZonedDateTime zdt = java.time.ZonedDateTime.parse(v, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME);
            long diff = Duration.between(Instant.now(), zdt.toInstant()).toMillis();
            if (diff <= 0) return Optional.of(MIN_BACKOFF);
            return Optional.of(Duration.ofMillis(diff));
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    private String snippet(String body) {
        if (body == null || body.isBlank()) return "-";
        String s = body.trim().replaceAll("\\s+", " ");
        if (s.length() > MAX_BODY_LOG) s = s.substring(0, MAX_BODY_LOG) + "...";
        return s;
    }

    void doWake() {
        if (serviceUrl.isBlank()) {
            log.warn("[whatsapp-availability] whatsapp service url nao configurada, wake ignorado");
            return;
        }
        CompletableFuture<Void> f = new CompletableFuture<>();
        doWakeBlocking(f);
        try { f.get(1, TimeUnit.MILLISECONDS); } catch (Exception ignored) {}
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    public enum WhatsAppAvailabilityReason {
        TIMEOUT, UNAVAILABLE, AUTH_ERROR, NOT_CONFIGURED
    }

    public static class WhatsAppAvailabilityException extends RuntimeException {
        private final WhatsAppAvailabilityReason reason;
        public WhatsAppAvailabilityException(WhatsAppAvailabilityReason reason, String msg) {
            super(msg);
            this.reason = reason;
        }
        public WhatsAppAvailabilityReason getReason() { return reason; }
    }
}

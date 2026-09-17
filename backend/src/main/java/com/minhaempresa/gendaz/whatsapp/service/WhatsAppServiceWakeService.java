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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
 * <p>Conceito: {@code ensureAvailable()} verifica /health; se WPP dormindo
 * dispara wake (GET /health que provoca cold start no Render) e aguarda até
 * READY com timeout limitado. Concorrência via single-flight: N requests
 * simultâneas compartilham 1 sequência de wake.
 */
@Service
@Slf4j
public class WhatsAppServiceWakeService implements DisposableBean {

    private static final Set<Integer> RETRYABLE_STATUS = Set.of(429, 502, 503, 504);
    private static final Set<Integer> NON_TRANSIENT_STATUS = Set.of(400, 401, 403, 404);

    private final String serviceUrl;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final Duration retryInterval;
    private final Duration maxDuration;
    private final Sleeper sleeper;
    private final ExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean(false);

    // single-flight
    private final Object flightLock = new Object();
    private final AtomicReference<CompletableFuture<Void>> flight = new AtomicReference<>();

    @org.springframework.beans.factory.annotation.Autowired
    public WhatsAppServiceWakeService(
            @Value("${whatsapp.service-url:${WHATSAPP_SERVICE_URL:}}") String serviceUrl) {
        this(serviceUrl, Duration.ofSeconds(5), Duration.ofSeconds(10),
                Duration.ofSeconds(5), Duration.ofSeconds(90),
                duration -> Thread.sleep(duration.toMillis()),
                createExecutor());
    }

    WhatsAppServiceWakeService(
            String serviceUrl,
            Duration connectTimeout,
            Duration requestTimeout,
            Duration retryInterval,
            Duration maxDuration,
            Sleeper sleeper,
            ExecutorService executor) {
        this.serviceUrl = serviceUrl == null ? "" : serviceUrl.trim().replaceAll("/+$", "");
        this.requestTimeout = requestTimeout == null ? Duration.ofSeconds(10) : requestTimeout;
        this.retryInterval = retryInterval == null ? Duration.ofSeconds(5) : retryInterval;
        this.maxDuration = maxDuration == null ? Duration.ofSeconds(90) : maxDuration;
        this.sleeper = sleeper == null ? duration -> Thread.sleep(duration.toMillis()) : sleeper;
        this.executor = executor == null ? createExecutor() : executor;
        Duration ct = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        this.httpClient = HttpClient.newBuilder().connectTimeout(ct).build();
    }

    // Test-only constructor with injected HttpClient
    WhatsAppServiceWakeService(
            String serviceUrl,
            HttpClient httpClient,
            Duration requestTimeout,
            Duration retryInterval,
            Duration maxDuration,
            Sleeper sleeper,
            ExecutorService executor) {
        this.serviceUrl = serviceUrl == null ? "" : serviceUrl.trim().replaceAll("/+$", "");
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout == null ? Duration.ofSeconds(10) : requestTimeout;
        this.retryInterval = retryInterval == null ? Duration.ofSeconds(5) : retryInterval;
        this.maxDuration = maxDuration == null ? Duration.ofSeconds(90) : maxDuration;
        this.sleeper = sleeper == null ? duration -> Thread.sleep(duration.toMillis()) : sleeper;
        this.executor = executor == null ? createExecutor() : executor;
    }

    private static ExecutorService createExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "whatsapp-wake");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Dispara wake de forma assincrona, garantindo no maximo uma execucao por boot.
     * Usado pelo startup listener. Nunca propaga excecao e nunca bloqueia.
     */
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

    /**
     * Garante que WPP está disponível. Centralizado: todas operações que precisam
     * do WPP devem chamar este método antes do provider.
     *
     * @throws WhatsAppAvailabilityException se não ficar READY dentro do timeout
     */
    public void ensureAvailable() {
        if (serviceUrl.isBlank()) {
            throw new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.NOT_CONFIGURED, "whatsapp service url nao configurada");
        }
        // Fast path: já está ready?
        HealthResult fast = checkHealthOnce();
        if (fast == HealthResult.READY) {
            log.debug("[whatsapp-availability] verificando disponibilidade -> READY");
            return;
        }
        if (fast == HealthResult.AUTH_ERROR) {
            log.warn("[whatsapp-availability] auth_error ao verificar disponibilidade");
            throw new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.AUTH_ERROR, "auth error no health");
        }
        // Precisa acordar -> single-flight
        log.info("[whatsapp-availability] WPP indisponivel, iniciando wake");
        CompletableFuture<Void> myFuture;
        boolean isLeader = false;
        synchronized (flightLock) {
            CompletableFuture<Void> existing = flight.get();
            if (existing != null && !existing.isDone()) {
                myFuture = existing;
                log.info("[whatsapp-availability] wake ja em andamento, aguardando");
            } else {
                myFuture = new CompletableFuture<>();
                flight.set(myFuture);
                isLeader = true;
            }
        }
        if (isLeader) {
            try {
                doWakeBlocking(myFuture);
            } catch (Throwable t) {
                // já tratado no completeExceptionally
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
                        .GET()
                        .build();
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    log.info("[whatsapp-availability] WPP READY tentativa={} status={}", tentativa, status);
                    future.complete(null);
                    return;
                }
                if (status == 401 || status == 403) {
                    log.warn("[whatsapp-availability] auth_error tentativa={} status={}", tentativa, status);
                    future.completeExceptionally(new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.AUTH_ERROR, "auth error " + status));
                    return;
                }
                if (NON_TRANSIENT_STATUS.contains(status)) {
                    log.warn("[whatsapp-availability] resposta nao transitoria tentativa={} status={} abortando", tentativa, status);
                    future.completeExceptionally(new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.UNAVAILABLE, "non transient " + status));
                    return;
                }
                if (RETRYABLE_STATUS.contains(status) || (status >= 500 && status < 600)) {
                    log.warn("[whatsapp-availability] tentativa={} status={}", tentativa, status);
                } else {
                    if (status >= 400 && status < 500) {
                        log.warn("[whatsapp-availability] resposta nao transitoria tentativa={} status={} abortando", tentativa, status);
                        future.completeExceptionally(new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.UNAVAILABLE, "client error " + status));
                        return;
                    }
                    log.warn("[whatsapp-availability] tentativa={} status={}", tentativa, status);
                }
            } catch (HttpTimeoutException e) {
                log.warn("[whatsapp-availability] tentativa={} timeout", tentativa);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("[whatsapp-availability] wake interrompido");
                future.completeExceptionally(new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.UNAVAILABLE, "interrompido"));
                return;
            } catch (ConnectException e) {
                log.warn("[whatsapp-availability] tentativa={} status=connect_error", tentativa);
            } catch (IOException e) {
                Throwable cause = e.getCause();
                boolean isConnectFailure = e instanceof ConnectException
                        || cause instanceof ConnectException
                        || e instanceof java.net.UnknownHostException
                        || cause instanceof java.net.UnknownHostException;
                if (isConnectFailure) {
                    log.warn("[whatsapp-availability] tentativa={} status=connect_error", tentativa);
                } else {
                    log.warn("[whatsapp-availability] tentativa={} status=io_error", tentativa);
                }
            } catch (Exception e) {
                log.warn("[whatsapp-availability] tentativa={} erroTipo={}", tentativa, e.getClass().getSimpleName());
            }

            if (Instant.now().plus(retryInterval).isAfter(deadline)) {
                break;
            }
            try {
                sleeper.sleep(retryInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("[whatsapp-availability] wake interrompido durante sleep");
                future.completeExceptionally(new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.UNAVAILABLE, "interrompido sleep"));
                return;
            } catch (Exception e) {
                log.warn("[whatsapp-availability] falha no sleep tentativa={} erroTipo={}", tentativa, e.getClass().getSimpleName());
                future.completeExceptionally(new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.UNAVAILABLE, "sleep fail"));
                return;
            }
        }
        log.warn("[whatsapp-availability] timeout tentativa={} - servico nao ficou disponivel", tentativa);
        future.completeExceptionally(new WhatsAppAvailabilityException(WhatsAppAvailabilityReason.TIMEOUT, "timeout apos " + tentativa + " tentativas"));
    }

    private enum HealthResult { READY, TRANSIENT, AUTH_ERROR }

    private HealthResult checkHealthOnce() {
        String healthUrl;
        try {
            healthUrl = serviceUrl + "/health";
            URI.create(healthUrl);
        } catch (IllegalArgumentException e) {
            return HealthResult.TRANSIENT;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .timeout(requestTimeout)
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status >= 200 && status < 300) return HealthResult.READY;
            if (status == 401 || status == 403) return HealthResult.AUTH_ERROR;
            return HealthResult.TRANSIENT;
        } catch (HttpTimeoutException e) {
            return HealthResult.TRANSIENT;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return HealthResult.TRANSIENT;
        } catch (IOException e) {
            return HealthResult.TRANSIENT;
        } catch (Exception e) {
            return HealthResult.TRANSIENT;
        }
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

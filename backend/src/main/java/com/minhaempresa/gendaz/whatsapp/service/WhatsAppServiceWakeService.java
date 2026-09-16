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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Best-effort wake do whatsapp-service via GET /health.
 * Disparado uma unica vez por boot via {@link com.minhaempresa.gendaz.whatsapp.WhatsAppServiceStartupListener}.
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
     * Nunca propaga excecao e nunca bloqueia a thread chamadora.
     */
    public void wakeAsync() {
        if (!started.compareAndSet(false, true)) {
            log.debug("[whatsapp-wake] wake ja iniciado, ignorando chamada duplicada");
            return;
        }
        if (serviceUrl.isBlank()) {
            log.warn("[whatsapp-wake] whatsapp service url nao configurada, wake ignorado");
            return;
        }
        log.info("[whatsapp-wake] iniciando wake do servico");
        try {
            executor.submit(this::doWake);
        } catch (Exception e) {
            log.warn("[whatsapp-wake] falha ao submeter wake. erroTipo={}", e.getClass().getSimpleName());
        }
    }

    /**
     * Execucao sincrona do wake, usada por wakeAsync e diretamente em testes.
     * Best effort: nunca lanca excecao para fora.
     */
    void doWake() {
        if (serviceUrl.isBlank()) {
            log.warn("[whatsapp-wake] whatsapp service url nao configurada, wake ignorado");
            return;
        }
        Instant deadline = Instant.now().plus(maxDuration);
        int tentativa = 0;
        String healthUrl;
        try {
            healthUrl = serviceUrl + "/health";
            // Validar URL antecipadamente
            URI.create(healthUrl);
        } catch (IllegalArgumentException e) {
            log.warn("[whatsapp-wake] url invalida, abortando wake");
            return;
        }

        while (Instant.now().isBefore(deadline)) {
            tentativa++;
            if (Thread.currentThread().isInterrupted()) {
                log.info("[whatsapp-wake] wake interrompido");
                return;
            }
            if (executor.isShutdown()) {
                log.info("[whatsapp-wake] executor em shutdown, abortando");
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
                    log.info("[whatsapp-wake] servico disponivel tentativa={} status={}", tentativa, status);
                    return;
                }
                if (NON_TRANSIENT_STATUS.contains(status)) {
                    log.warn("[whatsapp-wake] resposta nao transitoria tentativa={} status={} abortando", tentativa, status);
                    return;
                }
                if (RETRYABLE_STATUS.contains(status) || (status >= 500 && status < 600)) {
                    log.warn("[whatsapp-wake] tentativa={} status={}", tentativa, status);
                } else {
                    // Outros 4xx nao listados como non-transient: tratar como nao retryable para nao ficar 90s
                    if (status >= 400 && status < 500) {
                        log.warn("[whatsapp-wake] resposta nao transitoria tentativa={} status={} abortando", tentativa, status);
                        return;
                    }
                    log.warn("[whatsapp-wake] tentativa={} status={}", tentativa, status);
                }
            } catch (HttpTimeoutException e) {
                log.warn("[whatsapp-wake] tentativa={} timeout", tentativa);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("[whatsapp-wake] wake interrompido");
                return;
            } catch (ConnectException e) {
                log.warn("[whatsapp-wake] tentativa={} status=connect_error", tentativa);
            } catch (IOException e) {
                // Inclui HttpConnectTimeoutException (subclasse de IOException) e falhas de rede
                Throwable cause = e.getCause();
                boolean isConnectFailure = e instanceof ConnectException
                        || cause instanceof ConnectException
                        || e instanceof java.net.UnknownHostException
                        || cause instanceof java.net.UnknownHostException;
                if (isConnectFailure) {
                    log.warn("[whatsapp-wake] tentativa={} status=connect_error", tentativa);
                } else {
                    log.warn("[whatsapp-wake] tentativa={} status=io_error", tentativa);
                }
            } catch (Exception e) {
                log.warn("[whatsapp-wake] tentativa={} erroTipo={}", tentativa, e.getClass().getSimpleName());
            }

            if (Instant.now().plus(retryInterval).isAfter(deadline)) {
                break;
            }
            try {
                sleeper.sleep(retryInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("[whatsapp-wake] wake interrompido durante sleep");
                return;
            } catch (Exception e) {
                log.warn("[whatsapp-wake] falha no sleep tentativa={} erroTipo={}", tentativa, e.getClass().getSimpleName());
                return;
            }
        }
        log.warn("[whatsapp-wake] servico nao ficou disponivel dentro da janela de cold start");
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }
}

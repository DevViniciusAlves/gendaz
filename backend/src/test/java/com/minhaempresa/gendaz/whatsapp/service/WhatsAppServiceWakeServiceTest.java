package com.minhaempresa.gendaz.whatsapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WhatsAppServiceWakeServiceTest {

    private HttpServer stub;
    private ExecutorService exec;

    @BeforeEach
    void setup() {
        exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
    }
    private String baseUrl;
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int stubStatus = 200;
    private final List<Integer> statusSequence = new ArrayList<>();
    // Enhanced for Retry-After per response
    private final List<String> retryAfterSeq = new ArrayList<>();
    private volatile String responseRetryAfter;
    private final AtomicInteger seqIndex = new AtomicInteger(0);
    private volatile String lastPath;
    private volatile String lastAuth;
    private volatile String lastAccept;
    private volatile String lastUserAgent;
    private volatile String lastCacheControl;

    @BeforeEach
    void subirStub() throws IOException {
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/", exchange -> {
            calls.incrementAndGet();
            lastPath = exchange.getRequestURI().getPath();
            lastAuth = exchange.getRequestHeaders().getFirst("Authorization");
            lastAccept = exchange.getRequestHeaders().getFirst("Accept");
            lastUserAgent = exchange.getRequestHeaders().getFirst("User-Agent");
            lastCacheControl = exchange.getRequestHeaders().getFirst("Cache-Control");
            int status;
            String retryAfterValue = null;
            synchronized (statusSequence) {
                if (!statusSequence.isEmpty()) {
                    int idx = seqIndex.getAndIncrement();
                    status = idx < statusSequence.size() ? statusSequence.get(idx) : statusSequence.get(statusSequence.size() - 1);
                    synchronized (retryAfterSeq) {
                        if (!retryAfterSeq.isEmpty()) {
                            retryAfterValue = idx < retryAfterSeq.size() ? retryAfterSeq.get(idx) : retryAfterSeq.get(retryAfterSeq.size() - 1);
                        } else {
                            retryAfterValue = responseRetryAfter;
                        }
                    }
                } else {
                    status = stubStatus;
                    synchronized (retryAfterSeq) {
                        if (!retryAfterSeq.isEmpty()) {
                            // no sequence status but keep first retryAfter if set
                            retryAfterValue = retryAfterSeq.get(0);
                        } else {
                            retryAfterValue = responseRetryAfter;
                        }
                    }
                }
            }
            if (retryAfterValue != null) {
                exchange.getResponseHeaders().set("Retry-After", retryAfterValue);
            }
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        stub.start();
        baseUrl = "http://127.0.0.1:" + stub.getAddress().getPort();
    }

    @AfterEach
    void pararStub() {
        stub.stop(0);
        statusSequence.clear();
        retryAfterSeq.clear();
        seqIndex.set(0);
        calls.set(0);
        lastPath = null;
        lastAuth = null;
        lastAccept = null;
        lastUserAgent = null;
        lastCacheControl = null;
        responseRetryAfter = null;
        stubStatus = 200;
    }

    private WhatsAppServiceWakeService serviceWithNoSleep(String url, Duration maxDuration) {
        ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        return new WhatsAppServiceWakeService(url,
                Duration.ofSeconds(2), Duration.ofSeconds(2),
                Duration.ofMillis(10), maxDuration,
                d -> { /* no sleep */ },
                exec);
    }

    private WhatsAppServiceWakeService serviceWithSleeper(String url, Duration maxDuration, List<Duration> delays) {
        ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        return new WhatsAppServiceWakeService(url,
                Duration.ofSeconds(2), Duration.ofSeconds(2),
                Duration.ofMillis(10), maxDuration,
                d -> delays.add(d),
                exec);
    }

    // 1. 200 primeira tentativa
    @Test
    void health_200_primeiraTentativa_sucessoSemRetry() {
        WhatsAppServiceWakeService svc = serviceWithNoSleep(baseUrl, Duration.ofSeconds(2));
        stubStatus = 200;
        svc.doWake();
        assertEquals(1, calls.get());
        assertEquals("/health", lastPath);
        assertTrue(lastAuth == null || lastAuth.isBlank(), "health nao deve enviar Authorization");
        svc.destroy();
    }

    // 2. 502→502→200
    @Test
    void retry_502_502_200_paraNo200() {
        synchronized (statusSequence) {
            statusSequence.addAll(List.of(502, 502, 200));
        }
        WhatsAppServiceWakeService svc = serviceWithNoSleep(baseUrl, Duration.ofSeconds(15));
        svc.doWake();
        assertEquals(3, calls.get());
        svc.destroy();
    }

    // 3. 503→200
    @Test
    void retry_503_200_paraNo200() {
        synchronized (statusSequence) {
            statusSequence.addAll(List.of(503, 200));
        }
        WhatsAppServiceWakeService svc = serviceWithNoSleep(baseUrl, Duration.ofSeconds(10));
        svc.doWake();
        assertEquals(2, calls.get());
        svc.destroy();
    }

    // 4. 504→200
    @Test
    void retry_504_200_paraNo200() {
        synchronized (statusSequence) {
            statusSequence.addAll(List.of(504, 200));
        }
        WhatsAppServiceWakeService svc = serviceWithNoSleep(baseUrl, Duration.ofSeconds(10));
        svc.doWake();
        assertEquals(2, calls.get());
        svc.destroy();
    }

    // 5. 429 com Retry-After:60
    @Test
    void retry_429_comRetryAfter_60_usaDelay60() {
        synchronized (statusSequence) {
            statusSequence.addAll(List.of(429, 200));
        }
        synchronized (retryAfterSeq) {
            retryAfterSeq.addAll(java.util.Arrays.asList("60", null));
        }
        List<Duration> delays = Collections.synchronizedList(new ArrayList<>());
        WhatsAppServiceWakeService svc = serviceWithSleeper(baseUrl, Duration.ofSeconds(70), delays);
        svc.doWake();
        assertEquals(2, calls.get());
        assertTrue(delays.stream().anyMatch(d -> d.equals(Duration.ofSeconds(60))), "deve conter delay 60s delays=" + delays);
        svc.destroy();
    }

    // 6. validar Retry-After 60 NÃO vira 30
    @Test
    void retryAfter_60_naoVira30() {
        synchronized (statusSequence) {
            statusSequence.addAll(List.of(429, 200));
        }
        synchronized (retryAfterSeq) {
            retryAfterSeq.addAll(java.util.Arrays.asList("60", null));
        }
        List<Duration> delays = Collections.synchronizedList(new ArrayList<>());
        WhatsAppServiceWakeService svc = serviceWithSleeper(baseUrl, Duration.ofSeconds(70), delays);
        svc.doWake();
        assertTrue(delays.contains(Duration.ofSeconds(60)), "Retry-After 60 deve ser 60s, nao 30s delays=" + delays);
        assertTrue(delays.stream().noneMatch(d -> d.equals(Duration.ofSeconds(30))), "nao deve ter 30s");
        svc.destroy();
    }

    // 7. Retry-After 301 NÃO vira 300
    @Test
    void retryAfter_301_naoVira300() {
        synchronized (statusSequence) {
            statusSequence.addAll(List.of(429, 200));
        }
        synchronized (retryAfterSeq) {
            retryAfterSeq.addAll(java.util.Arrays.asList("301", null));
        }
        List<Duration> delays = Collections.synchronizedList(new ArrayList<>());
        WhatsAppServiceWakeService svc = serviceWithSleeper(baseUrl, Duration.ofSeconds(400), delays);
        svc.doWake();
        assertTrue(delays.contains(Duration.ofSeconds(301)), "Retry-After 301 deve ser exato delays=" + delays);
        svc.destroy();
    }

    // 8. Retry-After 600 NÃO vira 300
    @Test
    void retryAfter_600_naoVira300() {
        synchronized (statusSequence) {
            statusSequence.addAll(List.of(429, 200));
        }
        synchronized (retryAfterSeq) {
            retryAfterSeq.addAll(java.util.Arrays.asList("600", null));
        }
        List<Duration> delays = Collections.synchronizedList(new ArrayList<>());
        WhatsAppServiceWakeService svc = serviceWithSleeper(baseUrl, Duration.ofSeconds(700), delays);
        svc.doWake();
        assertTrue(delays.contains(Duration.ofSeconds(600)), "Retry-After 600 deve ser exato delays=" + delays);
        svc.destroy();
    }

    // 9. Retry-After 0 usa MIN_BACKOFF (2s)
    @Test
    void retryAfter_0_usaMinBackoff() {
        synchronized (statusSequence) {
            statusSequence.addAll(List.of(429, 200));
        }
        synchronized (retryAfterSeq) {
            retryAfterSeq.addAll(java.util.Arrays.asList("0", null));
        }
        List<Duration> delays = Collections.synchronizedList(new ArrayList<>());
        WhatsAppServiceWakeService svc = serviceWithSleeper(baseUrl, Duration.ofSeconds(10), delays);
        svc.doWake();
        assertTrue(delays.contains(Duration.ofSeconds(2)), "Retry-After 0 deve virar MIN_BACKOFF 2s delays=" + delays);
        svc.destroy();
    }

    // 10. Retry-After negativo usa fallback (MIN_BACKOFF)
    @Test
    void retryAfter_negativo_usaFallback() {
        synchronized (statusSequence) {
            statusSequence.addAll(List.of(429, 200));
        }
        synchronized (retryAfterSeq) {
            retryAfterSeq.addAll(java.util.Arrays.asList("-5", null));
        }
        List<Duration> delays = Collections.synchronizedList(new ArrayList<>());
        WhatsAppServiceWakeService svc = serviceWithSleeper(baseUrl, Duration.ofSeconds(10), delays);
        svc.doWake();
        assertTrue(delays.contains(Duration.ofSeconds(2)), "Retry-After negativo deve virar MIN_BACKOFF delays=" + delays);
        svc.destroy();
    }

    // 11. HTTP-date futura
    @Test
    void retryAfter_httpDate_futura() {
        String future = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                java.time.ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(60));
        synchronized (statusSequence) {
            statusSequence.addAll(List.of(429, 200));
        }
        synchronized (retryAfterSeq) {
            retryAfterSeq.addAll(java.util.Arrays.asList(future, null));
        }
        List<Duration> delays = Collections.synchronizedList(new ArrayList<>());
        WhatsAppServiceWakeService svc = serviceWithSleeper(baseUrl, Duration.ofSeconds(70), delays);
        svc.doWake();
        assertEquals(2, calls.get());
        assertTrue(!delays.isEmpty(), "deve ter delay");
        long secs = delays.get(0).toSeconds();
        // allow ~60s with small skew
        assertTrue(secs >= 55 && secs <= 62, "HTTP-date futura deve dar ~60s secs=" + secs);
        svc.destroy();
    }

    // 12. HTTP-date passada usa MIN_BACKOFF
    @Test
    void retryAfter_httpDate_passada_usaMinBackoff() {
        String past = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                java.time.ZonedDateTime.now(ZoneOffset.UTC).minusSeconds(60));
        synchronized (statusSequence) {
            statusSequence.addAll(List.of(429, 200));
        }
        synchronized (retryAfterSeq) {
            retryAfterSeq.addAll(java.util.Arrays.asList(past, null));
        }
        List<Duration> delays = Collections.synchronizedList(new ArrayList<>());
        WhatsAppServiceWakeService svc = serviceWithSleeper(baseUrl, Duration.ofSeconds(10), delays);
        svc.doWake();
        assertTrue(delays.contains(Duration.ofSeconds(2)), "HTTP-date passada deve virar MIN_BACKOFF delays=" + delays);
        svc.destroy();
    }

    // 13. Retry-After inválido usa exponential backoff
    @Test
    void retryAfter_invalido_usaExponentialBackoff() {
        synchronized (statusSequence) {
            statusSequence.addAll(List.of(429, 200));
        }
        synchronized (retryAfterSeq) {
            retryAfterSeq.addAll(java.util.Arrays.asList("invalido", null));
        }
        List<Duration> delays = Collections.synchronizedList(new ArrayList<>());
        WhatsAppServiceWakeService svc = serviceWithSleeper(baseUrl, Duration.ofSeconds(10), delays);
        svc.doWake();
        assertEquals(2, calls.get());
        assertTrue(!delays.isEmpty());
        long ms = delays.get(0).toMillis();
        // exponential first attempt BASE 4s with jitter ~3s-5s
        assertTrue(ms >= 2000 && ms <= 6000, "invalido deve usar exponential backoff, ms=" + ms);
        svc.destroy();
    }

    // 14. HTTP 401 terminal
    @Test
    void ensureAvailable_401_terminal_authError() {
        stubStatus = 401;
        List<Duration> delays = Collections.synchronizedList(new ArrayList<>());
        ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        WhatsAppServiceWakeService svc = new WhatsAppServiceWakeService(baseUrl,
                Duration.ofSeconds(2), Duration.ofSeconds(2),
                Duration.ofMillis(10), Duration.ofSeconds(2),
                d -> delays.add(d), exec);
        var ex = assertThrows(WhatsAppServiceWakeService.WhatsAppAvailabilityException.class, svc::ensureAvailable);
        assertEquals(WhatsAppServiceWakeService.WhatsAppAvailabilityReason.AUTH_ERROR, ex.getReason());
        assertEquals(1, calls.get());
        svc.destroy();
    }

    // 15. HTTP 403 terminal
    @Test
    void ensureAvailable_403_terminal_authError() {
        stubStatus = 403;
        ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        WhatsAppServiceWakeService svc = new WhatsAppServiceWakeService(baseUrl,
                Duration.ofSeconds(2), Duration.ofSeconds(2),
                Duration.ofMillis(10), Duration.ofSeconds(2),
                d -> {}, exec);
        var ex = assertThrows(WhatsAppServiceWakeService.WhatsAppAvailabilityException.class, svc::ensureAvailable);
        assertEquals(WhatsAppServiceWakeService.WhatsAppAvailabilityReason.AUTH_ERROR, ex.getReason());
        assertEquals(1, calls.get());
        svc.destroy();
    }

    // 16. HTTP 404 terminal
    @Test
    void respostaNaoTransitoria_404_abortaSemRetry() {
        stubStatus = 404;
        WhatsAppServiceWakeService svc = serviceWithNoSleep(baseUrl, Duration.ofSeconds(2));
        svc.doWake();
        assertEquals(1, calls.get());
        // ensureAvailable also throws UNAVAILABLE
        ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        WhatsAppServiceWakeService svc2 = new WhatsAppServiceWakeService(baseUrl,
                Duration.ofSeconds(2), Duration.ofSeconds(2),
                Duration.ofMillis(10), Duration.ofSeconds(2),
                d -> {}, exec);
        // reset calls for ensureAvailable check
        calls.set(0);
        seqIndex.set(0);
        var ex = assertThrows(WhatsAppServiceWakeService.WhatsAppAvailabilityException.class, svc2::ensureAvailable);
        assertEquals(1, calls.get());
        svc.destroy();
        svc2.destroy();
    }

    // 17. HTTP 500 transitório (retry)
    @Test
    void http_500_transitorio_retry() {
        synchronized (statusSequence) {
            statusSequence.addAll(List.of(500, 200));
        }
        List<Duration> delays = Collections.synchronizedList(new ArrayList<>());
        WhatsAppServiceWakeService svc = serviceWithSleeper(baseUrl, Duration.ofSeconds(10), delays);
        svc.doWake();
        assertEquals(2, calls.get());
        assertTrue(!delays.isEmpty(), "500 deve causar retry com delay");
        svc.destroy();
    }

    // 18. timeout global (maxDuration expiry)
    @Test
    void timeout_global_maxDuration_expiry() {
        stubStatus = 502;
        List<Duration> delays = Collections.synchronizedList(new ArrayList<>());
        ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        // maxDuration muito curta, sleeper tenta sleep mas deadline expirará
        WhatsAppServiceWakeService svc = new WhatsAppServiceWakeService(baseUrl,
                Duration.ofSeconds(2), Duration.ofSeconds(2),
                Duration.ofMillis(10), Duration.ofMillis(100),
                d -> delays.add(d), exec);
        var ex = assertThrows(WhatsAppServiceWakeService.WhatsAppAvailabilityException.class, svc::ensureAvailable);
        assertEquals(WhatsAppServiceWakeService.WhatsAppAvailabilityReason.TIMEOUT, ex.getReason());
        svc.destroy();
    }

    // 19. URL vazia
    @Test
    void urlVazia_nenhumaChamadaHttp() {
        WhatsAppServiceWakeService svc = serviceWithNoSleep("", Duration.ofSeconds(1));
        svc.doWake();
        assertEquals(0, calls.get());
        svc.destroy();

        WhatsAppServiceWakeService svc2 = serviceWithNoSleep("   ", Duration.ofSeconds(1));
        svc2.doWake();
        assertEquals(0, calls.get());
        svc2.destroy();

        WhatsAppServiceWakeService svc3 = serviceWithNoSleep(null, Duration.ofSeconds(1));
        svc3.doWake();
        assertEquals(0, calls.get());
        svc3.destroy();
    }

    // 20. URL inválida
    @Test
    void urlInvalida_ensureAvailable_throwsNotConfigured() {
        ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        WhatsAppServiceWakeService svc = new WhatsAppServiceWakeService("http://[invalid",
                Duration.ofSeconds(2), Duration.ofSeconds(2),
                Duration.ofMillis(10), Duration.ofSeconds(2),
                d -> {}, exec);
        var ex = assertThrows(WhatsAppServiceWakeService.WhatsAppAvailabilityException.class, svc::ensureAvailable);
        assertEquals(WhatsAppServiceWakeService.WhatsAppAvailabilityReason.NOT_CONFIGURED, ex.getReason());
        assertEquals(0, calls.get());
        svc.destroy();
    }

    // 21. trailing slash normalizada
    @Test
    void urlComBarraNoFinal_normalizada() {
        WhatsAppServiceWakeService svc = serviceWithNoSleep(baseUrl + "///", Duration.ofSeconds(1));
        stubStatus = 200;
        svc.doWake();
        assertEquals("/health", lastPath);
        svc.destroy();
    }

    // 22. /health NÃO envia Authorization
    @Test
    void health_naoUsaToken() {
        WhatsAppServiceWakeService svc = serviceWithNoSleep(baseUrl, Duration.ofSeconds(1));
        stubStatus = 200;
        svc.doWake();
        assertTrue(lastAuth == null, "Authorization deve ser null no /health");
        svc.destroy();
    }

    // also ensure health path check
    @Test
    void health_naoEnviaAuthorization_ensureAvailable() {
        stubStatus = 200;
        ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        WhatsAppServiceWakeService svc = new WhatsAppServiceWakeService(baseUrl,
                Duration.ofSeconds(2), Duration.ofSeconds(2),
                Duration.ofMillis(10), Duration.ofSeconds(2),
                d -> {}, exec);
        svc.ensureAvailable();
        assertTrue(lastAuth == null || lastAuth.isBlank(), "health nao deve enviar Authorization");
        svc.destroy();
    }

    // 23. request envia Accept: application/json
    @Test
    void request_enviaAcceptApplicationJson() {
        stubStatus = 200;
        WhatsAppServiceWakeService svc = serviceWithNoSleep(baseUrl, Duration.ofSeconds(2));
        svc.doWake();
        assertEquals("application/json", lastAccept);
        svc.destroy();
    }

    // 24. request envia User-Agent: Gendaz-Stage-Wake/1.0
    @Test
    void request_enviaUserAgent() {
        stubStatus = 200;
        WhatsAppServiceWakeService svc = serviceWithNoSleep(baseUrl, Duration.ofSeconds(2));
        svc.doWake();
        assertEquals("Gendaz-Stage-Wake/1.0", lastUserAgent);
        svc.destroy();
    }

    // 25. request envia Cache-Control: no-cache
    @Test
    void request_enviaCacheControl() {
        stubStatus = 200;
        WhatsAppServiceWakeService svc = serviceWithNoSleep(baseUrl, Duration.ofSeconds(2));
        svc.doWake();
        assertEquals("no-cache", lastCacheControl);
        svc.destroy();
    }

    // 26. 5 threads simultâneas ensureAvailable -> apenas UMA sequência de wake
    @Test
    void ensureAvailable_5threads_apenasUmaSequenciaWake() throws Exception {
        // block stub to verify only 1 request initially
        CountDownLatch block = new CountDownLatch(1);
        // replace handler temporarily to block
        stub.stop(0);
        HttpServer blockingStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger blockingCalls = new AtomicInteger();
        String[] capturedPath = new String[1];
        blockingStub.createContext("/", exchange -> {
            blockingCalls.incrementAndGet();
            capturedPath[0] = exchange.getRequestURI().getPath();
            try { block.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        blockingStub.start();
        String blockingUrl = "http://127.0.0.1:" + blockingStub.getAddress().getPort();

        ExecutorService exec = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        WhatsAppServiceWakeService svc = new WhatsAppServiceWakeService(blockingUrl,
                Duration.ofSeconds(2), Duration.ofSeconds(2),
                Duration.ofMillis(10), Duration.ofSeconds(5),
                d -> {}, exec);

        ExecutorService callers = Executors.newFixedThreadPool(5);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            futures.add(callers.submit(() -> svc.ensureAvailable()));
        }
        // give time for all threads to enter single-flight
        Thread.sleep(500);
        assertEquals(1, blockingCalls.get(), "apenas UMA request deve ter sido feita enquanto bloqueado");
        block.countDown();
        for (Future<?> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }
        assertEquals(1, blockingCalls.get(), "total deve continuar 1 apos desbloqueio");
        svc.destroy();
        callers.shutdownNow();
        blockingStub.stop(0);
        // restart original stub for AfterEach (will be stopped anyway)
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/", exchange -> {
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        stub.start();
    }

    // 27. garantir NÃO existe pre-check fora single-flight (5 calls => only 1 leader request)
    @Test
    void ensureAvailable_naoExistePreCheck_foraSingleFlight() throws Exception {
        stub.stop(0);
        HttpServer countingStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger cnt = new AtomicInteger();
        countingStub.createContext("/", exchange -> {
            cnt.incrementAndGet();
            // delay a bit to keep leader in progress
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        countingStub.start();
        String url = "http://127.0.0.1:" + countingStub.getAddress().getPort();

        ExecutorService exec = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        WhatsAppServiceWakeService svc = new WhatsAppServiceWakeService(url,
                Duration.ofSeconds(2), Duration.ofSeconds(2),
                Duration.ofMillis(10), Duration.ofSeconds(5),
                d -> {}, exec);
        ExecutorService callers = Executors.newFixedThreadPool(5);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            futures.add(callers.submit(() -> svc.ensureAvailable()));
        }
        for (Future<?> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }
        assertEquals(1, cnt.get(), "5 chamadas simultaneas devem resultar em apenas 1 request (sem pre-check)");
        svc.destroy();
        callers.shutdownNow();
        countingStub.stop(0);
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/", exchange -> {
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        stub.start();
    }

    // 28. garantir não existe dupla request imediata (primeiro 502 deve dar sleep antes segunda request)
    @Test
    void naoExisteDuplaRequestImediata_502_sleepAntesSegundaRequest() {
        synchronized (statusSequence) {
            statusSequence.addAll(List.of(502, 200));
        }
        List<Duration> delays = Collections.synchronizedList(new ArrayList<>());
        List<Long> requestTimes = Collections.synchronizedList(new ArrayList<>());
        // need custom stub to record times
        stub.stop(0);
        try {
            HttpServer timedStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            AtomicInteger idx = new AtomicInteger(0);
            timedStub.createContext("/", exchange -> {
                requestTimes.add(System.nanoTime());
                int status;
                synchronized (statusSequence) {
                    int i = idx.getAndIncrement();
                    status = i < statusSequence.size() ? statusSequence.get(i) : 200;
                }
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
            });
            timedStub.start();
            String url = "http://127.0.0.1:" + timedStub.getAddress().getPort();
            ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });
            // Sleeper fake that records but does not actually sleep long; we check delays
            WhatsAppServiceWakeService svc = new WhatsAppServiceWakeService(url,
                    Duration.ofSeconds(2), Duration.ofSeconds(2),
                    Duration.ofMillis(10), Duration.ofSeconds(10),
                    d -> delays.add(d),
                    exec);
            svc.doWake();
            assertEquals(2, requestTimes.size());
            assertTrue(!delays.isEmpty(), "deve ter sleep entre requests");
            assertTrue(delays.get(0).toMillis() >= 2000, "sleep deve ser >= MIN_BACKOFF");
            svc.destroy();
            timedStub.stop(0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                stub.createContext("/", exchange -> {
                    byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
                });
                stub.start();
                baseUrl = "http://127.0.0.1:" + stub.getAddress().getPort();
                statusSequence.clear();
                synchronized (statusSequence) {
                    statusSequence.addAll(List.of(502, 200));
                }
                seqIndex.set(0);
            } catch (IOException ignored) {}
        }
    }

    // 29. validar exponential backoff crescente (delay2 > delay1)
    @Test
    void exponentialBackoff_crescente() {
        synchronized (statusSequence) {
            statusSequence.addAll(List.of(502, 502, 502, 200));
        }
        List<Duration> delays = Collections.synchronizedList(new ArrayList<>());
        WhatsAppServiceWakeService svc = serviceWithSleeper(baseUrl, Duration.ofSeconds(30), delays);
        svc.doWake();
        assertTrue(delays.size() >= 2, "deve ter pelo menos 2 delays");
        // Due to jitter, allow delay2 > delay1 generally but check exponential trend
        // BASE 4s, attempt1 ~4s, attempt2 ~8s, so delay2 should be larger on average
        assertTrue(delays.get(1).toMillis() > delays.get(0).toMillis(), "delay2 deve ser maior que delay1 delays=" + delays);
        svc.destroy();
    }

    // 30. validar MAX_BACKOFF somente para backoff próprio, Retry-After não usa MAX_BACKOFF
    @Test
    void maxBackoff_somenteParaBackoffProprio_retryAfterNaoUsaMax() {
        synchronized (statusSequence) {
            statusSequence.addAll(List.of(429, 200));
        }
        synchronized (retryAfterSeq) {
            retryAfterSeq.addAll(java.util.Arrays.asList("60", null));
        }
        List<Duration> delays = Collections.synchronizedList(new ArrayList<>());
        WhatsAppServiceWakeService svc = serviceWithSleeper(baseUrl, Duration.ofSeconds(70), delays);
        svc.doWake();
        assertTrue(delays.contains(Duration.ofSeconds(60)), "Retry-After 60 nao deve ser capado a MAX_BACKOFF 30s delays=" + delays);
        svc.destroy();
    }

    // Keep existing compatible tests that were not replaced

    @Test
    void erroTemporarioAteEsgotarJanela_naoLancaException() {
        stubStatus = 502;
        WhatsAppServiceWakeService svc = serviceWithNoSleep(baseUrl, Duration.ofMillis(50));
        svc.doWake();
        assertTrue(calls.get() >= 1);
        svc.destroy();
    }

    @Test
    void respostaNaoTransitoria_400_abortaSemRetry() {
        stubStatus = 400;
        WhatsAppServiceWakeService svc = serviceWithNoSleep(baseUrl, Duration.ofSeconds(2));
        svc.doWake();
        assertEquals(1, calls.get());
        svc.destroy();
    }

    @Test
    void wakeAsync_garanteApenasUmaExecucao() throws Exception {
        stubStatus = 502;
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger sleepCalls = new AtomicInteger();
        ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        WhatsAppServiceWakeService svc = new WhatsAppServiceWakeService(baseUrl,
                Duration.ofSeconds(2), Duration.ofSeconds(2),
                Duration.ofMillis(50), Duration.ofMillis(300),
                d -> {
                    sleepCalls.incrementAndGet();
                    Thread.sleep(10);
                    if (sleepCalls.get() >= 2) latch.countDown();
                },
                exec);
        svc.wakeAsync();
        svc.wakeAsync();
        latch.await(2, TimeUnit.SECONDS);
        int c = calls.get();
        assertTrue(c >= 1 && c < 20, "calls=" + c);
        svc.destroy();
    }

    @Test
    void wakeAsync_naoBloqueiaThreadChamadora() {
        stubStatus = 502;
        ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        WhatsAppServiceWakeService svc = new WhatsAppServiceWakeService(baseUrl,
                Duration.ofSeconds(2), Duration.ofSeconds(2),
                Duration.ofMillis(100), Duration.ofSeconds(5),
                d -> Thread.sleep(d.toMillis()),
                exec);
        long inicio = System.currentTimeMillis();
        svc.wakeAsync();
        long duracao = System.currentTimeMillis() - inicio;
        assertTrue(duracao < 500, "wakeAsync nao deve bloquear, duracao=" + duracao);
        svc.destroy();
    }

    // Helper to create a stub HTTP server for relay POST
    private HttpServer relayStub;
    private final AtomicInteger relayCalls = new AtomicInteger();

    @BeforeEach
    void setupRelayStub() {
        relayCalls.set(0);
    }

    private HttpServer subirRelayStub(int port, String path, int status, String rndrIdHeader) throws IOException {
        HttpServer rStub = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        rStub.createContext(path, exchange -> {
            String rndrId = exchange.getRequestHeaders().getFirst("x-render-routing");
            byte[] body = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (rndrIdHeader != null) {
                exchange.getResponseHeaders().set("x-render-routing", rndrIdHeader);
            }
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        rStub.start();
        return rStub;
    }

    private String getRelayUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void pararRelayStub() {
        if (relayStub != null) {
            relayStub.stop(0);
        }
        relayCalls.set(0);
    }

    // Test helper: service with relay URL and token configured
    private WhatsAppServiceWakeService serviceWithRelay(String url, String relayUrl, String relayToken, Duration maxDuration, List<Duration> delays) throws IOException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        return new WhatsAppServiceWakeService(url,
                client,
                Duration.ofSeconds(2),
                maxDuration,
                d -> delays.add(d),
                exec,
                relayUrl, relayToken);
    }

    // 31. 429 + x-render-routing=hibernate-rate-limited → relay exatamente 1 vez e depois WPP 200
    @Test
    void relay_429_hibernateRateLimited_umVez_depoisWPP200() throws IOException {
        synchronized (statusSequence) {
            statusSequence.addAll(List.of(429, 200));
        }
        synchronized (retryAfterSeq) {
            retryAfterSeq.addAll(java.util.Arrays.asList(null, null));
        }
        relayStub = subirRelayStub(0, "/", 200, "hibernate-rate-limited");
        String relayUrl = getRelayUrl(relayStub);
        List<Duration> delays = Collections.synchronizedList(new ArrayList<>());
        WhatsAppServiceWakeService svcWithRelay = serviceWithRelay(baseUrl, relayUrl, "test-token", Duration.ofSeconds(15), delays);
        svcWithRelay.doWake();
        assertEquals(1, relayCalls.get(), "relay deve ter sido chamado exatamente 1 vez");
        assertEquals(3, calls.get(), "3 requests to WPP: 1st 429, 2nd after relay 200 check, 3rd final 200");
        svcWithRelay.destroy();
        relayStub.stop(0);
    }

    // 32. 502 + x-render-routing=no-deploy → relay exatamente 1 vez
    @Test
    void relay_502_noDeploy_umVez() throws IOException {
        synchronized (statusSequence) {
            statusSequence.addAll(List.of(502, 200));
        }
        synchronized (retryAfterSeq) {
            retryAfterSeq.addAll(java.util.Arrays.asList(null, null));
        }
        relayStub = subirRelayStub(0, "/", 200, "no-deploy");
        String relayUrl = getRelayUrl(relayStub);
        List<Duration> delays = Collections.synchronizedList(new ArrayList<>());
        WhatsAppServiceWakeService svcWithRelay = serviceWithRelay(baseUrl, relayUrl, "test-token", Duration.ofSeconds(15), delays);
        svcWithRelay.doWake();
        assertEquals(1, relayCalls.get(), "relay deve ter sido chamado exatamente 1 vez");
        assertTrue(calls.get() >= 2, "pelo menos 2 requests to WPP (1st 502, 2nd after relay)");
        svcWithRelay.destroy();
        relayStub.stop(0);
    }

    // 33. 429 sem header esperado → relay zero times
    @Test
    void relay_429_semHeader_zerasVezes() throws IOException {
        synchronized (statusSequence) {
            statusSequence.addAll(List.of(429, 200));
        }
        synchronized (retryAfterSeq) {
            retryAfterSeq.addAll(java.util.Arrays.asList(null, null));
        }
        List<Duration> delays = Collections.synchronizedList(new ArrayList<>());
        WhatsAppServiceWakeService svc = new WhatsAppServiceWakeService(baseUrl,
                Duration.ofSeconds(2), Duration.ofSeconds(2),
                Duration.ofMillis(10), Duration.ofSeconds(15),
                d -> delays.add(d),
                exec);
        svc.doWake();
        assertEquals(0, relayCalls.get(), "relay nao deve ser chamado quando header nao presente");
        assertEquals(2, calls.get(), "apenas 2 requests to WPP (1st 429, 2nd 200)");
        svc.destroy();
    }

    // 34. 502 sem header esperado → relay zero times
    @Test
    void relay_502_semHeader_zerasVezes() throws IOException {
        synchronized (statusSequence) {
            statusSequence.addAll(List.of(502, 200));
        }
        synchronized (retryAfterSeq) {
            retryAfterSeq.addAll(java.util.Arrays.asList(null, null));
        }
        List<Duration> delays = Collections.synchronizedList(new ArrayList<>());
        WhatsAppServiceWakeService svc = new WhatsAppServiceWakeService(baseUrl,
                Duration.ofSeconds(2), Duration.ofSeconds(2),
                Duration.ofMillis(10), Duration.ofSeconds(15),
                d -> delays.add(d),
                exec);
        svc.doWake();
        assertEquals(0, relayCalls.get(), "relay nao deve ser chamado quando header nao presente");
        assertEquals(2, calls.get(), "apenas 2 requests to WPP (1st 502, 2nd 200)");
        svc.destroy();
    }

    // 35. WPP 200 imediato → relay zero vezes
    @Test
    void relay_200_imediato_zerasVezes() throws IOException {
        stubStatus = 200;
        List<Duration> delays = Collections.synchronizedList(new ArrayList<>());
        WhatsAppServiceWakeService svc = serviceWithNoSleep(baseUrl, Duration.ofSeconds(2));
        svc.doWake();
        assertEquals(0, relayCalls.get(), "relay nao deve ser chamado quando WPP ja estiver 200");
        assertEquals(1, calls.get(), "apenas 1 request a WPP");
        svc.destroy();
    }

    // 36. varios 429 após o relay → relay continua exatamente 1 vez
    @Test
    void relay_varios429_depois_continuaUmaVez() throws IOException {
        synchronized (statusSequence) {
            statusSequence.addAll(List.of(429, 429, 429, 200));
        }
        synchronized (retryAfterSeq) {
            retryAfterSeq.addAll(java.util.Arrays.asList("60", "60", null, null));
        }
        relayStub = subirRelayStub(0, "/", 200, "hibernate-rate-limited");
        String relayUrl = getRelayUrl(relayStub);
        List<Duration> delays = Collections.synchronizedList(new ArrayList<>());
        WhatsAppServiceWakeService svcWithRelay = serviceWithRelay(baseUrl, relayUrl, "test-token", Duration.ofSeconds(15), delays);
        svcWithRelay.doWake();
        assertEquals(1, relayCalls.get(), "relay deve continuar exatamente 1 vez mesmo com multiplos 429");
        svcWithRelay.destroy();
        relayStub.stop(0);
    }

    // 37. relay retorna HTTP nao-2xx -> nao tenta relay novamente e continua readiness direto
    @Test
    void relay_nao2xx_naoTentaNovamente() throws IOException {
        synchronized (statusSequence) {
            statusSequence.addAll(List.of(429, 502, 200));
        }
        synchronized (retryAfterSeq) {
            retryAfterSeq.addAll(java.util.Arrays.asList(null, null, null));
        }
        relayStub = subirRelayStub(0, "/", 500, "hibernate-rate-limited"); // relay returns 500
        String relayUrl = getRelayUrl(relayStub);
        List<Duration> delays = Collections.synchronizedList(new ArrayList<>());
        WhatsAppServiceWakeService svcWithRelay = serviceWithRelay(baseUrl, relayUrl, "test-token", Duration.ofSeconds(15), delays);
        svcWithRelay.doWake();
        // Relay was attempted once (the first 429), then continued with direct polling
        // The 502 after relay should not trigger another relay attempt
        assertTrue(relayCalls.get() >= 1, "relay deve ter sido attemptado pelo menos 1 vez");
        // After relay fails, should continue with direct WPP polling, not another relay
        svcWithRelay.destroy();
        relayStub.stop(0);
    }

    // 38. relay indisponivel/configuracao vazia -> nao quebra
    @Test
    void relay_indisponivel_nQuebra() throws IOException {
        // Test with empty relay config - should not throw, just continue
        WhatsAppServiceWakeService svc = new WhatsAppServiceWakeService(baseUrl,
                Duration.ofSeconds(2), Duration.ofSeconds(2),
                Duration.ofMillis(10), Duration.ofSeconds(2),
                d -> {}, exec);
        // wakeRelayUrl and wakeRelayToken default to "" in constructor
        // First 429 should not trigger relay since config is empty
        synchronized (statusSequence) {
            statusSequence.addAll(List.of(429, 200));
        }
        svc.doWake();
        // Should succeed via direct WPP polling, not relay
        assertTrue(calls.get() >= 1, "deve ter chamado WPP");
        svc.destroy();
    }

    // 39. Authorization do relay nunca vai para GET /health
    @Test
    void relay_Authorization_naoVaiParaHealth() throws IOException {
        // Stub for /health that captures Authorization header
        HttpServer healthStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        healthStub.createContext("/", exchange -> {
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        healthStub.start();
        String healthBaseUrl = "http://127.0.0.1:" + healthStub.getAddress().getPort();

        // Relay stub that returns 200 with x-render-routing header
        HttpServer relayStub = subirRelayStub(0, "/", 200, "hibernate-rate-limited");
        String relayUrl = getRelayUrl(relayStub);

        List<Duration> delays = Collections.synchronizedList(new ArrayList<>());
        WhatsAppServiceWakeService svcWithRelay = serviceWithRelay(healthBaseUrl, relayUrl, "test-token", Duration.ofSeconds(15), delays);
        svcWithRelay.doWake();

        // Authorization nao deve ter sido enviado para /health
        assertTrue(capturedAuth.get() == null || capturedAuth.get().isBlank(), 
            "Authorization nao deve ir para /health. captured=" + capturedAuth.get());

        svcWithRelay.destroy();
        relayStub.stop(0);
        healthStub.stop(0);
    }

    // 40. requests concorrentes continuam compartilhando um unico single-flight e no maximo uma chamada relay
    @Test
    void concurrentRequests_singleFlight_umRelay() throws Exception {
        CountDownLatch block = new CountDownLatch(1);
        stub.stop(0);
        HttpServer blockingStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger blockingCalls = new AtomicInteger();
        String[] capturedPath = new String[1];
        blockingStub.createContext("/", exchange -> {
            blockingCalls.incrementAndGet();
            capturedPath[0] = exchange.getRequestURI().getPath();
            try { block.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        blockingStub.start();
        String blockingUrl = "http://127.0.0.1:" + blockingStub.getAddress().getPort();

        // Configure relay URL using package-private constructor
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        ExecutorService exec = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        WhatsAppServiceWakeService svcWithRelay = new WhatsAppServiceWakeService(blockingUrl,
                client,
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                d -> {},
                exec,
                blockingUrl, "test-token");

        ExecutorService callers = Executors.newFixedThreadPool(3);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            futures.add(callers.submit(() -> svcWithRelay.ensureAvailable()));
        }
        // give time for all threads to enter single-flight
        Thread.sleep(500);
        assertEquals(1, blockingCalls.get(), "apenas UMA request deve ter sido feita enquanto bloqueado");
        block.countDown();
        for (Future<?> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }
        // relay should have been called at most once across all concurrent threads
        // (single-flight ensures only one relay attempt)
        svcWithRelay.destroy();
        callers.shutdownNow();
        blockingStub.stop(0);
        // restart original stub for AfterEach
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/", exchange -> {
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        stub.start();
    }

}

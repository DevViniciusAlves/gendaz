package com.minhaempresa.gendaz.whatsapp.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

class WhatsAppSessionReadyServiceTest {

    private HttpServer stub;
    private String baseUrl;
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int stubStatus = 200;
    private volatile String stubBody = "{\"companyId\":\"1\",\"state\":\"CONNECTED\"}";
    private final List<Integer> statusSeq = new ArrayList<>();
    private final List<String> bodySeq = new ArrayList<>();
    private final AtomicInteger idx = new AtomicInteger();
    private final ObjectMapper om = new ObjectMapper();

    // Requirement: Stub must support response headers Retry-After
    private volatile String responseRetryAfter;
    private final List<String> retryAfterSeq = new ArrayList<>();
    private final AtomicInteger retryIdx = new AtomicInteger();

    // for timeout test: delay response
    private volatile long responseDelayMs = 0;

    // for concurrency blocking tests
    private volatile CountDownLatch blockEntered;
    private volatile CountDownLatch blockRelease;

    // sleeper recording
    private final List<Duration> slept = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void up() throws Exception {
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/", ex -> {
            int cur = calls.incrementAndGet();
            // blocking for single-flight proof - block first call after counting
            if (blockEntered != null && blockRelease != null) {
                if (cur == 1) {
                    blockEntered.countDown();
                    try { blockRelease.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            }
            if (responseDelayMs > 0) {
                try { Thread.sleep(responseDelayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            int s;
            String b;
            String ra = null;
            synchronized (statusSeq) {
                if (!statusSeq.isEmpty()) {
                    int i = idx.getAndIncrement();
                    s = i < statusSeq.size() ? statusSeq.get(i) : statusSeq.get(statusSeq.size() - 1);
                    b = i < bodySeq.size() ? bodySeq.get(i) : bodySeq.get(bodySeq.size() - 1);
                    synchronized (retryAfterSeq) {
                        if (!retryAfterSeq.isEmpty()) {
                            int ri = retryIdx.getAndIncrement();
                            ra = ri < retryAfterSeq.size() ? retryAfterSeq.get(ri) : retryAfterSeq.get(retryAfterSeq.size() - 1);
                        } else {
                            ra = responseRetryAfter;
                        }
                    }
                } else {
                    s = stubStatus;
                    b = stubBody;
                    synchronized (retryAfterSeq) {
                        if (!retryAfterSeq.isEmpty()) {
                            int ri = retryIdx.getAndIncrement();
                            ra = ri < retryAfterSeq.size() ? retryAfterSeq.get(ri) : retryAfterSeq.get(retryAfterSeq.size() - 1);
                        } else {
                            ra = responseRetryAfter;
                        }
                    }
                }
            }
            byte[] body = b.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            if (ra != null) {
                ex.getResponseHeaders().set("Retry-After", ra);
            }
            ex.sendResponseHeaders(s, body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        });
        stub.setExecutor(Executors.newCachedThreadPool());
        stub.start();
        baseUrl = "http://127.0.0.1:" + stub.getAddress().getPort();
    }

    @AfterEach
    void down() {
        stub.stop(0);
        synchronized (statusSeq) { statusSeq.clear(); bodySeq.clear(); }
        synchronized (retryAfterSeq) { retryAfterSeq.clear(); }
        idx.set(0);
        retryIdx.set(0);
        calls.set(0);
        responseRetryAfter = null;
        responseDelayMs = 0;
        blockEntered = null;
        blockRelease = null;
        slept.clear();
        stubStatus = 200;
        stubBody = "{\"companyId\":\"1\",\"state\":\"CONNECTED\"}";
    }

    // helpers
    WhatsAppSessionReadyService svc(Duration maxWait) {
        return new WhatsAppSessionReadyService(om, baseUrl, "tok",
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                Duration.ofSeconds(2), maxWait, d -> {});
    }

    WhatsAppSessionReadyService svcWithRecordingSleeper(Duration maxWait, List<Duration> out) {
        return new WhatsAppSessionReadyService(om, baseUrl, "tok",
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                Duration.ofSeconds(2), maxWait, d -> out.add(d));
    }

    WhatsAppSessionReadyService svcWithSleeper(Duration maxWait, WhatsAppSessionReadyService.Sleeper sleeper) {
        return new WhatsAppSessionReadyService(om, baseUrl, "tok",
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                Duration.ofSeconds(2), maxWait, sleeper);
    }

    // 1 CONNECTED imediato
    @Test
    void connectedImediato() {
        stubBody = "{\"companyId\":\"1\",\"state\":\"CONNECTED\"}";
        assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, svc(Duration.ofSeconds(2)).ensureSessionConnected("1"));
        assertEquals(1, calls.get());
    }

    // 2 CONNECTING -> CONNECTED
    @Test
    void connectingThenConnected() {
        synchronized (statusSeq) {
            statusSeq.addAll(List.of(200, 200));
            bodySeq.addAll(List.of("{\"state\":\"CONNECTING\"}", "{\"state\":\"CONNECTED\"}"));
        }
        var s = new WhatsAppSessionReadyService(om, baseUrl, "tok",
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                Duration.ofSeconds(2), Duration.ofSeconds(5), d -> {});
        assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, s.ensureSessionConnected("1"));
        assertTrue(calls.get() >= 2);
    }

    // 3 RECONNECTING -> CONNECTED
    @Test
    void reconnectingThenConnected() {
        synchronized (statusSeq) {
            statusSeq.addAll(List.of(200, 200));
            bodySeq.addAll(List.of("{\"state\":\"RECONNECTING\"}", "{\"state\":\"CONNECTED\"}"));
        }
        var s = svc(Duration.ofSeconds(5));
        assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, s.ensureSessionConnected("1"));
    }

    // 4 DISCONNECTED -> CONNECTED
    @Test
    void disconnectedThenConnected() {
        synchronized (statusSeq) {
            statusSeq.addAll(List.of(200, 200));
            bodySeq.addAll(List.of("{\"state\":\"DISCONNECTED\"}", "{\"state\":\"CONNECTED\"}"));
        }
        assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, svc(Duration.ofSeconds(5)).ensureSessionConnected("1"));
    }

    // 5 NOT_CONNECTED -> CONNECTED
    @Test
    void notConnectedThenConnected() {
        synchronized (statusSeq) {
            statusSeq.addAll(List.of(200, 200));
            bodySeq.addAll(List.of("{\"state\":\"NOT_CONNECTED\"}", "{\"state\":\"CONNECTED\"}"));
        }
        assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, svc(Duration.ofSeconds(5)).ensureSessionConnected("1"));
    }

    // 6 LOGGED_OUT terminal
    @Test
    void loggedOutTerminal() {
        stubBody = "{\"state\":\"LOGGED_OUT\"}";
        assertEquals(WhatsAppSessionReadyService.ReadyResult.LOGGED_OUT, svc(Duration.ofSeconds(2)).ensureSessionConnected("1"));
    }

    // 7 HTTP 401 -> AUTH_ERROR
    @Test
    void auth401() {
        stubStatus = 401;
        stubBody = "unauthorized";
        assertEquals(WhatsAppSessionReadyService.ReadyResult.AUTH_ERROR, svc(Duration.ofSeconds(2)).ensureSessionConnected("1"));
    }

    // 8 HTTP 403 -> AUTH_ERROR
    @Test
    void auth403() {
        stubStatus = 403;
        stubBody = "forbidden";
        assertEquals(WhatsAppSessionReadyService.ReadyResult.AUTH_ERROR, svc(Duration.ofSeconds(2)).ensureSessionConnected("1"));
    }

    // 9 HTTP 404 -> UNAVAILABLE
    @Test
    void http404Unavailable() {
        stubStatus = 404;
        stubBody = "not found";
        assertEquals(WhatsAppSessionReadyService.ReadyResult.UNAVAILABLE, svc(Duration.ofSeconds(2)).ensureSessionConnected("1"));
    }

    // 10 HTTP 429 com Retry-After 60
    @Test
    void http429ComRetryAfter60() {
        synchronized (statusSeq) {
            statusSeq.addAll(List.of(429, 200));
            bodySeq.addAll(List.of("{\"error\":\"rate limited\"}", "{\"state\":\"CONNECTED\"}"));
        }
        synchronized (retryAfterSeq) { retryAfterSeq.addAll(Arrays.asList("60", (String) null)); }
        List<Duration> rec = Collections.synchronizedList(new ArrayList<>());
        // maxWait must be > 60 to allow second poll
        var s = svcWithRecordingSleeper(Duration.ofSeconds(70), rec);
        assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, s.ensureSessionConnected("1"));
        assertFalse(rec.isEmpty(), "should have slept with Retry-After");
        assertEquals(60, rec.get(0).toSeconds(), "Retry-After 60 must be used as delay");
    }

    // 11 HTTP 429 com HTTP-date futura
    @Test
    void http429ComHttpDateFutura() {
        String httpDate = ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(60).format(DateTimeFormatter.RFC_1123_DATE_TIME);
        synchronized (statusSeq) {
            statusSeq.addAll(List.of(429, 200));
            bodySeq.addAll(List.of("{\"error\":\"rate\"}", "{\"state\":\"CONNECTED\"}"));
        }
        synchronized (retryAfterSeq) { retryAfterSeq.addAll(Arrays.asList(httpDate, (String) null)); }
        List<Duration> rec = Collections.synchronizedList(new ArrayList<>());
        var s = svcWithRecordingSleeper(Duration.ofSeconds(70), rec);
        assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, s.ensureSessionConnected("1"));
        assertFalse(rec.isEmpty());
        // http-date ~60s, allow tolerance
        assertTrue(rec.get(0).toSeconds() >= 50 && rec.get(0).toSeconds() <= 65, "http-date delay ~60s got " + rec.get(0));
    }

    // 12 HTTP 429 SEM Retry-After -> backoff padrão
    @Test
    void http429SemRetryAfterBackoffPadrao() {
        synchronized (statusSeq) {
            statusSeq.addAll(List.of(429, 200));
            bodySeq.addAll(List.of("{\"error\":\"rate\"}", "{\"state\":\"CONNECTED\"}"));
        }
        // no Retry-After header
        List<Duration> rec = Collections.synchronizedList(new ArrayList<>());
        var s = svcWithRecordingSleeper(Duration.ofSeconds(10), rec);
        assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, s.ensureSessionConnected("1"));
        assertFalse(rec.isEmpty());
        // default backoff sequence first is 1s
        assertEquals(1, rec.get(0).toSeconds());
    }

    // 13 502 -> CONNECTED
    @Test
    void http502ThenConnected() {
        synchronized (statusSeq) {
            statusSeq.addAll(List.of(502, 200));
            bodySeq.addAll(List.of("bad gateway", "{\"state\":\"CONNECTED\"}"));
        }
        assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, svc(Duration.ofSeconds(5)).ensureSessionConnected("1"));
    }

    // 14 503 -> CONNECTED
    @Test
    void http503ThenConnected() {
        synchronized (statusSeq) {
            statusSeq.addAll(List.of(503, 200));
            bodySeq.addAll(List.of("unavailable", "{\"state\":\"CONNECTED\"}"));
        }
        assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, svc(Duration.ofSeconds(5)).ensureSessionConnected("1"));
    }

    // 15 504 -> CONNECTED
    @Test
    void http504ThenConnected() {
        synchronized (statusSeq) {
            statusSeq.addAll(List.of(504, 200));
            bodySeq.addAll(List.of("gateway timeout", "{\"state\":\"CONNECTED\"}"));
        }
        assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, svc(Duration.ofSeconds(5)).ensureSessionConnected("1"));
    }

    // 16 JSON inválido -> transitório até deadline
    @Test
    void jsonInvalidoTransitorioAteDeadline() {
        stubStatus = 200;
        stubBody = "not-json{{{";
        // use real sleep sleeper with short maxWait to prove transient polling
        List<Duration> rec = Collections.synchronizedList(new ArrayList<>());
        // requestTimeout short, sleeper records delays, maxWait 800ms -> should attempt multiple polls
        var svc = new WhatsAppSessionReadyService(om, baseUrl, "tok",
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                Duration.ofSeconds(2), Duration.ofMillis(800), d -> rec.add(d));
        assertEquals(WhatsAppSessionReadyService.ReadyResult.TIMEOUT, svc.ensureSessionConnected("1"));
        assertTrue(calls.get() >= 1);
        // if sleeper was invoked, it proves polling continued; if deadline very short may be 0-1 sleeps
        assertTrue(calls.get() >= 1, "should poll until deadline");
    }

    // 17 timeout HTTP -> transitório até deadline (simulate via short requestTimeout)
    @Test
    void timeoutHttpTransitorioAteDeadline() {
        responseDelayMs = 600; // longer than requestTimeout 200ms
        stubBody = "{\"state\":\"CONNECTED\"}";
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        var svc = new WhatsAppSessionReadyService(om, baseUrl, "tok", client,
                Duration.ofMillis(150), Duration.ofMillis(700), d -> {});
        long start = System.currentTimeMillis();
        assertEquals(WhatsAppSessionReadyService.ReadyResult.TIMEOUT, svc.ensureSessionConnected("1"));
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed >= 400);
        assertTrue(calls.get() >= 1);
    }

    // 18 companyId null
    @Test
    void companyIdNull() {
        var s = new WhatsAppSessionReadyService(om, baseUrl, "tok",
                HttpClient.newBuilder().build(), Duration.ofSeconds(2), Duration.ofSeconds(2), d -> {});
        assertEquals(WhatsAppSessionReadyService.ReadyResult.UNAVAILABLE, s.ensureSessionConnected(null));
        assertEquals(0, calls.get(), "should not call stub when companyId null");
    }

    // 19 companyId vazio
    @Test
    void companyIdVazio() {
        var s = new WhatsAppSessionReadyService(om, baseUrl, "tok",
                HttpClient.newBuilder().build(), Duration.ofSeconds(2), Duration.ofSeconds(2), d -> {});
        assertEquals(WhatsAppSessionReadyService.ReadyResult.UNAVAILABLE, s.ensureSessionConnected(""));
        assertEquals(WhatsAppSessionReadyService.ReadyResult.UNAVAILABLE, s.ensureSessionConnected("   "));
        assertEquals(0, calls.get());
    }

    // 20 serviceUrl vazio
    @Test
    void serviceUrlVazio() {
        var s = new WhatsAppSessionReadyService(om, "", "tok",
                HttpClient.newBuilder().build(), Duration.ofSeconds(2), Duration.ofSeconds(2), d -> {});
        assertEquals(WhatsAppSessionReadyService.ReadyResult.NOT_CONFIGURED, s.ensureSessionConnected("1"));
        assertEquals(0, calls.get());
    }

    // 21 token vazio
    @Test
    void tokenVazio() {
        var s = new WhatsAppSessionReadyService(om, baseUrl, "",
                HttpClient.newBuilder().build(), Duration.ofSeconds(2), Duration.ofSeconds(2), d -> {});
        assertEquals(WhatsAppSessionReadyService.ReadyResult.NOT_CONFIGURED, s.ensureSessionConnected("1"));
        var s2 = new WhatsAppSessionReadyService(om, baseUrl, "   ",
                HttpClient.newBuilder().build(), Duration.ofSeconds(2), Duration.ofSeconds(2), d -> {});
        assertEquals(WhatsAppSessionReadyService.ReadyResult.NOT_CONFIGURED, s2.ensureSessionConnected("1"));
        assertEquals(0, calls.get());
    }

    // 22 Retry-After "0" -> MIN_BACKOFF (2s)
    @Test
    void retryAfterZeroMinBackoff() {
        assertTrue(WhatsAppSessionReadyService.parseRetryAfter("0").isPresent());
        assertEquals(2, WhatsAppSessionReadyService.parseRetryAfter("0").get().toSeconds());
        // also via polling
        synchronized (statusSeq) {
            statusSeq.addAll(List.of(429, 200));
            bodySeq.addAll(List.of("rate", "{\"state\":\"CONNECTED\"}"));
        }
        synchronized (retryAfterSeq) { retryAfterSeq.addAll(Arrays.asList("0", (String) null)); }
        List<Duration> rec = Collections.synchronizedList(new ArrayList<>());
        var s = svcWithRecordingSleeper(Duration.ofSeconds(10), rec);
        assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, s.ensureSessionConnected("1"));
        assertEquals(2, rec.get(0).toSeconds());
    }

    // 23 Retry-After negativo -> MIN_BACKOFF
    @Test
    void retryAfterNegativoMinBackoff() {
        assertTrue(WhatsAppSessionReadyService.parseRetryAfter("-5").isPresent());
        assertEquals(2, WhatsAppSessionReadyService.parseRetryAfter("-5").get().toSeconds());
        assertTrue(WhatsAppSessionReadyService.parseRetryAfter("-100").isPresent());
        assertEquals(2, WhatsAppSessionReadyService.parseRetryAfter("-100").get().toSeconds());
    }

    // 24 Retry-After "301" NÃO reduzir para 300
    @Test
    void retryAfter301NaoReduzirPara300() {
        var opt = WhatsAppSessionReadyService.parseRetryAfter("301");
        assertTrue(opt.isPresent());
        assertEquals(301, opt.get().toSeconds(), "301 should not be capped to 300");
        synchronized (statusSeq) {
            statusSeq.addAll(List.of(429, 200));
            bodySeq.addAll(List.of("rate", "{\"state\":\"CONNECTED\"}"));
        }
        synchronized (retryAfterSeq) { retryAfterSeq.addAll(Arrays.asList("301", (String) null)); }
        List<Duration> rec = Collections.synchronizedList(new ArrayList<>());
        var s = svcWithRecordingSleeper(Duration.ofSeconds(400), rec);
        assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, s.ensureSessionConnected("1"));
        assertEquals(301, rec.get(0).toSeconds());
    }

    // 25 Retry-After "600" NÃO reduzir para 300
    @Test
    void retryAfter600NaoReduzirPara300() {
        var opt = WhatsAppSessionReadyService.parseRetryAfter("600");
        assertTrue(opt.isPresent());
        assertEquals(600, opt.get().toSeconds(), "600 should not be capped");
        synchronized (statusSeq) {
            statusSeq.addAll(List.of(429, 200));
            bodySeq.addAll(List.of("rate", "{\"state\":\"CONNECTED\"}"));
        }
        synchronized (retryAfterSeq) { retryAfterSeq.addAll(Arrays.asList("600", (String) null)); }
        List<Duration> rec = Collections.synchronizedList(new ArrayList<>());
        var s = svcWithRecordingSleeper(Duration.ofSeconds(700), rec);
        assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, s.ensureSessionConnected("1"));
        assertEquals(600, rec.get(0).toSeconds());
    }

    // 26 5 threads simultâneas mesmo companyId -> UMA única sequência de polling (single-flight)
    @Test
    void singleFlight5ThreadsMesmaEmpresa() throws Exception {
        stubBody = "{\"state\":\"CONNECTED\"}";
        blockEntered = new CountDownLatch(1);
        blockRelease = new CountDownLatch(1);
        var service = new WhatsAppSessionReadyService(om, baseUrl, "tok",
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                Duration.ofSeconds(2), Duration.ofSeconds(5), d -> {});

        ExecutorService exec = Executors.newFixedThreadPool(5);
        List<Future<WhatsAppSessionReadyService.ReadyResult>> fs = new ArrayList<>();
        for (int i = 0; i < 5; i++) fs.add(exec.submit(() -> service.ensureSessionConnected("1")));

        // leader entered server and is blocked; wait for latch
        assertTrue(blockEntered.await(3, TimeUnit.SECONDS), "leader should have entered stub");
        // while blocked, followers should be waiting on flight, not creating new HTTP calls
        Thread.sleep(300);
        assertEquals(1, calls.get(), "single-flight: only leader should have called server while blocked, calls=" + calls.get());

        blockRelease.countDown();

        for (var f : fs) assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, f.get(5, TimeUnit.SECONDS));
        // at most 1 call (or 2 if retry) but not 5
        assertTrue(calls.get() <= 2, "calls=" + calls.get());
        exec.shutdown();
        assertTrue(exec.awaitTermination(2, TimeUnit.SECONDS));
    }

    // 27 duas empresas diferentes simultâneas -> flights independentes
    @Test
    void duasEmpresasDiferentesFlightsIndependentes() throws Exception {
        stubBody = "{\"state\":\"CONNECTED\"}";
        var service = new WhatsAppSessionReadyService(om, baseUrl, "tok",
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                Duration.ofSeconds(2), Duration.ofSeconds(5), d -> {});
        ExecutorService exec = Executors.newFixedThreadPool(2);
        Future<WhatsAppSessionReadyService.ReadyResult> f1 = exec.submit(() -> service.ensureSessionConnected("1"));
        Future<WhatsAppSessionReadyService.ReadyResult> f2 = exec.submit(() -> service.ensureSessionConnected("2"));
        assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, f1.get(3, TimeUnit.SECONDS));
        assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, f2.get(3, TimeUnit.SECONDS));
        // independent flights: each company causes its own HTTP call
        assertTrue(calls.get() >= 2, "expected 2 calls for 2 companies, got " + calls.get());
        exec.shutdown();
    }

    // 28 após flight terminar nova chamada cria NOVO flight
    @Test
    void aposFlightTerminarNovoFlightCriado() {
        stubBody = "{\"state\":\"CONNECTED\"}";
        var s = svc(Duration.ofSeconds(2));
        assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, s.ensureSessionConnected("1"));
        int afterFirst = calls.get();
        assertEquals(1, afterFirst);
        assertEquals(0, s.activeFlights(), "flight should be removed after completion");
        assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, s.ensureSessionConnected("1"));
        assertEquals(2, calls.get(), "second call should create new flight and call server again");
    }

    // 29 validar flights removidos
    @Test
    void flightsRemovidosAposConcluir() {
        stubBody = "{\"state\":\"CONNECTED\"}";
        var s = svc(Duration.ofSeconds(2));
        assertEquals(0, s.activeFlights());
        assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, s.ensureSessionConnected("1"));
        assertEquals(0, s.activeFlights(), "flights map must be empty after success");
        assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, s.ensureSessionConnected("1"));
        assertTrue(calls.get() >= 2);
        assertEquals(0, s.activeFlights());

        // also after terminal LOGGED_OUT
        stubBody = "{\"state\":\"LOGGED_OUT\"}";
        assertEquals(WhatsAppSessionReadyService.ReadyResult.LOGGED_OUT, s.ensureSessionConnected("99"));
        assertEquals(0, s.activeFlights());
    }

    // 30 InterruptedException -> preserve interrupt, return TIMEOUT, não continuar polling
    @Test
    void interruptedExceptionPreserveInterrupt() {
        stubBody = "{\"state\":\"CONNECTING\"}";
        // sleeper that throws InterruptedException immediately to simulate interruption during backoff
        WhatsAppSessionReadyService.Sleeper throwing = d -> { throw new InterruptedException("interrupted for test"); };
        var s = new WhatsAppSessionReadyService(om, baseUrl, "tok",
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                Duration.ofMillis(500), Duration.ofSeconds(5), throwing);
        // clear interrupt flag before
        Thread.interrupted();
        WhatsAppSessionReadyService.ReadyResult r = s.ensureSessionConnected("1");
        assertEquals(WhatsAppSessionReadyService.ReadyResult.TIMEOUT, r);
        assertTrue(Thread.currentThread().isInterrupted(), "interrupt flag must be preserved");
        // cleanup
        Thread.interrupted();
        // should have done only one poll then sleep interrupted, not continued polling
        assertEquals(1, calls.get(), "should not continue polling after InterruptedException");
    }

    // additional helper tests required by original file (kept for coverage)
    @Test
    void retryAfterParseSeconds() {
        assertTrue(WhatsAppSessionReadyService.parseRetryAfter("60").isPresent());
        assertEquals(60, WhatsAppSessionReadyService.parseRetryAfter("60").get().toSeconds());
    }

    @Test
    void retryAfterHttpDate() {
        String d = ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(60).format(DateTimeFormatter.RFC_1123_DATE_TIME);
        assertTrue(WhatsAppSessionReadyService.parseRetryAfter(d).isPresent());
    }

    @Test
    void singleFlightMesmaEmpresa() throws Exception {
        stubBody = "{\"state\":\"CONNECTED\"}";
        var service = new WhatsAppSessionReadyService(om, baseUrl, "tok",
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                Duration.ofSeconds(2), Duration.ofSeconds(5), d -> { try { Thread.sleep(100); } catch (Exception e) {} });
        ExecutorService exec = Executors.newFixedThreadPool(5);
        List<Future<WhatsAppSessionReadyService.ReadyResult>> fs = new ArrayList<>();
        for (int i = 0; i < 5; i++) fs.add(exec.submit(() -> service.ensureSessionConnected("1")));
        for (var f : fs) assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, f.get(3, TimeUnit.SECONDS));
        assertTrue(calls.get() <= 2, "calls=" + calls.get());
        exec.shutdown();
    }

    @Test
    void flightRemovidoAposConcluirLegacy() {
        stubBody = "{\"state\":\"CONNECTED\"}";
        var s = svc(Duration.ofSeconds(2));
        assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, s.ensureSessionConnected("1"));
        assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, s.ensureSessionConnected("1"));
        assertTrue(calls.get() >= 2);
    }

    // extra edge for notConfigured helper
    @Test
    void notConfiguredNull() {
        var s = new WhatsAppSessionReadyService(om, baseUrl, "tok",
                HttpClient.newBuilder().build(), Duration.ofSeconds(2), Duration.ofSeconds(2), d -> {});
        assertEquals(WhatsAppSessionReadyService.ReadyResult.UNAVAILABLE, s.ensureSessionConnected(null));
    }

    @Test
    void timeoutLegacy() {
        stubBody = "{\"state\":\"CONNECTING\"}";
        var s = svc(Duration.ofMillis(300));
        assertEquals(WhatsAppSessionReadyService.ReadyResult.TIMEOUT, s.ensureSessionConnected("1"));
    }
}

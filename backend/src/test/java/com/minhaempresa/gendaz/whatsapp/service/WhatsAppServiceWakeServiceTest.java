package com.minhaempresa.gendaz.whatsapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WhatsAppServiceWakeServiceTest {

    private HttpServer stub;
    private String baseUrl;
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int stubStatus = 200;
    private final List<Integer> statusSequence = new ArrayList<>();
    private final AtomicInteger seqIndex = new AtomicInteger(0);
    private volatile String lastPath;
    private volatile String lastAuth;

    @BeforeEach
    void subirStub() throws IOException {
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/", exchange -> {
            calls.incrementAndGet();
            lastPath = exchange.getRequestURI().getPath();
            lastAuth = exchange.getRequestHeaders().getFirst("Authorization");
            int status;
            synchronized (statusSequence) {
                if (!statusSequence.isEmpty()) {
                    int idx = seqIndex.getAndIncrement();
                    status = idx < statusSequence.size() ? statusSequence.get(idx) : statusSequence.get(statusSequence.size() - 1);
                } else {
                    status = stubStatus;
                }
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
        seqIndex.set(0);
        calls.set(0);
        lastPath = null;
        lastAuth = null;
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

    @Test
    void retry_502_502_200_paraNo200() {
        synchronized (statusSequence) {
            statusSequence.addAll(List.of(502, 502, 200));
        }
        WhatsAppServiceWakeService svc = serviceWithNoSleep(baseUrl, Duration.ofSeconds(2));
        svc.doWake();
        assertEquals(3, calls.get());
        svc.destroy();
    }

    @Test
    void erroTemporarioAteEsgotarJanela_naoLancaException() {
        stubStatus = 502;
        WhatsAppServiceWakeService svc = serviceWithNoSleep(baseUrl, Duration.ofMillis(50));
        svc.doWake();
        assertTrue(calls.get() >= 1);
        svc.destroy();
    }

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

    @Test
    void respostaNaoTransitoria_404_abortaSemRetry() {
        stubStatus = 404;
        WhatsAppServiceWakeService svc = serviceWithNoSleep(baseUrl, Duration.ofSeconds(2));
        svc.doWake();
        assertEquals(1, calls.get());
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

    @Test
    void urlComBarraNoFinal_normalizada() {
        WhatsAppServiceWakeService svc = serviceWithNoSleep(baseUrl + "///", Duration.ofSeconds(1));
        stubStatus = 200;
        svc.doWake();
        assertEquals("/health", lastPath);
        svc.destroy();
    }

    @Test
    void health_naoUsaToken() {
        WhatsAppServiceWakeService svc = serviceWithNoSleep(baseUrl, Duration.ofSeconds(1));
        stubStatus = 200;
        svc.doWake();
        assertTrue(lastAuth == null, "Authorization deve ser null no /health");
        svc.destroy();
    }
}

package com.minhaempresa.gendaz.whatsapp.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
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
    private volatile String lastRetryAfter;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void up() throws Exception {
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/", ex -> {
            calls.incrementAndGet();
            int s; String b;
            synchronized(statusSeq){
                if(!statusSeq.isEmpty()){
                    int i=idx.getAndIncrement();
                    s = i<statusSeq.size()?statusSeq.get(i):statusSeq.get(statusSeq.size()-1);
                    b = i<bodySeq.size()?bodySeq.get(i):bodySeq.get(bodySeq.size()-1);
                } else { s=stubStatus; b=stubBody; }
            }
            if(ex.getRequestHeaders().getFirst("Retry-After")!=null) lastRetryAfter=ex.getRequestHeaders().getFirst("Retry-After");
            // echo Retry-After if stub wants to send: set via header map
            String ra = ex.getRequestURI().getQuery();
            byte[] body = b.getBytes(StandardCharsets.UTF_8);
            if(statusSeq.contains(429) || s==429){
                // if test set header via stubBody marker, handle separately via exchange attribute
            }
            ex.getResponseHeaders().set("Content-Type","application/json");
            // allow test to inject Retry-After response header via threadlocal
            String header = (String) ex.getAttribute("Retry-After");
            // Simplified: use stubBody prefix
            ex.sendResponseHeaders(s, body.length);
            try(OutputStream os=ex.getResponseBody()){ os.write(body); }
        });
        stub.start();
        baseUrl="http://127.0.0.1:"+stub.getAddress().getPort();
    }
    @AfterEach void down(){ stub.stop(0); statusSeq.clear(); bodySeq.clear(); idx.set(0); calls.set(0); }

    WhatsAppSessionReadyService svc(Duration maxWait){
        return new WhatsAppSessionReadyService(om, baseUrl, "tok",
                java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                Duration.ofSeconds(2), maxWait, d->{} );
    }

    @Test void connectedImediato(){ stubBody="{\"companyId\":\"1\",\"state\":\"CONNECTED\"}"; assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, svc(Duration.ofSeconds(2)).ensureSessionConnected("1")); }
    @Test void connectingThenConnected(){
        synchronized(statusSeq){ statusSeq.addAll(List.of(200,200)); bodySeq.addAll(List.of("{\"state\":\"CONNECTING\"}","{\"state\":\"CONNECTED\"}")); }
        var s = new WhatsAppSessionReadyService(om, baseUrl, "tok", java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(), Duration.ofSeconds(2), Duration.ofSeconds(5), d->{});
        assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, s.ensureSessionConnected("1"));
    }
    @Test void loggedOutTerminal(){ stubBody="{\"state\":\"LOGGED_OUT\"}"; assertEquals(WhatsAppSessionReadyService.ReadyResult.LOGGED_OUT, svc(Duration.ofSeconds(2)).ensureSessionConnected("1")); }
    @Test void auth401(){ stubStatus=401; assertEquals(WhatsAppSessionReadyService.ReadyResult.AUTH_ERROR, svc(Duration.ofSeconds(2)).ensureSessionConnected("1")); }
    @Test void notConfiguredNull(){ var s = new WhatsAppSessionReadyService(om, baseUrl, "tok", java.net.http.HttpClient.newBuilder().build(), Duration.ofSeconds(2), Duration.ofSeconds(2), d->{}); assertEquals(WhatsAppSessionReadyService.ReadyResult.UNAVAILABLE, s.ensureSessionConnected(null)); }
    @Test void tokenVazio(){ var s = new WhatsAppSessionReadyService(om, "", "", java.net.http.HttpClient.newBuilder().build(), Duration.ofSeconds(2), Duration.ofSeconds(2), d->{}); assertEquals(WhatsAppSessionReadyService.ReadyResult.NOT_CONFIGURED, s.ensureSessionConnected("1")); }
    @Test void urlVazia(){ var s = new WhatsAppSessionReadyService(om, "", "tok", java.net.http.HttpClient.newBuilder().build(), Duration.ofSeconds(2), Duration.ofSeconds(2), d->{}); assertEquals(WhatsAppSessionReadyService.ReadyResult.NOT_CONFIGURED, s.ensureSessionConnected("1")); }
    @Test void timeout(){ stubBody="{\"state\":\"CONNECTING\"}"; var s=svc(Duration.ofMillis(200)); assertEquals(WhatsAppSessionReadyService.ReadyResult.TIMEOUT, s.ensureSessionConnected("1")); }
    @Test void singleFlightMesmaEmpresa() throws Exception {
        stubBody="{\"state\":\"CONNECTED\"}";
        var service = new WhatsAppSessionReadyService(om, baseUrl, "tok", java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(), Duration.ofSeconds(2), Duration.ofSeconds(5), d->{ try{Thread.sleep(100);}catch(Exception e){}});
        ExecutorService exec=Executors.newFixedThreadPool(5);
        List<Future<WhatsAppSessionReadyService.ReadyResult>> fs=new ArrayList<>();
        for(int i=0;i<5;i++) fs.add(exec.submit(()->service.ensureSessionConnected("1")));
        for(var f: fs) assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, f.get(3, TimeUnit.SECONDS));
        // due to single-flight, calls should be 1 (or few) not 5 independent loops
        assertTrue(calls.get()<=2, "calls="+calls.get());
        exec.shutdown();
    }
    @Test void retryAfterParseSeconds(){ assertTrue(WhatsAppSessionReadyService.parseRetryAfter("60").isPresent()); assertEquals(60, WhatsAppSessionReadyService.parseRetryAfter("60").get().toSeconds()); }
    @Test void retryAfterHttpDate(){ String d= ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(60).format(DateTimeFormatter.RFC_1123_DATE_TIME); assertTrue(WhatsAppSessionReadyService.parseRetryAfter(d).isPresent()); }
    @Test void flightRemovidoAposConcluir(){
        stubBody="{\"state\":\"CONNECTED\"}";
        var s=svc(Duration.ofSeconds(2));
        assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, s.ensureSessionConnected("1"));
        assertEquals(WhatsAppSessionReadyService.ReadyResult.CONNECTED, s.ensureSessionConnected("1"));
        assertTrue(calls.get()>=2);
    }
}

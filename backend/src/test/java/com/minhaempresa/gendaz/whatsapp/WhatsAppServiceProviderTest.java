package com.minhaempresa.gendaz.whatsapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhaempresa.gendaz.shared.audit.OutboundTrafficAuditService;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WhatsAppServiceProviderTest {

    private static final String TOKEN = "token-de-teste-123";

    private HttpServer stub;
    private String baseUrl;
    private volatile int stubStatus = 200;
    private volatile String stubBody = "{}";
    private volatile String lastMethod;
    private volatile String lastPath;
    private volatile String lastAuth;
    private final AtomicInteger calls = new AtomicInteger();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OutboundTrafficAuditService audit = new OutboundTrafficAuditService(false, 600000L);

    @BeforeEach
    void subirStub() throws IOException {
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/", exchange -> {
            calls.incrementAndGet();
            lastMethod = exchange.getRequestMethod();
            lastPath = exchange.getRequestURI().getPath();
            lastAuth = exchange.getRequestHeaders().getFirst("Authorization");
            // Soneca opcional para teste de timeout, via corpo especial.
            if (stubBody.startsWith("SLEEP:")) {
                try {
                    Thread.sleep(Long.parseLong(stubBody.substring("SLEEP:".length())));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] resposta = stubBody.startsWith("SLEEP:") ? new byte[0] : stubBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(stubStatus, resposta.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resposta);
            }
        });
        stub.start();
        baseUrl = "http://127.0.0.1:" + stub.getAddress().getPort();
    }

    @AfterEach
    void pararStub() {
        stub.stop(0);
    }

    private WhatsAppServiceProvider provider() {
        return new WhatsAppServiceProvider(objectMapper, audit, baseUrl, TOKEN,
                Duration.ofSeconds(2), Duration.ofSeconds(2));
    }

    private static final String STATUS_JSON = """
            {"companyId":"empresa-teste","state":"CONNECTING","hasQr":true,
             "qrUpdatedAt":"2026-09-07T10:00:00Z","connectedAt":null,
             "reconnectAttempts":0,"lastDisconnectCode":null}""";

    @Test
    void semConfiguracao_retornaNotConfigured_semHttp() {
        WhatsAppServiceProvider p = new WhatsAppServiceProvider(objectMapper, audit, "", "",
                Duration.ofSeconds(2), Duration.ofSeconds(2));
        assertFalse(p.disponivel());
        assertEquals(WhatsAppOperationStatus.NOT_CONFIGURED, p.conectar("empresa-1").getStatus());
        assertEquals(WhatsAppOperationStatus.NOT_CONFIGURED, p.consultarStatus("empresa-1").getStatus());
        assertEquals(WhatsAppOperationStatus.NOT_CONFIGURED, p.obterQr("empresa-1").getStatus());
        assertEquals(WhatsAppOperationStatus.NOT_CONFIGURED, p.logout("empresa-1").getStatus());
        assertEquals(0, calls.get());
    }

    @Test
    void semConfiguracao_naoQuebraStartup() {
        WhatsAppServiceProvider p1 = new WhatsAppServiceProvider(objectMapper, audit, null, null,
                Duration.ofSeconds(2), Duration.ofSeconds(2));
        assertFalse(p1.disponivel());
        WhatsAppServiceProvider p2 = new WhatsAppServiceProvider(objectMapper, audit, "http://[invalido", TOKEN,
                Duration.ofSeconds(2), Duration.ofSeconds(2));
        assertTrue(p2.disponivel());
    }

    @Test
    void connect_usaPost_pathCorreto_eBearer() {
        stubBody = STATUS_JSON;
        WhatsAppResult<WhatsAppSessionStatus> resultado = provider().conectar("empresa-teste");
        assertTrue(resultado.isSuccess());
        assertEquals("POST", lastMethod);
        assertEquals("/internal/whatsapp/sessions/empresa-teste/connect", lastPath);
        assertEquals("Bearer " + TOKEN, lastAuth);
        assertEquals("empresa-teste", resultado.getData().getCompanyId());
        assertEquals("CONNECTING", resultado.getData().getState());
        assertTrue(resultado.getData().isHasQr());
        assertEquals("2026-09-07T10:00:00Z", resultado.getData().getQrUpdatedAt());
        assertNull(resultado.getData().getConnectedAt());
        assertEquals(0, resultado.getData().getReconnectAttempts());
        assertNull(resultado.getData().getLastDisconnectCode());
    }

    @Test
    void status_usaGet_eInterpretaCampos() {
        stubBody = """
                {"companyId":"abc","state":"CONNECTED","hasQr":false,
                 "qrUpdatedAt":null,"connectedAt":"2026-09-07T11:00:00Z",
                 "reconnectAttempts":3,"lastDisconnectCode":408}""";
        WhatsAppResult<WhatsAppSessionStatus> resultado = provider().consultarStatus("abc");
        assertTrue(resultado.isSuccess());
        assertEquals("GET", lastMethod);
        assertEquals("/internal/whatsapp/sessions/abc/status", lastPath);
        assertEquals("CONNECTED", resultado.getData().getState());
        assertEquals(Integer.valueOf(408), resultado.getData().getLastDisconnectCode());
    }

    @Test
    void qr_200_interpretaQr() {
        stubBody = "{\"qr\":\"QR-FAKE-123\",\"updatedAt\":\"2026-09-07T10:00:01Z\"}";
        WhatsAppResult<WhatsAppQr> resultado = provider().obterQr("empresa-teste");
        assertTrue(resultado.isSuccess());
        assertEquals("GET", lastMethod);
        assertEquals("/internal/whatsapp/sessions/empresa-teste/qr", lastPath);
        assertEquals("QR-FAKE-123", resultado.getData().getQr());
    }

    @Test
    void qr_404_retornaQrUnavailable() {
        stubStatus = 404;
        stubBody = "{\"error\":\"qr_unavailable\",\"state\":\"CONNECTED\"}";
        WhatsAppResult<WhatsAppQr> resultado = provider().obterQr("empresa-teste");
        assertEquals(WhatsAppOperationStatus.QR_UNAVAILABLE, resultado.getStatus());
        assertNull(resultado.getData());
    }

    @Test
    void logout_usaPost_eInterpreta200() {
        stubBody = STATUS_JSON;
        WhatsAppResult<WhatsAppSessionStatus> resultado = provider().logout("empresa-teste");
        assertTrue(resultado.isSuccess());
        assertEquals("POST", lastMethod);
        assertEquals("/internal/whatsapp/sessions/empresa-teste/logout", lastPath);
    }

    @Test
    void mapeia400_401_500_503() {
        stubStatus = 400;
        stubBody = "{\"error\":\"invalid_company_id\"}";
        assertEquals(WhatsAppOperationStatus.INVALID_COMPANY_ID, provider().consultarStatus("x").getStatus());

        stubStatus = 401;
        stubBody = "{\"error\":\"unauthorized\"}";
        assertEquals(WhatsAppOperationStatus.UNAUTHORIZED, provider().consultarStatus("x").getStatus());

        stubStatus = 500;
        stubBody = "{\"error\":\"internal_error\"}";
        assertEquals(WhatsAppOperationStatus.INTERNAL_ERROR, provider().consultarStatus("x").getStatus());

        stubStatus = 503;
        stubBody = "{\"error\":\"service_unavailable\"}";
        assertEquals(WhatsAppOperationStatus.UNAVAILABLE, provider().consultarStatus("x").getStatus());

        stubStatus = 418;
        stubBody = "??";
        assertEquals(WhatsAppOperationStatus.INTERNAL_ERROR, provider().consultarStatus("x").getStatus());
    }

    @Test
    void jsonInvalido_naoDerrubaAplicacao() {
        stubStatus = 200;
        stubBody = "isto-nao-e-json";
        WhatsAppResult<WhatsAppSessionStatus> resultado = provider().consultarStatus("x");
        assertEquals(WhatsAppOperationStatus.INTERNAL_ERROR, resultado.getStatus());
    }

    @Test
    void timeout_retornaUnavailable() {
        stubStatus = 200;
        stubBody = "SLEEP:5000";
        WhatsAppServiceProvider lento = new WhatsAppServiceProvider(objectMapper, audit, baseUrl, TOKEN,
                Duration.ofSeconds(2), Duration.ofMillis(500));
        assertEquals(WhatsAppOperationStatus.UNAVAILABLE, lento.consultarStatus("x").getStatus());
    }

    @Test
    void conexaoRecusada_retornaUnavailable() throws IOException {
        HttpServer fechado = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int portaLivre = fechado.getAddress().getPort();
        fechado.stop(0);
        WhatsAppServiceProvider p = new WhatsAppServiceProvider(objectMapper, audit,
                "http://127.0.0.1:" + portaLivre, TOKEN, Duration.ofSeconds(2), Duration.ofSeconds(2));
        assertEquals(WhatsAppOperationStatus.UNAVAILABLE, p.consultarStatus("x").getStatus());
    }

    @Test
    void interruptedException_restauraInterrupt() {
        Thread.currentThread().interrupt();
        WhatsAppResult<WhatsAppSessionStatus> resultado = provider().consultarStatus("x");
        assertEquals(WhatsAppOperationStatus.UNAVAILABLE, resultado.getStatus());
        assertTrue(Thread.interrupted(), "flag de interrupt deve ser restaurada");
    }

    @Test
    void companyIdInvalido_rejeitadoAntesDoHttp() {
        WhatsAppServiceProvider p = provider();
        String longo = "a".repeat(65);
        for (String invalido : new String[]{"../teste", "/teste", "com espaco", "", "   ", longo, "a.b", null}) {
            assertEquals(WhatsAppOperationStatus.INVALID_COMPANY_ID, p.consultarStatus(invalido).getStatus(),
                    "deveria rejeitar: '" + invalido + "'");
            assertEquals(WhatsAppOperationStatus.INVALID_COMPANY_ID, p.conectar(invalido).getStatus());
            assertEquals(WhatsAppOperationStatus.INVALID_COMPANY_ID, p.obterQr(invalido).getStatus());
            assertEquals(WhatsAppOperationStatus.INVALID_COMPANY_ID, p.logout(invalido).getStatus());
        }
        assertEquals(0, calls.get());
    }

    @Test
    void companyIdValido_aceito() {
        stubBody = STATUS_JSON;
        WhatsAppServiceProvider p = provider();
        for (String valido : new String[]{"123", "empresa_1", "empresa-teste"}) {
            assertTrue(p.consultarStatus(valido).isSuccess(), "deveria aceitar: '" + valido + "'");
        }
        assertEquals(3, calls.get());
    }

    @Test
    void baseUrlComBarraDupla_normalizada() {
        stubBody = STATUS_JSON;
        WhatsAppServiceProvider p = new WhatsAppServiceProvider(objectMapper, audit, baseUrl + "///", TOKEN,
                Duration.ofSeconds(2), Duration.ofSeconds(2));
        assertTrue(p.consultarStatus("x").isSuccess());
        assertEquals("/internal/whatsapp/sessions/x/status", lastPath);
    }

    @Test
    void tokenEnviado_masNaoVazaNosRetornos() {
        stubBody = STATUS_JSON;
        WhatsAppServiceProvider p = provider();
        WhatsAppResult<WhatsAppSessionStatus> status = p.consultarStatus("x");
        assertTrue(status.isSuccess());
        // O token e enviado no header...
        assertEquals("Bearer " + TOKEN, lastAuth);
        // ...mas nao aparece em nenhum dado retornado.
        WhatsAppSessionStatus dados = status.getData();
        String concatenado = "" + dados.getCompanyId() + dados.getState()
                + dados.getQrUpdatedAt() + dados.getConnectedAt()
                + dados.getReconnectAttempts() + dados.getLastDisconnectCode();
        assertTrue(concatenadoNaoContemToken(concatenado));
    }

    private boolean concatenadoNaoContemToken(String concatenado) {
        return !concatenado.contains(TOKEN);
    }
}

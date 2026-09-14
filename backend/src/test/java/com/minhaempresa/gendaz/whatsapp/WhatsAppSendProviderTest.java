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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WhatsAppSendProviderTest {

    private static final String TOKEN = "token-de-teste-123";

    private HttpServer stub;
    private String baseUrl;
    private volatile int stubStatus = 200;
    private volatile String stubBody = "{}";
    private volatile String lastMethod;
    private volatile String lastPath;
    private volatile String lastAuth;
    private volatile String lastContentType;
    private volatile String lastBody;
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
            lastContentType = exchange.getRequestHeaders().getFirst("Content-Type");
            lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
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

    @Test
    void enviaPostCorretoComAuthEBody() {
        stubBody = "{\"status\":\"sent\",\"messageId\":\"WAMID-1\",\"requestId\":\"req-1\"}";
        WhatsAppSendResult resultado = provider().enviarTexto("42", "5511999999999", "Ola", "req-1");

        assertTrue(resultado.isSent());
        assertEquals(WhatsAppSendStatus.SENT, resultado.getStatus());
        assertEquals("WAMID-1", resultado.getMessageId());
        assertEquals("POST", lastMethod);
        assertEquals("/internal/whatsapp/sessions/42/messages/text", lastPath);
        assertEquals("Bearer " + TOKEN, lastAuth);
        assertTrue(lastContentType.contains("application/json"));
        Map<?, ?> body;
        try {
            body = objectMapper.readValue(lastBody, Map.class);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertEquals("5511999999999", body.get("recipient"));
        assertEquals("Ola", body.get("text"));
        assertEquals("req-1", body.get("requestId"));
    }

    @Test
    void mapeia400ParaErrosTerminais() {
        stubStatus = 400;
        stubBody = "{\"error\":\"invalid_recipient\"}";
        assertEquals(WhatsAppSendStatus.INVALID_RECIPIENT,
                provider().enviarTexto("42", "x", "Ola", "r").getStatus());
        stubBody = "{\"error\":\"invalid_message\"}";
        assertEquals(WhatsAppSendStatus.INVALID_MESSAGE,
                provider().enviarTexto("42", "5511", "Ola", "r").getStatus());
    }

    @Test
    void mapeia401_409_500_503() {
        stubStatus = 401;
        stubBody = "{\"error\":\"unauthorized\"}";
        assertEquals(WhatsAppSendStatus.UNAUTHORIZED,
                provider().enviarTexto("42", "5511", "Ola", "r").getStatus());

        stubStatus = 409;
        stubBody = "{\"error\":\"session_not_connected\",\"state\":\"DISCONNECTED\"}";
        WhatsAppSendResult naoConectado = provider().enviarTexto("42", "5511", "Ola", "r");
        assertEquals(WhatsAppSendStatus.SESSION_NOT_CONNECTED, naoConectado.getStatus());
        assertTrue(naoConectado.getStatus().isRetryable());

        stubStatus = 500;
        stubBody = "{\"error\":\"provider_send_failed\"}";
        WhatsAppSendResult ambiguo = provider().enviarTexto("42", "5511", "Ola", "r");
        assertEquals(WhatsAppSendStatus.DELIVERY_UNKNOWN, ambiguo.getStatus());
        assertFalse(ambiguo.getStatus().isRetryable());

        stubStatus = 500;
        stubBody = "{\"error\":\"outro_erro_qualquer\"}";
        WhatsAppSendResult provider = provider().enviarTexto("42", "5511", "Ola", "r");
        assertEquals(WhatsAppSendStatus.PROVIDER_ERROR, provider.getStatus());
        assertFalse(provider.getStatus().isRetryable());

        stubStatus = 503;
        stubBody = "{\"error\":\"service_unavailable\"}";
        WhatsAppSendResult indisponivel = provider().enviarTexto("42", "5511", "Ola", "r");
        assertEquals(WhatsAppSendStatus.SERVICE_UNAVAILABLE, indisponivel.getStatus());
        assertTrue(indisponivel.getStatus().isRetryable());
    }

    @Test
    void conexaoRecusadaEServiceUnavailable() {
        WhatsAppServiceProvider fechado = new WhatsAppServiceProvider(objectMapper, audit,
                "http://127.0.0.1:1", TOKEN, Duration.ofSeconds(2), Duration.ofSeconds(2));
        WhatsAppSendResult resultado = fechado.enviarTexto("42", "5511", "Ola", "r");
        assertEquals(WhatsAppSendStatus.SERVICE_UNAVAILABLE, resultado.getStatus());
        assertTrue(resultado.getStatus().isRetryable());
    }

    @Test
    void requestTimeoutEDeliveryUnknown() {
        stubStatus = 200;
        stubBody = "SLEEP:5000";
        WhatsAppServiceProvider impaciente = new WhatsAppServiceProvider(objectMapper, audit,
                baseUrl, TOKEN, Duration.ofSeconds(2), Duration.ofMillis(300));
        WhatsAppSendResult resultado = impaciente.enviarTexto("42", "5511", "Ola", "r");
        assertEquals(WhatsAppSendStatus.DELIVERY_UNKNOWN, resultado.getStatus());
        assertFalse(resultado.getStatus().isRetryable());
    }

    @Test
    void jsonInvalidoNo200EDeliveryUnknown() {
        stubStatus = 200;
        stubBody = "nao-json";
        assertEquals(WhatsAppSendStatus.DELIVERY_UNKNOWN,
                provider().enviarTexto("42", "5511", "Ola", "r").getStatus());
    }

    @Test
    void semConfiguracaoRetornaNotConfiguredSemHttp() {
        WhatsAppServiceProvider p = new WhatsAppServiceProvider(objectMapper, audit, "", "",
                Duration.ofSeconds(2), Duration.ofSeconds(2));
        assertEquals(WhatsAppSendStatus.NOT_CONFIGURED,
                p.enviarTexto("42", "5511", "Ola", "r").getStatus());
        assertEquals(0, calls.get());
    }

    @Test
    void statusTerminaisNaoSaoRetryable() {
        for (WhatsAppSendStatus status : new WhatsAppSendStatus[]{
                WhatsAppSendStatus.INVALID_RECIPIENT,
                WhatsAppSendStatus.INVALID_MESSAGE,
                WhatsAppSendStatus.UNAUTHORIZED,
                WhatsAppSendStatus.NOT_CONFIGURED,
                WhatsAppSendStatus.DELIVERY_UNKNOWN,
                WhatsAppSendStatus.PROVIDER_ERROR}) {
            assertFalse(status.isRetryable(), status + " nao deveria ser retryable");
        }
        assertNull(WhatsAppSendResult.erro(WhatsAppSendStatus.PROVIDER_ERROR).getMessageId());
    }
}

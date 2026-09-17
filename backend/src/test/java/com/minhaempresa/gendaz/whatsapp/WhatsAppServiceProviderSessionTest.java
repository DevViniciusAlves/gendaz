package com.minhaempresa.gendaz.whatsapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhaempresa.gendaz.shared.audit.OutboundTrafficAuditService;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppServiceWakeService;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppSessionReadyService;
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

class WhatsAppServiceProviderSessionTest {

    private static final String TOKEN = "token-de-teste-123";

    private HttpServer stub;
    private String baseUrl;
    private volatile int stubStatus = 200;
    private volatile String stubBody = "{\"messageId\":\"mid-123\"}";
    private volatile String lastMethod;
    private volatile String lastPath;
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
            byte[] resposta = stubBody.getBytes(StandardCharsets.UTF_8);
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

    private WhatsAppServiceProvider provider(WhatsAppServiceWakeService wake, WhatsAppSessionReadyService sessionReady) {
        return new WhatsAppServiceProvider(objectMapper, audit, baseUrl, TOKEN,
                Duration.ofSeconds(2), Duration.ofSeconds(2), wake, sessionReady);
    }

    private WhatsAppSessionStatus connectedStatus() {
        WhatsAppSessionStatus s = new WhatsAppSessionStatus();
        s.setCompanyId("empresa-teste");
        s.setState("CONNECTED");
        s.setHasQr(false);
        return s;
    }

    // ========== enviarTexto ==========

    @Test
    void enviarTexto_sessionReadyConnected_executaPost() {
        WhatsAppSessionReadyService sessionReady = mock(WhatsAppSessionReadyService.class);
        when(sessionReady.ensureSessionConnected("empresa-teste"))
                .thenReturn(WhatsAppSessionReadyService.ReadyResult.CONNECTED);
        WhatsAppServiceWakeService wake = mock(WhatsAppServiceWakeService.class);

        stubStatus = 200;
        stubBody = "{\"messageId\":\"mid-abc\"}";

        WhatsAppSendResult result = provider(wake, sessionReady)
                .enviarTexto("empresa-teste", "5511999999999", "ola", "req-1");

        assertEquals(WhatsAppSendStatus.SENT, result.getStatus());
        assertEquals(1, calls.get());
        assertEquals("POST", lastMethod);
        assertEquals("/internal/whatsapp/sessions/empresa-teste/messages/text", lastPath);
        verify(sessionReady).ensureSessionConnected("empresa-teste");
    }

    @Test
    void enviarTexto_loggedOut_naoFazPost_statusSessionNotConnected() {
        WhatsAppSessionReadyService sessionReady = mock(WhatsAppSessionReadyService.class);
        when(sessionReady.ensureSessionConnected(anyString()))
                .thenReturn(WhatsAppSessionReadyService.ReadyResult.LOGGED_OUT);

        WhatsAppSendResult result = provider(mock(WhatsAppServiceWakeService.class), sessionReady)
                .enviarTexto("empresa-teste", "5511999999999", "ola", "req-1");

        assertEquals(WhatsAppSendStatus.SESSION_NOT_CONNECTED, result.getStatus());
        assertEquals(0, calls.get());
    }

    @Test
    void enviarTexto_timeout_naoFazPost() {
        WhatsAppSessionReadyService sessionReady = mock(WhatsAppSessionReadyService.class);
        when(sessionReady.ensureSessionConnected(anyString()))
                .thenReturn(WhatsAppSessionReadyService.ReadyResult.TIMEOUT);

        WhatsAppSendResult result = provider(mock(WhatsAppServiceWakeService.class), sessionReady)
                .enviarTexto("empresa-teste", "5511999999999", "ola", "req-1");

        assertEquals(WhatsAppSendStatus.SESSION_NOT_CONNECTED, result.getStatus());
        assertEquals(0, calls.get());
    }

    @Test
    void enviarTexto_authError_naoFazPost_unauthorized() {
        WhatsAppSessionReadyService sessionReady = mock(WhatsAppSessionReadyService.class);
        when(sessionReady.ensureSessionConnected(anyString()))
                .thenReturn(WhatsAppSessionReadyService.ReadyResult.AUTH_ERROR);

        WhatsAppSendResult result = provider(mock(WhatsAppServiceWakeService.class), sessionReady)
                .enviarTexto("empresa-teste", "5511999999999", "ola", "req-1");

        assertEquals(WhatsAppSendStatus.UNAUTHORIZED, result.getStatus());
        assertEquals(0, calls.get());
    }

    @Test
    void enviarTexto_unavailable_naoFazPost_serviceUnavailable() {
        WhatsAppSessionReadyService sessionReady = mock(WhatsAppSessionReadyService.class);
        when(sessionReady.ensureSessionConnected(anyString()))
                .thenReturn(WhatsAppSessionReadyService.ReadyResult.UNAVAILABLE);

        WhatsAppSendResult result = provider(mock(WhatsAppServiceWakeService.class), sessionReady)
                .enviarTexto("empresa-teste", "5511999999999", "ola", "req-1");

        assertEquals(WhatsAppSendStatus.SERVICE_UNAVAILABLE, result.getStatus());
        assertEquals(0, calls.get());
    }

    @Test
    void enviarTexto_notConfigured_naoFazPost() {
        WhatsAppSessionReadyService sessionReady = mock(WhatsAppSessionReadyService.class);
        when(sessionReady.ensureSessionConnected(anyString()))
                .thenReturn(WhatsAppSessionReadyService.ReadyResult.NOT_CONFIGURED);

        WhatsAppSendResult result = provider(mock(WhatsAppServiceWakeService.class), sessionReady)
                .enviarTexto("empresa-teste", "5511999999999", "ola", "req-1");

        assertEquals(WhatsAppSendStatus.SERVICE_UNAVAILABLE, result.getStatus());
        assertEquals(0, calls.get());
    }

    @Test
    void enviarTexto_ensureAvailableFalha_naoFazPost() {
        WhatsAppServiceWakeService wake = mock(WhatsAppServiceWakeService.class);
        doThrow(new WhatsAppServiceWakeService.WhatsAppAvailabilityException(
                WhatsAppServiceWakeService.WhatsAppAvailabilityReason.UNAVAILABLE, "down"))
                .when(wake).ensureAvailable();
        WhatsAppSessionReadyService sessionReady = mock(WhatsAppSessionReadyService.class);

        WhatsAppSendResult result = provider(wake, sessionReady)
                .enviarTexto("empresa-teste", "5511999999999", "ola", "req-1");

        assertEquals(WhatsAppSendStatus.SERVICE_UNAVAILABLE, result.getStatus());
        assertEquals(0, calls.get());
        verify(sessionReady, never()).ensureSessionConnected(anyString());
    }

    @Test
    void enviarTexto_ensureAvailableAuthError_mapeiaUnauthorized() {
        WhatsAppServiceWakeService wake = mock(WhatsAppServiceWakeService.class);
        doThrow(new WhatsAppServiceWakeService.WhatsAppAvailabilityException(
                WhatsAppServiceWakeService.WhatsAppAvailabilityReason.AUTH_ERROR, "auth"))
                .when(wake).ensureAvailable();
        WhatsAppSessionReadyService sessionReady = mock(WhatsAppSessionReadyService.class);

        WhatsAppSendResult result = provider(wake, sessionReady)
                .enviarTexto("empresa-teste", "5511999999999", "ola", "req-1");

        assertEquals(WhatsAppSendStatus.UNAUTHORIZED, result.getStatus());
        assertEquals(0, calls.get());
    }

    // ========== consultarStatusAguardandoConexao ==========

    @Test
    void consultarStatusAguardandoConexao_connectedComStatus_retornaExatoSemSegundoGet() {
        WhatsAppSessionStatus status = connectedStatus();
        WhatsAppSessionReadyService sessionReady = mock(WhatsAppSessionReadyService.class);
        when(sessionReady.ensureSessionReady("empresa-teste"))
                .thenReturn(new WhatsAppSessionReadyService.SessionReadyOutcome(
                        WhatsAppSessionReadyService.ReadyResult.CONNECTED, status));

        WhatsAppResult<WhatsAppSessionStatus> result = provider(mock(WhatsAppServiceWakeService.class), sessionReady)
                .consultarStatusAguardandoConexao("empresa-teste");

        assertTrue(result.isSuccess());
        assertSame(status, result.getData());
        // Nao deve fazer GET redundante: provider nao chama HttpServer
        assertEquals(0, calls.get());
        verify(sessionReady).ensureSessionReady("empresa-teste");
    }

    @Test
    void consultarStatusAguardandoConexao_loggedOut_mapeiaUnavailable() {
        WhatsAppSessionReadyService sessionReady = mock(WhatsAppSessionReadyService.class);
        when(sessionReady.ensureSessionReady(anyString()))
                .thenReturn(new WhatsAppSessionReadyService.SessionReadyOutcome(
                        WhatsAppSessionReadyService.ReadyResult.LOGGED_OUT, connectedStatus()));

        WhatsAppResult<WhatsAppSessionStatus> result = provider(mock(WhatsAppServiceWakeService.class), sessionReady)
                .consultarStatusAguardandoConexao("empresa-teste");

        assertEquals(WhatsAppOperationStatus.UNAVAILABLE, result.getStatus());
        assertEquals(0, calls.get());
    }

    @Test
    void consultarStatusAguardandoConexao_authError_mapeiaUnauthorized() {
        WhatsAppSessionReadyService sessionReady = mock(WhatsAppSessionReadyService.class);
        when(sessionReady.ensureSessionReady(anyString()))
                .thenReturn(new WhatsAppSessionReadyService.SessionReadyOutcome(
                        WhatsAppSessionReadyService.ReadyResult.AUTH_ERROR, null));

        WhatsAppResult<WhatsAppSessionStatus> result = provider(mock(WhatsAppServiceWakeService.class), sessionReady)
                .consultarStatusAguardandoConexao("empresa-teste");

        assertEquals(WhatsAppOperationStatus.UNAUTHORIZED, result.getStatus());
        assertEquals(0, calls.get());
    }

    @Test
    void consultarStatusAguardandoConexao_timeout_mapeiaConnectTimeout() {
        WhatsAppSessionReadyService sessionReady = mock(WhatsAppSessionReadyService.class);
        when(sessionReady.ensureSessionReady(anyString()))
                .thenReturn(new WhatsAppSessionReadyService.SessionReadyOutcome(
                        WhatsAppSessionReadyService.ReadyResult.TIMEOUT, null));

        WhatsAppResult<WhatsAppSessionStatus> result = provider(mock(WhatsAppServiceWakeService.class), sessionReady)
                .consultarStatusAguardandoConexao("empresa-teste");

        assertEquals(WhatsAppOperationStatus.CONNECT_TIMEOUT, result.getStatus());
        assertEquals(0, calls.get());
    }

    @Test
    void consultarStatusAguardandoConexao_unavailable_mapeiaUnavailable() {
        WhatsAppSessionReadyService sessionReady = mock(WhatsAppSessionReadyService.class);
        when(sessionReady.ensureSessionReady(anyString()))
                .thenReturn(new WhatsAppSessionReadyService.SessionReadyOutcome(
                        WhatsAppSessionReadyService.ReadyResult.UNAVAILABLE, null));

        WhatsAppResult<WhatsAppSessionStatus> result = provider(mock(WhatsAppServiceWakeService.class), sessionReady)
                .consultarStatusAguardandoConexao("empresa-teste");

        assertEquals(WhatsAppOperationStatus.UNAVAILABLE, result.getStatus());
        assertEquals(0, calls.get());
    }

    @Test
    void consultarStatusAguardandoConexao_notConfigured_mapeiaUnavailable() {
        WhatsAppSessionReadyService sessionReady = mock(WhatsAppSessionReadyService.class);
        when(sessionReady.ensureSessionReady(anyString()))
                .thenReturn(new WhatsAppSessionReadyService.SessionReadyOutcome(
                        WhatsAppSessionReadyService.ReadyResult.NOT_CONFIGURED, null));

        WhatsAppResult<WhatsAppSessionStatus> result = provider(mock(WhatsAppServiceWakeService.class), sessionReady)
                .consultarStatusAguardandoConexao("empresa-teste");

        assertEquals(WhatsAppOperationStatus.UNAVAILABLE, result.getStatus());
        assertEquals(0, calls.get());
    }

    @Test
    void consultarStatusAguardandoConexao_connectedSemStatus_fazFallbackParaGet() {
        WhatsAppSessionReadyService sessionReady = mock(WhatsAppSessionReadyService.class);
        when(sessionReady.ensureSessionReady(anyString()))
                .thenReturn(new WhatsAppSessionReadyService.SessionReadyOutcome(
                        WhatsAppSessionReadyService.ReadyResult.CONNECTED, null));
        stubStatus = 200;
        stubBody = """
                {"companyId":"empresa-teste","state":"CONNECTED","hasQr":false,
                 "qrUpdatedAt":null,"connectedAt":"2026-09-07T11:00:00Z",
                 "reconnectAttempts":0,"lastDisconnectCode":null}""";

        WhatsAppResult<WhatsAppSessionStatus> result = provider(mock(WhatsAppServiceWakeService.class), sessionReady)
                .consultarStatusAguardandoConexao("empresa-teste");

        assertTrue(result.isSuccess());
        assertEquals(1, calls.get());
        assertEquals("/internal/whatsapp/sessions/empresa-teste/status", lastPath);
    }

    // ========== QR / connect / logout / consultarStatus nao chamam ensureSessionConnected ==========

    @Test
    void operacoesSimples_naoChamamEnsureSessionConnected() {
        WhatsAppSessionReadyService sessionReady = mock(WhatsAppSessionReadyService.class);
        stubBody = """
                {"companyId":"empresa-teste","state":"CONNECTED","hasQr":false,
                 "qrUpdatedAt":null,"connectedAt":"2026-09-07T11:00:00Z",
                 "reconnectAttempts":0,"lastDisconnectCode":null}""";
        WhatsAppServiceProvider p = provider(mock(WhatsAppServiceWakeService.class), sessionReady);

        p.conectar("empresa-teste");
        p.consultarStatus("empresa-teste");
        p.logout("empresa-teste");

        stubBody = "{\"qr\":\"QR-FAKE\",\"updatedAt\":\"2026-09-07T10:00:00Z\"}";
        p.obterQr("empresa-teste");

        verify(sessionReady, never()).ensureSessionConnected(anyString());
        verify(sessionReady, never()).ensureSessionReady(anyString());
    }

    @Test
    void consultarStatusAguardandoConexao_ensureAvailableFalha_naoChamaSessionReady() {
        WhatsAppServiceWakeService wake = mock(WhatsAppServiceWakeService.class);
        doThrow(new WhatsAppServiceWakeService.WhatsAppAvailabilityException(
                WhatsAppServiceWakeService.WhatsAppAvailabilityReason.TIMEOUT, "timeout"))
                .when(wake).ensureAvailable();
        WhatsAppSessionReadyService sessionReady = mock(WhatsAppSessionReadyService.class);

        WhatsAppResult<WhatsAppSessionStatus> result = provider(wake, sessionReady)
                .consultarStatusAguardandoConexao("empresa-teste");

        assertEquals(WhatsAppOperationStatus.CONNECT_TIMEOUT, result.getStatus());
        assertEquals(0, calls.get());
        verify(sessionReady, never()).ensureSessionReady(anyString());
    }

    @Test
    void consultarStatus_chamaEnsureAvailableAntesDoHttp() {
        WhatsAppServiceWakeService wake = mock(WhatsAppServiceWakeService.class);
        stubBody = """
                {"companyId":"empresa-teste","state":"CONNECTING","hasQr":false,
                 "qrUpdatedAt":null,"connectedAt":null,
                 "reconnectAttempts":0,"lastDisconnectCode":null}""";
        WhatsAppResult<WhatsAppSessionStatus> result = provider(wake, null)
                .consultarStatus("empresa-teste");

        assertTrue(result.isSuccess());
        verify(wake).ensureAvailable();
        assertEquals(1, calls.get());
        assertEquals("/internal/whatsapp/sessions/empresa-teste/status", lastPath);
    }
}

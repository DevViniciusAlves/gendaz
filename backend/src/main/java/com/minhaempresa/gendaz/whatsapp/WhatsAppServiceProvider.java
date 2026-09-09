package com.minhaempresa.gendaz.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhaempresa.gendaz.shared.audit.OutboundTrafficAuditService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Implementacao HTTP do {@link WhatsAppProvider} sobre o whatsapp-service.
 *
 * <p>Padrao seguido das integracoes existentes (ex.: ResendEmailService):
 * {@code java.net.http.HttpClient}, auditoria via OutboundTrafficAuditService
 * e logs apenas operacionais. Token e QR nunca aparecem em logs, exceptions
 * ou respostas.
 */
@Service
@Slf4j
public class WhatsAppServiceProvider implements WhatsAppProvider {

    private static final Pattern COMPANY_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final ObjectMapper objectMapper;
    private final OutboundTrafficAuditService auditService;
    private final String serviceUrl;
    private final String internalToken;
    private final Duration requestTimeout;
    private final HttpClient httpClient;

    @Autowired
    public WhatsAppServiceProvider(
            ObjectMapper objectMapper,
            OutboundTrafficAuditService auditService,
            @Value("${whatsapp.service-url:${WHATSAPP_SERVICE_URL:}}") String serviceUrl,
            @Value("${whatsapp.internal-token:${WHATSAPP_INTERNAL_TOKEN:}}") String internalToken
    ) {
        this(objectMapper, auditService, serviceUrl, internalToken,
                Duration.ofSeconds(5), Duration.ofSeconds(10));
    }

    WhatsAppServiceProvider(
            ObjectMapper objectMapper,
            OutboundTrafficAuditService auditService,
            String serviceUrl,
            String internalToken,
            Duration connectTimeout,
            Duration requestTimeout
    ) {
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.serviceUrl = serviceUrl == null ? "" : serviceUrl.trim().replaceAll("/+$", "");
        this.internalToken = internalToken == null ? "" : internalToken.trim();
        this.requestTimeout = requestTimeout == null ? Duration.ofSeconds(10) : requestTimeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout)
                .build();
    }

    @Override
    public boolean disponivel() {
        return !serviceUrl.isBlank() && !internalToken.isBlank();
    }

    @Override
    public WhatsAppResult<WhatsAppSessionStatus> conectar(String companyId) {
        String valido = validarCompanyId(companyId);
        if (valido == null) {
            return WhatsAppResult.erro(WhatsAppOperationStatus.INVALID_COMPANY_ID);
        }
        if (!disponivel()) {
            return WhatsAppResult.erro(WhatsAppOperationStatus.NOT_CONFIGURED);
        }
        HttpCall call = post(valido, "connect", "conectar");
        return call.mapStatus(WhatsAppSessionStatus.class, false);
    }

    @Override
    public WhatsAppResult<WhatsAppSessionStatus> consultarStatus(String companyId) {
        String valido = validarCompanyId(companyId);
        if (valido == null) {
            return WhatsAppResult.erro(WhatsAppOperationStatus.INVALID_COMPANY_ID);
        }
        if (!disponivel()) {
            return WhatsAppResult.erro(WhatsAppOperationStatus.NOT_CONFIGURED);
        }
        HttpCall call = get(valido, "status", "consultarStatus");
        return call.mapStatus(WhatsAppSessionStatus.class, false);
    }

    @Override
    public WhatsAppResult<WhatsAppQr> obterQr(String companyId) {
        String valido = validarCompanyId(companyId);
        if (valido == null) {
            return WhatsAppResult.erro(WhatsAppOperationStatus.INVALID_COMPANY_ID);
        }
        if (!disponivel()) {
            return WhatsAppResult.erro(WhatsAppOperationStatus.NOT_CONFIGURED);
        }
        HttpCall call = get(valido, "qr", "obterQr");
        return call.mapStatus(WhatsAppQr.class, true);
    }

    @Override
    public WhatsAppResult<WhatsAppSessionStatus> logout(String companyId) {
        String valido = validarCompanyId(companyId);
        if (valido == null) {
            return WhatsAppResult.erro(WhatsAppOperationStatus.INVALID_COMPANY_ID);
        }
        if (!disponivel()) {
            return WhatsAppResult.erro(WhatsAppOperationStatus.NOT_CONFIGURED);
        }
        HttpCall call = post(valido, "logout", "logout");
        return call.mapStatus(WhatsAppSessionStatus.class, false);
    }

    @Override
    public WhatsAppSendResult enviarTexto(String companyId, String recipient, String text, String requestId) {
        String valido = validarCompanyId(companyId);
        if (valido == null) {
            return WhatsAppSendResult.erro(WhatsAppSendStatus.PROVIDER_ERROR);
        }
        if (!disponivel()) {
            return WhatsAppSendResult.erro(WhatsAppSendStatus.NOT_CONFIGURED);
        }
        String corpo;
        try {
            corpo = objectMapper.writeValueAsString(Map.of(
                    "recipient", recipient == null ? "" : recipient,
                    "text", text == null ? "" : text,
                    "requestId", requestId == null ? "" : requestId));
        } catch (Exception e) {
            return WhatsAppSendResult.erro(WhatsAppSendStatus.PROVIDER_ERROR);
        }
        return postTexto(valido, corpo, "enviarTexto");
    }

    private static String validarCompanyId(String companyId) {
        if (companyId == null) {
            return null;
        }
        String valor = companyId.trim();
        return COMPANY_ID_PATTERN.matcher(valor).matches() ? valor : null;
    }

    private HttpCall post(String companyId, String acao, String origem) {
        String url = serviceUrl + "/internal/whatsapp/sessions/" + companyId + "/" + acao;
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(requestTimeout)
                    .header("Authorization", "Bearer " + internalToken)
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("[whatsapp-provider] url invalida ao executar {}", origem);
            return new HttpCall(WhatsAppOperationStatus.UNAVAILABLE);
        }
        return executar(request, "POST", origem);
    }

    private HttpCall get(String companyId, String acao, String origem) {
        String url = serviceUrl + "/internal/whatsapp/sessions/" + companyId + "/" + acao;
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(requestTimeout)
                    .header("Authorization", "Bearer " + internalToken)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("[whatsapp-provider] url invalida ao executar {}", origem);
            return new HttpCall(WhatsAppOperationStatus.UNAVAILABLE);
        }
        return executar(request, "GET", origem);
    }

    /**
     * Envio de texto com classificacao retryable/terminal/ambiguo.
     *
     * <p>Falha comprovadamente anterior ao envio (conexao recusada, host
     * indisponivel, connect timeout, 409, 503) e retryable. Timeout da
     * requisicao, interrupcao ou qualquer excecao sem certeza do resultado e
     * {@code DELIVERY_UNKNOWN}: o Node pode ter enviado e a resposta se
     * perdido, entao nao ha retry automatico. Nunca loga destinatario,
     * texto ou token — apenas origem, status e tipo de erro.
     */
    private WhatsAppSendResult postTexto(String companyId, String corpoJson, String origem) {
        String url = serviceUrl + "/internal/whatsapp/sessions/" + companyId + "/messages/text";
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(requestTimeout)
                    .header("Authorization", "Bearer " + internalToken)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(corpoJson, StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("[whatsapp-provider] url invalida ao executar {}", origem);
            return WhatsAppSendResult.erro(WhatsAppSendStatus.PROVIDER_ERROR);
        }
        contarExecucao("WhatsAppServiceProvider#" + origem);
        long inicio = System.currentTimeMillis();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            registrarHttp("POST", origem, corpoJson, response.body(),
                    System.currentTimeMillis() - inicio, response.statusCode());
            return converterEnvio(response.statusCode(), response.body());
        } catch (java.net.http.HttpConnectTimeoutException e) {
            registrarHttp("POST", origem, corpoJson, "", System.currentTimeMillis() - inicio, -1);
            log.warn("[whatsapp-provider] conexao timeout ao executar {}", origem);
            return WhatsAppSendResult.erro(WhatsAppSendStatus.SERVICE_UNAVAILABLE);
        } catch (HttpTimeoutException e) {
            registrarHttp("POST", origem, corpoJson, "", System.currentTimeMillis() - inicio, -1);
            log.warn("[whatsapp-provider] timeout ambiguo ao executar {} (possivel envio sem resposta)", origem);
            return WhatsAppSendResult.erro(WhatsAppSendStatus.DELIVERY_UNKNOWN);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            registrarHttp("POST", origem, corpoJson, "", System.currentTimeMillis() - inicio, -1);
            log.warn("[whatsapp-provider] interrompido ao executar {} (resultado incerto)", origem);
            return WhatsAppSendResult.erro(WhatsAppSendStatus.DELIVERY_UNKNOWN);
        } catch (Exception e) {
            registrarHttp("POST", origem, corpoJson, "", System.currentTimeMillis() - inicio, -1);
            if (falhaAntesDoEnvio(e)) {
                log.warn("[whatsapp-provider] servico indisponivel ao executar {}. erroTipo={}", origem, e.getClass().getSimpleName());
                return WhatsAppSendResult.erro(WhatsAppSendStatus.SERVICE_UNAVAILABLE);
            }
            log.warn("[whatsapp-provider] falha ambigua ao executar {}. erroTipo={}", origem, e.getClass().getSimpleName());
            return WhatsAppSendResult.erro(WhatsAppSendStatus.DELIVERY_UNKNOWN);
        }
    }

    /** Conexao recusada, DNS/host indisponivel: a requisicao nunca chegou. */
    private static boolean falhaAntesDoEnvio(Throwable e) {
        for (Throwable atual = e; atual != null; atual = atual.getCause()) {
            if (atual instanceof java.net.ConnectException
                    || atual instanceof java.net.UnknownHostException
                    || atual instanceof java.net.NoRouteToHostException) {
                return true;
            }
        }
        return false;
    }

    private WhatsAppSendResult converterEnvio(int httpStatus, String body) {
        WhatsAppSendStatus mapeado = mapearEnvio(httpStatus, body);
        if (mapeado != WhatsAppSendStatus.SENT) {
            log.warn("[whatsapp-provider] envio nao-sucedido status={}", httpStatus);
            return WhatsAppSendResult.erro(mapeado);
        }
        try {
            JsonNode root = objectMapper.readTree(body == null ? "" : body);
            JsonNode messageId = root.path("messageId");
            return WhatsAppSendResult.sent(messageId.isTextual() ? messageId.asText() : null);
        } catch (Exception e) {
            // HTTP 200 com corpo ilegivel: pode ter enviado, mas sem
            // confirmacao parseavel. Conservador: ambiguo, sem retry.
            log.warn("[whatsapp-provider] resposta de envio invalida. erroTipo={}", e.getClass().getSimpleName());
            return WhatsAppSendResult.erro(WhatsAppSendStatus.DELIVERY_UNKNOWN);
        }
    }

    private WhatsAppSendStatus mapearEnvio(int httpStatus, String body) {
        if (httpStatus >= 200 && httpStatus < 300) {
            return WhatsAppSendStatus.SENT;
        }
        String erro = extrairCodigoErro(body);
        if (httpStatus == 400) {
            return switch (erro) {
                case "invalid_recipient" -> WhatsAppSendStatus.INVALID_RECIPIENT;
                case "invalid_message", "invalid_request_id" -> WhatsAppSendStatus.INVALID_MESSAGE;
                default -> WhatsAppSendStatus.PROVIDER_ERROR;
            };
        }
        if (httpStatus == 401) {
            return WhatsAppSendStatus.UNAUTHORIZED;
        }
        if (httpStatus == 409) {
            return WhatsAppSendStatus.SESSION_NOT_CONNECTED;
        }
        if (httpStatus == 503 || "service_unavailable".equals(erro)) {
            return WhatsAppSendStatus.SERVICE_UNAVAILABLE;
        }
        if (httpStatus == 500) {
            // provider_send_failed ocorre apos tentativa do sock.sendMessage:
            // ambiguo, sem retry automatico.
            return WhatsAppSendStatus.PROVIDER_ERROR;
        }
        return WhatsAppSendStatus.PROVIDER_ERROR;
    }

    private HttpCall executar(HttpRequest request, String metodo, String origem) {
        contarExecucao("WhatsAppServiceProvider#" + origem);
        long inicio = System.currentTimeMillis();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            registrarHttp(metodo, origem, "", response.body(), System.currentTimeMillis() - inicio, response.statusCode());
            return new HttpCall(response.statusCode(), response.body());
        } catch (HttpTimeoutException e) {
            registrarHttp(metodo, origem, "", "", System.currentTimeMillis() - inicio, -1);
            log.warn("[whatsapp-provider] timeout ao executar {}", origem);
            return new HttpCall(WhatsAppOperationStatus.UNAVAILABLE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[whatsapp-provider] interrompido ao executar {}", origem);
            return new HttpCall(WhatsAppOperationStatus.UNAVAILABLE);
        } catch (Exception e) {
            log.warn("[whatsapp-provider] servico indisponivel ao executar {}. erroTipo={}", origem, e.getClass().getSimpleName());
            return new HttpCall(WhatsAppOperationStatus.UNAVAILABLE);
        }
    }

    private <T> WhatsAppResult<T> converter(int httpStatus, String body, Class<T> tipo, boolean eQr) {
        WhatsAppOperationStatus mapeado = mapear(httpStatus, body, eQr);
        if (mapeado != WhatsAppOperationStatus.SUCCESS) {
            if (mapeado == WhatsAppOperationStatus.QR_UNAVAILABLE) {
                log.info("[whatsapp-provider] qr indisponivel no momento");
            } else {
                log.warn("[whatsapp-provider] resposta nao-sucedida status={}", httpStatus);
            }
            return WhatsAppResult.erro(mapeado);
        }
        try {
            T data = objectMapper.readValue(body, tipo);
            return WhatsAppResult.success(data);
        } catch (Exception e) {
            log.warn("[whatsapp-provider] resposta json invalida. erroTipo={}", e.getClass().getSimpleName());
            return WhatsAppResult.erro(WhatsAppOperationStatus.INTERNAL_ERROR);
        }
    }

    private WhatsAppOperationStatus mapear(int httpStatus, String body, boolean eQr) {
        if (httpStatus >= 200 && httpStatus < 300) {
            return WhatsAppOperationStatus.SUCCESS;
        }
        String erro = extrairCodigoErro(body);
        if (httpStatus == 400) {
            return "invalid_company_id".equals(erro)
                    ? WhatsAppOperationStatus.INVALID_COMPANY_ID
                    : WhatsAppOperationStatus.INTERNAL_ERROR;
        }
        if (httpStatus == 401) {
            return WhatsAppOperationStatus.UNAUTHORIZED;
        }
        if (httpStatus == 404) {
            return eQr && "qr_unavailable".equals(erro)
                    ? WhatsAppOperationStatus.QR_UNAVAILABLE
                    : WhatsAppOperationStatus.INTERNAL_ERROR;
        }
        if (httpStatus == 503 || "service_unavailable".equals(erro)) {
            return WhatsAppOperationStatus.UNAVAILABLE;
        }
        if (httpStatus == 500) {
            return WhatsAppOperationStatus.INTERNAL_ERROR;
        }
        return WhatsAppOperationStatus.INTERNAL_ERROR;
    }

    private String extrairCodigoErro(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode erro = root.path("error");
            return erro.isTextual() ? erro.asText() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private void contarExecucao(String chave) {
        if (auditService != null) {
            auditService.contarExecucao(chave);
        }
    }

    private void registrarHttp(
            String metodoHttp,
            String origem,
            String bodyEnviado,
            String bodyRecebido,
            long duracaoMs,
            int statusHttp
    ) {
        if (auditService == null) {
            return;
        }
        // Apenas contagem de bytes: token e QR nunca sao registrados como conteudo.
        auditService.registrarHttp(
                "WhatsApp",
                auditService.sanitizarBaseUrl(serviceUrl),
                metodoHttp,
                auditService.origem("WhatsAppServiceProvider", origem),
                auditService.bytesUtf8(bodyEnviado),
                auditService.headersBytes(Map.of("Accept", "application/json")),
                auditService.bytesUtf8(bodyRecebido),
                duracaoMs,
                statusHttp
        );
    }

    /** Chamada executada: resposta HTTP ou falha de infraestrutura. */
    private final class HttpCall {
        private final boolean executada;
        private final int httpStatus;
        private final String body;
        private final WhatsAppOperationStatus falha;

        private HttpCall(int httpStatus, String body) {
            this.executada = true;
            this.httpStatus = httpStatus;
            this.body = body;
            this.falha = null;
        }

        private HttpCall(WhatsAppOperationStatus falha) {
            this.executada = false;
            this.httpStatus = -1;
            this.body = "";
            this.falha = falha;
        }

        private <T> WhatsAppResult<T> mapStatus(Class<T> tipo, boolean eQr) {
            if (!executada) {
                return WhatsAppResult.erro(falha);
            }
            return converter(httpStatus, body, tipo, eQr);
        }
    }
}

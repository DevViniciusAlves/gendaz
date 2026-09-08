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

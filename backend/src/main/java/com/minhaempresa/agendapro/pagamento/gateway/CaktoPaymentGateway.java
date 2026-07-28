package com.minhaempresa.agendapro.pagamento.gateway;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhaempresa.agendapro.pagamento.entity.PagamentoPlanoEntity;
import com.minhaempresa.agendapro.pagamento.enums.MetodoPagamento;
import com.minhaempresa.agendapro.pagamento.enums.StatusPagamento;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.shared.audit.OutboundTrafficAuditService;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "CAKTO")
@RequiredArgsConstructor
@Slf4j
public class CaktoPaymentGateway implements PaymentGateway {
    private static final String CAKTO_API_BASE_URL = "https://api.cakto.com.br";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final PaymentGatewayProperties properties;
    private final ObjectMapper objectMapper;
    private final OutboundTrafficAuditService auditService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private String accessToken;
    private Instant accessTokenExpiresAt = Instant.EPOCH;

    @Override
    public PaymentGatewayResponse criarPagamentoPlano(PagamentoPlanoEntity pagamento) {
        String plano = pagamento.getPlano().getNome().toUpperCase(Locale.ROOT);
        try {
            if (!"PRO".equals(plano)) {
                return fallbackCheckout(pagamento, plano);
            }
            if (pagamento.getMetodoPagamento() == MetodoPagamento.CREDIT_CARD) {
                return fallbackCheckout(pagamento, plano);
            }
            if (!dadosPixAutoSuficientes(pagamento)) {
                log.warn("Cakto pix_auto sem dados suficientes para {}. Usando fallback.", pagamento.getPaymentReference());
                return fallbackCheckout(pagamento, plano);
            }
            validarCredenciaisApi();
            return criarPagamentoPixAuto(obterAccessToken(), pagamento);
        } catch (BusinessException ex) {
            log.warn("Cakto nao conseguiu criar pagamento automatico para {}: {}", pagamento.getPaymentReference(), ex.getMessage());
            return fallbackCheckout(pagamento, plano);
        } catch (RuntimeException ex) {
            log.warn("Falha inesperada na criacao automatica da Cakto para {}: {}", pagamento.getPaymentReference(), ex.getMessage());
            return fallbackCheckout(pagamento, plano);
        }
    }

    @Override
    public boolean validarWebhook(String assinatura, PaymentGatewayWebhook webhook) {
        String secret = properties.getCaktoWebhookSecret();
        if (secret == null || secret.isBlank() || assinatura == null || assinatura.isBlank()) {
            return false;
        }
        String recebido = normalizarAssinatura(assinatura);
        return MessageDigest.isEqual(recebido.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public PaymentGatewayWebhook consultarPagamentoWebhook(String providerPaymentId, String assinatura, String requestId) {
        throw new BusinessException("Consulta automatica da Cakto nao configurada. Aguarde o webhook ou tente novamente em instantes.");
    }

    @Override
    public Optional<PaymentGatewayWebhook> consultarPagamentoPlano(PagamentoPlanoEntity pagamento) {
        auditService.contarExecucao("CaktoPaymentGateway#consultarPagamentoPlano");
        validarCredenciaisApi();
        String token = obterAccessToken();

        Optional<Map<String, Object>> porReferencia = buscarPedidoAprovadoPorReferencia(token, pagamento);
        if (porReferencia.isPresent()) {
            return porReferencia.map(pedido -> toWebhook(pedido, pagamento));
        }

        return buscarPedidoAprovadoPorCliente(token, pagamento).map(pedido -> toWebhook(pedido, pagamento));
    }

    private PaymentGatewayResponse fallbackCheckout(PagamentoPlanoEntity pagamento, String plano) {
        String checkoutBase = checkoutUrl(plano);
        if (checkoutBase == null || checkoutBase.isBlank()) {
            throw new BusinessException("Checkout da Cakto nao configurado para o plano " + plano + ".");
        }

        String paymentReference = pagamento.getPaymentReference();
        String externalReference = pagamento.getExternalReference() == null || pagamento.getExternalReference().isBlank()
                ? paymentReference
                : pagamento.getExternalReference();
        String providerPaymentId = pagamento.getProviderPaymentId();
        if (providerPaymentId == null || providerPaymentId.isBlank() || providerPaymentId.startsWith("pending-")) {
            providerPaymentId = "cakto-" + paymentReference;
        }

        return new PaymentGatewayResponse(
                "CAKTO",
                providerPaymentId,
                externalReference,
                paymentReference,
                checkoutBase,
                null,
                null,
                LocalDateTime.now().plusHours(24)
        );
    }

    private String checkoutUrl(String plano) {
        return "PRO".equals(plano) ? properties.getCaktoCheckoutProUrl() : properties.getCaktoCheckoutBasicoUrl();
    }

    private String normalizarAssinatura(String assinatura) {
        String texto = assinatura.trim();
        if (texto.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return texto.substring(7).trim();
        }
        return texto;
    }

    private void validarCredenciaisApi() {
        if (!temCredenciaisApi()) {
            throw new BusinessException("Credenciais da Cakto nao configuradas para consulta automatica.");
        }
    }

    private boolean temCredenciaisApi() {
        return properties.getCaktoClientId() != null && !properties.getCaktoClientId().isBlank()
                && properties.getCaktoClientSecret() != null && !properties.getCaktoClientSecret().isBlank();
    }

    private synchronized String obterAccessToken() {
        auditService.contarExecucao("CaktoPaymentGateway#obterAccessToken");
        if (accessToken != null && Instant.now().isBefore(accessTokenExpiresAt.minusSeconds(30))) {
            return accessToken;
        }

        String body = "client_id=" + encode(properties.getCaktoClientId())
                + "&client_secret=" + encode(properties.getCaktoClientSecret());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CAKTO_API_BASE_URL + "/public_api/token/"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            Map<String, Object> json = executarJson(request, body, "CaktoPaymentGateway#obterAccessToken");
            String token = texto(json, "access_token", "accessToken", "token");
            if (token == null || token.isBlank()) {
                throw new BusinessException("Cakto nao retornou token para consulta automatica.");
            }
            accessToken = token;
            accessTokenExpiresAt = Instant.now().plusSeconds(numero(json, "expires_in", "expiresIn").orElse(300L));
            return accessToken;
        } catch (IOException ex) {
            throw new BusinessException("Nao foi possivel ler a resposta da Cakto.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Consulta automatica da Cakto foi interrompida.");
        }
    }

    private PaymentGatewayResponse criarPagamentoPixAuto(String token, PagamentoPlanoEntity pagamento) {
        auditService.contarExecucao("CaktoPaymentGateway#criarPagamentoPixAuto");
        String offerId = primeiroNaoVazio(pagamento.getCaktoOfferId(), properties.getCaktoOfferProId());
        if (offerId == null || offerId.isBlank()) {
            throw new BusinessException("Oferta da Cakto nao configurada para o plano Pro.");
        }

        String nome = primeiroNaoVazio(pagamento.getCustomerName(), pagamento.getEmpresa().getNomeFantasia());
        String email = primeiroNaoVazio(pagamento.getCustomerEmail(), pagamento.getEmpresa().getEmail());
        String telefone = telefoneE164(primeiroNaoVazio(pagamento.getCustomerPhone(), pagamento.getEmpresa().getTelefone()));
        String fingerprint = primeiroNaoVazio(pagamento.getAntifraudReference(), UUID.randomUUID().toString());
        String docType = primeiroNaoVazio(pagamento.getCustomerDocType());
        String docNumber = primeiroNaoVazio(pagamento.getCustomerDocNumber());

        if (nome == null || email == null || telefone == null || docType == null || docNumber == null) {
            throw new BusinessException("Dados do cliente insuficientes para gerar PIX automatico.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productId", properties.getCaktoProductProId());
        body.put("paymentMethod", "pix_auto");
        body.put("customer", Map.of(
                "name", nome,
                "email", email,
                "phone", telefone,
                "fingerprint", fingerprint,
                "docType", docType,
                "docNumber", docNumber
        ));
        body.put("items", List.of(Map.of("offerId", offerId)));
        String bodyJson = safeToJson(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CAKTO_API_BASE_URL + "/public_api/payments/"))
                .timeout(Duration.ofSeconds(25))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("X-Idempotency-Key", pagamento.getPaymentReference())
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .build();

        Map<String, Object> response;
        try {
            response = executarJson(request, bodyJson, "CaktoPaymentGateway#criarPagamentoPixAuto");
        } catch (IOException ex) {
            throw new BusinessException("Nao foi possivel ler a resposta da Cakto.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Consulta automatica da Cakto foi interrompida.");
        }
        String providerPaymentId = primeiroNaoVazio(
                texto(response, "id", "paymentId", "payment_id", "data.id", "data.paymentId"),
                "cakto-" + pagamento.getPaymentReference()
        );
        String reference = primeiroNaoVazio(
                texto(response, "reference", "refId", "referenceId", "paymentReference", "payment_reference", "data.reference", "data.refId"),
                pagamento.getPaymentReference()
        );
        String externalReference = primeiroNaoVazio(
                texto(response, "externalReference", "external_reference", "orderId", "order_id", "data.externalReference", "data.orderId"),
                pagamento.getExternalReference()
        );
        String checkoutUrl = primeiroNaoVazio(
                texto(response, "checkoutUrl", "checkout_url", "url", "paymentUrl", "payment_url", "data.checkoutUrl", "data.url"),
                checkoutUrl("PRO")
        );
        String pixCopiaECola = primeiroNaoVazio(
                texto(response, "pixCopiaECola", "pix_copia_e_cola", "qrCode", "qr_code", "copyPaste", "copy_paste", "data.pixCopiaECola", "data.qrCode", "data.qr_code"),
                null
        );
        String pixQrCodeBase64 = primeiroNaoVazio(
                texto(response, "pixQrCodeBase64", "pix_qr_code_base64", "qrCodeBase64", "qr_code_base64", "data.pixQrCodeBase64", "data.qr_code_base64"),
                null
        );
        LocalDateTime expiracao = parseData(primeiroNaoVazio(
                texto(response, "expiresAt", "expires_at", "expirationDate", "expiration_date", "data.expiresAt", "data.expiration_date"),
                null
        ));

        if ((pixCopiaECola == null || pixCopiaECola.isBlank()) && (pixQrCodeBase64 == null || pixQrCodeBase64.isBlank())) {
            log.warn("Cakto nao retornou PIX para pagamento {}. Usando checkout como fallback.", pagamento.getPaymentReference());
            return fallbackCheckout(pagamento, "PRO");
        }

        return new PaymentGatewayResponse(
                "CAKTO",
                providerPaymentId,
                externalReference,
                reference,
                checkoutUrl,
                pixCopiaECola,
                pixQrCodeBase64,
                expiracao
        );
    }

    private Map<String, Object> executarJson(HttpRequest request, String bodyEnviado, String origem) throws IOException, InterruptedException {
        long inicio = System.currentTimeMillis();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        auditService.registrarHttp(
                "Cakto",
                auditService.sanitizarBaseUrl(request.uri().toString()),
                request.method(),
                auditService.origem("CaktoPaymentGateway", origem.contains("#") ? origem.substring(origem.indexOf('#') + 1) : origem),
                auditService.bytesUtf8(bodyEnviado),
                auditService.headersBytes(request.headers().map().entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> String.join(";", entry.getValue())
                        ))),
                auditService.bytesUtf8(response.body()),
                System.currentTimeMillis() - inicio,
                response.statusCode()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException("Cakto recusou solicitacao.");
        }
        return objectMapper.readValue(response.body(), MAP_TYPE);
    }

    private Optional<Map<String, Object>> buscarPedidoAprovadoPorReferencia(String token, PagamentoPlanoEntity pagamento) {
        String referencia = primeiroNaoVazio(pagamento.getExternalReference(), pagamento.getPaymentReference());
        if (referencia == null || referencia.isBlank()) {
            return Optional.empty();
        }
        return consultarPedidos(token, Map.of("refId", referencia, "status", "paid")).stream()
                .filter(pedido -> pedidoCompativel(pedido, pagamento))
                .findFirst();
    }

    private Optional<Map<String, Object>> buscarPedidoAprovadoPorCliente(String token, PagamentoPlanoEntity pagamento) {
        String email = pagamento.getEmpresa().getEmail();
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        String produto = produtoOuOferta(pagamento);
        Map<String, String> filtros = (produto == null || produto.isBlank())
                ? Map.of("customer", email, "status", "paid")
                : Map.of("customer", email, "products", produto, "status", "paid");
        return consultarPedidos(token, filtros).stream()
                .filter(pedido -> pedidoCompativel(pedido, pagamento))
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> consultarPedidos(String token, Map<String, String> filtros) {
        auditService.contarExecucao("CaktoPaymentGateway#consultarPedidos");
        StringBuilder url = new StringBuilder(CAKTO_API_BASE_URL + "/public_api/orders/");
        if (!filtros.isEmpty()) {
            url.append("?");
            filtros.forEach((chave, valor) -> {
                if (valor != null && !valor.isBlank()) {
                    if (url.charAt(url.length() - 1) != '?') {
                        url.append("&");
                    }
                    url.append(encode(chave)).append("=").append(encode(valor));
                }
            });
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        try {
            long inicio = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            auditService.registrarHttp(
                    "Cakto",
                    auditService.sanitizarBaseUrl(request.uri().toString()),
                    "GET",
                    auditService.origem("CaktoPaymentGateway", "consultarPedidos"),
                    0L,
                    auditService.headersBytes(request.headers().map().entrySet().stream()
                            .collect(java.util.stream.Collectors.toMap(
                                    Map.Entry::getKey,
                                    entry -> String.join(";", entry.getValue())
                            ))),
                    auditService.bytesUtf8(response.body()),
                    System.currentTimeMillis() - inicio,
                    response.statusCode()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Consulta Cakto retornou status {} para filtros {}", response.statusCode(), filtros.keySet());
                return List.of();
            }
            Map<String, Object> json = objectMapper.readValue(response.body(), MAP_TYPE);
            Object data = primeiroNaoNulo(json, "data", "results", "items", "orders");
            if (data instanceof List<?> lista) {
                return lista.stream()
                        .filter(Map.class::isInstance)
                        .map(item -> (Map<String, Object>) item)
                        .toList();
            }
            if (json.containsKey("id") || json.containsKey("refId")) {
                return List.of(json);
            }
            return List.of();
        } catch (IOException ex) {
            log.warn("Nao foi possivel ler pedidos da Cakto: {}", ex.getMessage());
            return List.of();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    private boolean pedidoCompativel(Map<String, Object> pedido, PagamentoPlanoEntity pagamento) {
        return statusAprovado(pedido)
                && clienteCompativel(pedido, pagamento)
                && produtoCompativel(pedido, pagamento)
                && valorCompativel(pedido, pagamento)
                && dataCompativel(pedido, pagamento);
    }

    private boolean statusAprovado(Map<String, Object> pedido) {
        String status = texto(pedido, "status", "paymentStatus", "payment_status", "saleStatus", "sale_status");
        return status != null && switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "paid", "approved", "completed", "active", "aprovado", "pago", "purchase_approved" -> true;
            default -> false;
        };
    }

    private boolean clienteCompativel(Map<String, Object> pedido, PagamentoPlanoEntity pagamento) {
        String email = texto(pedido,
                "customer.email", "customerEmail", "customer_email", "buyer.email", "buyerEmail", "buyer_email", "email");
        return email == null || pagamento.getEmpresa().getEmail() == null || email.equalsIgnoreCase(pagamento.getEmpresa().getEmail());
    }

    private boolean produtoCompativel(Map<String, Object> pedido, PagamentoPlanoEntity pagamento) {
        String esperado = produtoOuOferta(pagamento);
        if (esperado == null || esperado.isBlank()) {
            return true;
        }
        String encontrado = texto(pedido,
                "product.id", "productId", "product_id", "offer.id", "offerId", "offer_id",
                "items.0.product.id", "items.0.productId", "items.0.product_id", "items.0.offer.id", "items.0.offerId");
        return encontrado == null || esperado.equals(encontrado);
    }

    private boolean valorCompativel(Map<String, Object> pedido, PagamentoPlanoEntity pagamento) {
        BigDecimal valor = decimal(pedido,
                "amount", "baseAmount", "base_amount", "total", "value", "price",
                "payment.amount", "payment.value", "data.amount");
        if (valor == null) {
            return true;
        }
        return pagamento.getValor().compareTo(valor) == 0 || pagamento.getValor().compareTo(valor.movePointLeft(2)) == 0;
    }

    private boolean dataCompativel(Map<String, Object> pedido, PagamentoPlanoEntity pagamento) {
        Instant pedidoCriado = instant(pedido, "createdAt", "created_at", "paidAt", "paid_at", "updatedAt", "updated_at").orElse(null);
        if (pedidoCriado == null || pagamento.getDataCriacao() == null) {
            return true;
        }
        return pedidoCriado.isAfter(pagamento.getDataCriacao().minusDays(1).atZone(java.time.ZoneId.systemDefault()).toInstant());
    }

    private PaymentGatewayWebhook toWebhook(Map<String, Object> pedido, PagamentoPlanoEntity pagamento) {
        String providerPaymentId = primeiroNaoVazio(texto(pedido, "id", "orderId", "order_id", "transactionId", "transaction_id"), pagamento.getProviderPaymentId());
        BigDecimal valor = decimal(pedido, "amount", "baseAmount", "base_amount", "total", "value", "price");
        return new PaymentGatewayWebhook(
                primeiroNaoVazio(texto(pedido, "eventId", "event_id"), providerPaymentId),
                providerPaymentId,
                primeiroNaoVazio(texto(pedido, "refId", "ref_id", "externalReference", "external_reference"), pagamento.getExternalReference()),
                primeiroNaoVazio(texto(pedido, "paymentReference", "payment_reference"), pagamento.getPaymentReference()),
                StatusPagamento.PAYMENT_APPROVED,
                valor == null ? pagamento.getValor() : valor
        );
    }

    private String produtoOuOferta(PagamentoPlanoEntity pagamento) {
        String pro = properties.getCaktoProductProId();
        String offer = properties.getCaktoOfferProId();
        if (pro != null && !pro.isBlank()) {
            return pro;
        }
        return offer;
    }

    private String primeiroNaoVazio(String... valores) {
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                return valor.trim();
            }
        }
        return null;
    }

    private String planoPorProduto(String productId) {
        if (productId == null || productId.isBlank()) return null;
        if (productId.equals(properties.getCaktoProductBasicoId())) return "BASICO";
        if (productId.equals(properties.getCaktoProductProId())) return "PRO";
        if (productId.equals(properties.getCaktoOfferProId())) return "PRO";
        String texto = productId.toUpperCase(Locale.ROOT);
        if (texto.contains("BASICO")) return "BASICO";
        if (texto.contains("PRO")) return "PRO";
        return null;
    }

    @SuppressWarnings("unchecked")
    private String texto(Map<String, Object> payload, String... chaves) {
        if (payload == null) return null;
        for (String chave : chaves) {
            if (chave.contains(".")) {
                String[] partes = chave.split("\\.");
                Object atual = payload;
                for (String parte : partes) {
                    if (atual instanceof Map<?, ?> map) {
                        atual = ((Map<String, Object>) map).get(parte);
                        continue;
                    }
                    if (atual instanceof List<?> lista && parte.matches("\\d+")) {
                        int index = Integer.parseInt(parte);
                        atual = index < lista.size() ? lista.get(index) : null;
                        continue;
                    }
                    atual = null;
                    break;
                }
                if (atual != null) return String.valueOf(atual);
                continue;
            }
            Object valor = payload.get(chave);
            if (valor != null) return String.valueOf(valor);
        }
        for (String nested : List.of("data", "customer", "payment", "transaction", "sale", "product", "offer", "metadata", "point_of_interaction")) {
            Object valor = payload.get(nested);
            if (valor instanceof Map<?, ?> map) {
                String encontrado = texto((Map<String, Object>) map, chaves);
                if (encontrado != null) return encontrado;
            }
        }
        return null;
    }

    private Object primeiroNaoNulo(Map<String, Object> json, String... chaves) {
        for (String chave : chaves) {
            Object valor = json.get(chave);
            if (valor != null) return valor;
        }
        return null;
    }

    private Optional<Long> numero(Map<String, Object> json, String... chaves) {
        String texto = texto(json, chaves);
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(texto));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private BigDecimal decimal(Map<String, Object> payload, String... chaves) {
        String valor = texto(payload, chaves);
        if (valor == null || valor.isBlank()) return null;
        try {
            return new BigDecimal(valor.replace(",", "."));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Optional<Instant> instant(Map<String, Object> payload, String... chaves) {
        String valor = texto(payload, chaves);
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(valor).toInstant());
        } catch (RuntimeException ex) {
            try {
                return Optional.of(Instant.parse(valor));
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        }
    }

    private String telefoneE164(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            return null;
        }
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.startsWith("55")) {
            return "+" + digitos;
        }
        return "+55" + digitos;
    }

    private String safeToJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (IOException ex) {
            throw new BusinessException("Nao foi possivel montar a cobranca da Cakto.");
        }
    }

    private LocalDateTime parseData(String valor) {
        if (valor == null || valor.isBlank()) {
            return LocalDateTime.now().plusHours(24);
        }
        try {
            return OffsetDateTime.parse(valor).toLocalDateTime();
        } catch (RuntimeException ex) {
            try {
                return LocalDateTime.parse(valor);
            } catch (RuntimeException ignored) {
                return LocalDateTime.now().plusHours(24);
            }
        }
    }

    private String encode(String valor) {
        return URLEncoder.encode(Objects.toString(valor, ""), StandardCharsets.UTF_8);
    }

    private StatusPagamento normalizarStatusCakto(String status) {
        return switch (status == null ? "" : status.trim().toLowerCase(Locale.ROOT)) {
            case "approved", "paid", "completed", "active", "aprovado", "pago", "purchase_approved" -> StatusPagamento.PAYMENT_APPROVED;
            case "rejected", "refused", "declined", "recusado" -> StatusPagamento.PAYMENT_REJECTED;
            case "cancelled", "canceled", "cancelado" -> StatusPagamento.PAYMENT_CANCELED;
            case "expired", "expirado" -> StatusPagamento.PAYMENT_EXPIRED;
            default -> StatusPagamento.PAYMENT_PENDING;
        };
    }

    private boolean dadosPixAutoSuficientes(PagamentoPlanoEntity pagamento) {
        return primeiroNaoVazio(pagamento.getCustomerName(), pagamento.getEmpresa().getNomeFantasia()) != null
                && primeiroNaoVazio(pagamento.getCustomerEmail(), pagamento.getEmpresa().getEmail()) != null
                && telefoneE164(primeiroNaoVazio(pagamento.getCustomerPhone(), pagamento.getEmpresa().getTelefone())) != null
                && primeiroNaoVazio(pagamento.getCustomerDocType()) != null
                && primeiroNaoVazio(pagamento.getCustomerDocNumber()) != null
                && primeiroNaoVazio(pagamento.getCaktoOfferId(), properties.getCaktoOfferProId()) != null;
    }
}

package com.minhaempresa.agendapro.pagamento.gateway;

import com.minhaempresa.agendapro.pagamento.entity.PagamentoPlanoEntity;
import com.minhaempresa.agendapro.pagamento.enums.MetodoPagamento;
import com.minhaempresa.agendapro.pagamento.enums.StatusPagamento;
import com.minhaempresa.agendapro.shared.BusinessException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "MERCADO_PAGO")
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoPaymentGateway implements PaymentGateway {
    private static final String BASE_URL = "https://api.mercadopago.com";

    private final PaymentGatewayProperties properties;
    private final RestClient.Builder restClientBuilder;

    @Override
    public PaymentGatewayResponse criarPagamentoPlano(PagamentoPlanoEntity pagamento) {
        validarCredenciais();
        if (pagamento.getMetodoPagamento() == MetodoPagamento.PIX
                || pagamento.getMetodoPagamento() == MetodoPagamento.PIX_AUTO) {
            return criarPagamentoPix(pagamento);
        }
        if (pagamento.getMetodoPagamento() == MetodoPagamento.CREDIT_CARD) {
            return criarPreferenciaCartao(pagamento);
        }
        throw new BusinessException("Metodo de pagamento nao suportado pelo Mercado Pago.");
    }

    @Override
    public boolean validarWebhook(String assinatura, PaymentGatewayWebhook webhook) {
        return webhook != null && validarAssinatura(assinatura, null, webhook.providerPaymentId());
    }

    @Override
    public PaymentGatewayWebhook consultarPagamentoWebhook(String providerPaymentId, String assinatura, String requestId) {
        if (!validarAssinatura(assinatura, requestId, providerPaymentId)) {
            throw new BusinessException("Webhook de pagamento invalido.");
        }
        Map<String, Object> response = executarMercadoPago(() -> client().get()
                .uri("/v1/payments/{id}", providerPaymentId)
                .retrieve()
                .body(Map.class));

        String id = texto(response.get("id"));
        String externalReference = texto(response.get("external_reference"));
        StatusPagamento status = normalizarStatus(texto(response.get("status")));
        BigDecimal valor = numero(response.get("transaction_amount"));
        return new PaymentGatewayWebhook(id, id, externalReference, externalReference, status, valor);
    }

    private PaymentGatewayResponse criarPagamentoPix(PagamentoPlanoEntity pagamento) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transaction_amount", pagamento.getValor());
        body.put("description", "Plano Pro AgendEasy");
        body.put("payment_method_id", "pix");
        body.put("external_reference", pagamento.getExternalReference());
        body.put("payer", Map.of(
                "email", pagamento.getEmpresa().getEmail(),
                "first_name", pagamento.getEmpresa().getNomeFantasia()
        ));

        Map<String, Object> response = executarMercadoPago(() -> client().post()
                .uri("/v1/payments")
                .header("X-Idempotency-Key", pagamento.getExternalReference())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class));

        Map<String, Object> pointOfInteraction = mapa(response.get("point_of_interaction"));
        Map<String, Object> transactionData = mapa(pointOfInteraction.get("transaction_data"));
        String pixCopiaECola = texto(transactionData.get("qr_code"));
        String pixQrCodeBase64 = texto(transactionData.get("qr_code_base64"));
        if (pixCopiaECola == null || pixCopiaECola.isBlank() || pixQrCodeBase64 == null || pixQrCodeBase64.isBlank()) {
            log.warn("Mercado Pago nao retornou dados PIX para pagamento {}", pagamento.getExternalReference());
            throw new BusinessException("Mercado Pago nao retornou QR Code PIX. Tente novamente em alguns instantes.");
        }
        return new PaymentGatewayResponse(
                "MERCADO_PAGO",
                texto(response.get("id")),
                pagamento.getExternalReference(),
                pagamento.getPaymentReference(),
                null,
                pixCopiaECola,
                pixQrCodeBase64,
                parseData(texto(transactionData.get("expiration_date")))
        );
    }

    private PaymentGatewayResponse criarPreferenciaCartao(PagamentoPlanoEntity pagamento) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("external_reference", pagamento.getExternalReference());
        body.put("items", java.util.List.of(Map.of(
                "id", "plano-pro",
                "title", "Plano Pro AgendEasy",
                "description", "Assinatura do Plano Pro",
                "quantity", 1,
                "currency_id", "BRL",
                "unit_price", pagamento.getValor()
        )));
        body.put("payer", Map.of("email", pagamento.getEmpresa().getEmail()));
        body.put("back_urls", Map.of(
                "success", properties.getSuccessUrl(),
                "failure", properties.getCancelUrl(),
                "pending", properties.getSuccessUrl()
        ));
        body.put("auto_return", "approved");

        Map<String, Object> response = executarMercadoPago(() -> client().post()
                .uri("/checkout/preferences")
                .header("X-Idempotency-Key", pagamento.getExternalReference())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class));

        String checkoutUrl = texto(response.get("init_point"));
        if (checkoutUrl == null || checkoutUrl.isBlank()) {
            throw new BusinessException("Mercado Pago nao retornou link de checkout. Tente novamente em alguns instantes.");
        }
        return new PaymentGatewayResponse(
                "MERCADO_PAGO",
                texto(response.get("id")),
                pagamento.getExternalReference(),
                pagamento.getPaymentReference(),
                checkoutUrl,
                null,
                null,
                LocalDateTime.now().plusHours(24)
        );
    }

    private boolean validarAssinatura(String xSignature, String xRequestId, String dataId) {
        if (properties.getWebhookSecret() == null || properties.getWebhookSecret().isBlank()) return false;
        if (xSignature == null || xSignature.isBlank()) return false;

        Map<String, String> partes = extrairAssinatura(xSignature);
        String ts = partes.get("ts");
        String v1 = partes.get("v1");
        if (v1 == null || v1.isBlank()) return false;

        StringBuilder manifest = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) manifest.append("id:").append(dataId).append(";");
        if (xRequestId != null && !xRequestId.isBlank()) manifest.append("request-id:").append(xRequestId).append(";");
        if (ts != null && !ts.isBlank()) manifest.append("ts:").append(ts).append(";");

        String esperado = hmacSha256(properties.getWebhookSecret(), manifest.toString());
        return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8), v1.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, String> extrairAssinatura(String assinatura) {
        Map<String, String> partes = new LinkedHashMap<>();
        for (String parte : assinatura.split(",")) {
            String[] chaveValor = parte.split("=", 2);
            if (chaveValor.length == 2) partes.put(chaveValor[0].trim(), chaveValor[1].trim());
        }
        return partes;
    }

    private String hmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new BusinessException("Nao foi possivel validar assinatura do Mercado Pago.");
        }
    }

    private RestClient client() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(8));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        return restClientBuilder.baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getMercadoPagoAccessToken())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private void validarCredenciais() {
        if (properties.getMercadoPagoAccessToken() == null || properties.getMercadoPagoAccessToken().isBlank()) {
            throw new BusinessException("Credencial do Mercado Pago nao configurada.");
        }
    }

    private Map<String, Object> executarMercadoPago(MercadoPagoCall call) {
        try {
            Map<String, Object> response = call.execute();
            if (response == null) {
                throw new BusinessException("Mercado Pago nao respondeu a solicitacao.");
            }
            return response;
        } catch (RestClientResponseException ex) {
            String detalhe = limparMensagemMercadoPago(ex.getResponseBodyAsString());
            log.warn("Mercado Pago recusou solicitacao: status={}, detalhe={}", ex.getStatusCode().value(), detalhe);
            throw new BusinessException("Mercado Pago recusou o pagamento: " + detalhe);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Falha ao comunicar com Mercado Pago: {}", ex.getMessage());
            throw new BusinessException("Nao foi possivel comunicar com o Mercado Pago. Tente novamente em alguns instantes.");
        }
    }

    private String limparMensagemMercadoPago(String body) {
        if (body == null || body.isBlank()) return "verifique as credenciais e os dados do pagamento.";
        String texto = body.replaceAll("\\s+", " ").trim();
        return texto.length() > 220 ? texto.substring(0, 220) + "..." : texto;
    }

    private StatusPagamento normalizarStatus(String status) {
        return switch (status == null ? "" : status.toLowerCase()) {
            case "approved" -> StatusPagamento.PAYMENT_APPROVED;
            case "rejected" -> StatusPagamento.PAYMENT_REJECTED;
            case "cancelled", "canceled" -> StatusPagamento.PAYMENT_CANCELED;
            case "expired" -> StatusPagamento.PAYMENT_EXPIRED;
            default -> StatusPagamento.PAYMENT_PENDING;
        };
    }

    private LocalDateTime parseData(String valor) {
        if (valor == null || valor.isBlank()) return LocalDateTime.now().plusMinutes(30);
        try {
            return OffsetDateTime.parse(valor, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
        } catch (Exception ignored) {
            return LocalDateTime.now().plusMinutes(30);
        }
    }

    private BigDecimal numero(Object valor) {
        if (valor == null) return BigDecimal.ZERO;
        return new BigDecimal(String.valueOf(valor));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapa(Object valor) {
        return valor instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String texto(Object valor) {
        return valor == null ? null : String.valueOf(valor);
    }

    @FunctionalInterface
    private interface MercadoPagoCall {
        Map<String, Object> execute();
    }
}

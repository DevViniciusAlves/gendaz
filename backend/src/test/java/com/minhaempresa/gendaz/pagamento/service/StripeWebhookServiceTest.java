package com.minhaempresa.gendaz.pagamento.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.gateway.StripeProperties;
import com.minhaempresa.gendaz.pagamento.repository.StripeWebhookEventRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

    private static final String WEBHOOK_SECRET = "whsec_teste_auditoria_0123456789";

    @Mock
    private PagamentoService pagamentoService;

    @Mock
    private StripeWebhookEventRepository webhookEventRepository;

    private StripeWebhookService stripeWebhookService;

    @BeforeEach
    void setup() {
        StripeProperties properties = new StripeProperties();
        properties.setWebhookSecret(WEBHOOK_SECRET);
        stripeWebhookService = new StripeWebhookService(properties, pagamentoService, webhookEventRepository);
    }

    private String checkoutCompletedPayload() {
        return """
                {
                  "id": "evt_checkout_completed_1",
                  "object": "event",
                  "api_version": "2026-07-29.dahlia",
                  "type": "checkout.session.completed",
                  "created": 1700000000,
                  "livemode": false,
                  "pending_webhooks": 1,
                  "request": null,
                  "data": {
                    "object": {
                      "id": "cs_test_abc",
                      "object": "checkout.session",
                      "status": "complete",
                      "payment_status": "paid",
                      "mode": "subscription",
                      "subscription": "sub_abc",
                      "customer": "cus_abc",
                      "metadata": {
                        "empresaId": "1",
                        "pagamentoPlanoId": "42",
                        "paymentReference": "AGE-PRO-ABC",
                        "externalReference": "AGE-PRO-ABC",
                        "plano": "PRO"
                      }
                    }
                  }
                }
                """;
    }

    private String assinaturaValida(String payload, long timestamp) throws Exception {
        String signedPayload = timestamp + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        Key chave = new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(chave);
        byte[] digest = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return "t=" + timestamp + ",v1=" + hex;
    }

    @Test
    void webhookComAssinaturaValidaEProcessadoComSucesso() throws Exception {
        String payload = checkoutCompletedPayload();
        long timestamp = System.currentTimeMillis() / 1000L;
        String sig = assinaturaValida(payload, timestamp);

        when(webhookEventRepository.reservarEvento(
                "evt_checkout_completed_1",
                "checkout.session.completed",
                "cs_test_abc",
                "checkout.session.completed:cs_test_abc"
        )).thenReturn(1);

        assertDoesNotThrow(() -> stripeWebhookService.processar(payload, sig));
        verify(pagamentoService).registrarCheckoutStripeConcluido(
                eq("cs_test_abc"),
                eq("sub_abc"),
                eq("cus_abc"),
                eq(42L),
                eq("AGE-PRO-ABC"));
    }

    @Test
    void webhookComAssinaturaAusenteERejeitado() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> stripeWebhookService.processar(checkoutCompletedPayload(), null));
        assertEquals("Assinatura Stripe ausente.", ex.getMessage());
        verify(pagamentoService, org.mockito.Mockito.never()).registrarCheckoutStripeConcluido(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void webhookComAssinaturaInvalidaERejeitado() throws Exception {
        String payload = checkoutCompletedPayload();
        long timestamp = System.currentTimeMillis() / 1000L;
        // Assinatura valida para OUTRO payload: verifica que a validacao de
        // assinatura (Stripe-Signature + raw body + secret) rejeita chaves/AIVs nao confiaveis.
        String sigDeOutroPayload = assinaturaValida("{\"object\":\"event\",\"type\":\"outro\"}", timestamp);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stripeWebhookService.processar(payload, sigDeOutroPayload));
        assertEquals("Assinatura Stripe invalida.", ex.getMessage());
        verify(pagamentoService, org.mockito.Mockito.never()).registrarCheckoutStripeConcluido(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void eventoDuplicadoDeCheckoutCompletedENaoProcessadoDeNovo() throws Exception {
        String payload = checkoutCompletedPayload();
        long timestamp = System.currentTimeMillis() / 1000L;
        String sig = assinaturaValida(payload, timestamp);

        when(webhookEventRepository.reservarEvento(
                "evt_checkout_completed_1",
                "checkout.session.completed",
                "cs_test_abc",
                "checkout.session.completed:cs_test_abc"
        )).thenReturn(0);

        stripeWebhookService.processar(payload, sig);
        verify(pagamentoService, org.mockito.Mockito.never()).registrarCheckoutStripeConcluido(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void invoiceForaDeOrdemComSubscriptionAindaNaoVinculadaFalhaESeraRetentadoPelaStripe() throws Exception {
        String payload = """
                {
                  "id": "evt_invoice_1",
                  "object": "event",
                  "api_version": "2026-07-29.dahlia",
                  "type": "invoice.payment_succeeded",
                  "created": 1700000000,
                  "livemode": false,
                  "pending_webhooks": 1,
                  "request": null,
                  "data": {
                    "object": {
                      "id": "in_123",
                      "object": "invoice",
                      "subscription": "sub_x"
                    }
                  }
                }
                """;
        long timestamp = System.currentTimeMillis() / 1000L;
        String sig = assinaturaValida(payload, timestamp);

        when(webhookEventRepository.reservarEvento(
                "evt_invoice_1",
                "invoice.payment_succeeded",
                "in_123",
                "invoice.payment_succeeded:in_123"
        )).thenReturn(1);
        doThrow(new ResourceNotFoundException("Pagamento do plano não encontrado para subscriptionId: sub_x"))
                .when(pagamentoService).processarInvoiceStripe(
                        eq("in_123"), any(), eq(StatusPagamento.PAYMENT_APPROVED));

        // Comportamento atual: o evento propaga o erro (HTTP 500) e a Stripe re-entrega depois.
        assertThrows(ResourceNotFoundException.class, () -> stripeWebhookService.processar(payload, sig));
    }

    @Test
    void assinaturaInvalidaNaoReservaEvento() throws Exception {
        String payload = checkoutCompletedPayload();
        long timestamp = System.currentTimeMillis() / 1000L;
        String assinaturaDeOutroPayload = assinaturaValida("{}", timestamp);

        assertThrows(BusinessException.class, () -> stripeWebhookService.processar(payload, assinaturaDeOutroPayload));

        verify(webhookEventRepository, never()).reservarEvento(any(), any(), any(), any());
    }

    @Test
    void eventosDistintosDoMesmoObjetoLogicoSaoDeduplicadosPelaChaveDeNegocio() throws Exception {
        String primeiroPayload = checkoutCompletedPayload();
        String segundoPayload = primeiroPayload.replace("evt_checkout_completed_1", "evt_checkout_completed_2");
        long timestamp = System.currentTimeMillis() / 1000L;

        when(webhookEventRepository.reservarEvento(
                "evt_checkout_completed_1",
                "checkout.session.completed",
                "cs_test_abc",
                "checkout.session.completed:cs_test_abc"
        )).thenReturn(1);
        when(webhookEventRepository.reservarEvento(
                "evt_checkout_completed_2",
                "checkout.session.completed",
                "cs_test_abc",
                "checkout.session.completed:cs_test_abc"
        )).thenReturn(0);

        stripeWebhookService.processar(primeiroPayload, assinaturaValida(primeiroPayload, timestamp));
        stripeWebhookService.processar(segundoPayload, assinaturaValida(segundoPayload, timestamp));

        verify(pagamentoService).registrarCheckoutStripeConcluido(
                eq("cs_test_abc"), eq("sub_abc"), eq("cus_abc"), eq(42L), eq("AGE-PRO-ABC"));
    }

    @Test
    void subscriptionUpdatedNaoUsaChaveDeNegocioPermanente() throws Exception {
        String payload = """
                {
                  "id": "evt_subscription_updated_1",
                  "object": "event",
                  "api_version": "2026-07-29.dahlia",
                  "type": "customer.subscription.updated",
                  "created": 1700000000,
                  "livemode": false,
                  "pending_webhooks": 1,
                  "request": null,
                  "data": {
                    "object": {
                      "id": "sub_abc",
                      "object": "subscription",
                      "status": "active"
                    }
                  }
                }
                """;
        long timestamp = System.currentTimeMillis() / 1000L;

        when(webhookEventRepository.reservarEvento(
                "evt_subscription_updated_1",
                "customer.subscription.updated",
                "sub_abc",
                null
        )).thenReturn(1);

        stripeWebhookService.processar(payload, assinaturaValida(payload, timestamp));

        verify(webhookEventRepository).reservarEvento(
                "evt_subscription_updated_1",
                "customer.subscription.updated",
                "sub_abc",
                null
        );
        verify(pagamentoService, never()).aplicarStatusPorSubscriptionStripe(any(), any());
    }
}

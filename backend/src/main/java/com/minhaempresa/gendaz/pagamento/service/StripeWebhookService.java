package com.minhaempresa.gendaz.pagamento.service;

import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.gateway.StripeProperties;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookService {
    private final StripeProperties stripeProperties;
    private final PagamentoService pagamentoService;

    @Transactional
    public void processar(String payload, String sigHeader) {
        Event event = construirEvento(payload, sigHeader);
        switch (event.getType()) {
            case "checkout.session.completed" -> processarCheckoutSessionCompleted(event);
            case "invoice.payment_succeeded" -> processarInvoice(event, StatusPagamento.PAYMENT_APPROVED);
            case "invoice.payment_failed" -> processarInvoice(event, StatusPagamento.PAYMENT_REJECTED);
            case "customer.subscription.deleted" -> processarSubscription(event, StatusPagamento.PAYMENT_CANCELED);
            case "customer.subscription.updated" -> processarSubscriptionUpdated(event);
            default -> log.info("Webhook Stripe ignorado: type={}", event.getType());
        }
    }

    private Event construirEvento(String payload, String sigHeader) {
        if (sigHeader == null || sigHeader.isBlank()) {
            throw new BusinessException("Assinatura Stripe ausente.");
        }
        if (stripeProperties.getWebhookSecret() == null || stripeProperties.getWebhookSecret().isBlank()) {
            throw new BusinessException("STRIPE_WEBHOOK_SECRET nao configurado.");
        }
        try {
            return Webhook.constructEvent(payload, sigHeader, stripeProperties.getWebhookSecret());
        } catch (SignatureVerificationException ex) {
            throw new BusinessException("Assinatura Stripe invalida.");
        }
    }

    private void processarCheckoutSessionCompleted(Event event) {
        Session session = desserializar(event, Session.class);
        
        // Idempotência
        if (pagamentoService.eventoJaProcessado(event.getId())) {
             log.info("Evento checkout.session.completed já processado: {}", event.getId());
             return;
        }

        Map<String, String> metadata = session.getMetadata();
        Long pagamentoPlanoId = parseLong(metadata == null ? null : metadata.get("pagamentoPlanoId"));
        String paymentReference = metadata == null ? null : metadata.get("paymentReference");
        
        String status = session.getStatus();
        String paymentStatus = session.getPaymentStatus();
        
        log.info("Processando checkout.session.completed: id={}, status={}, paymentStatus={}, pagamentoPlanoId={}", 
                 session.getId(), status, paymentStatus, pagamentoPlanoId);

        if ("complete".equalsIgnoreCase(status) && "paid".equalsIgnoreCase(paymentStatus)) {
            pagamentoService.registrarCheckoutStripeConcluido(
                    session.getId(),
                    session.getSubscription(),
                    session.getCustomer(),
                    pagamentoPlanoId,
                    paymentReference
            );
        } else {
            log.warn("Checkout Session completada mas nao paga ou incompleta: status={}, paymentStatus={}", status, paymentStatus);
        }
    }

    private void processarInvoice(Event event, StatusPagamento status) {
        Invoice invoice = desserializar(event, Invoice.class);
        String eventId = event.getId();
        String invoiceId = invoice.getId();
        String subscriptionId = (String) new com.google.gson.Gson().fromJson(invoice.toJson(), java.util.Map.class).get("subscription");
        
        // Idempotência: verificar se o evento já foi processado
        if (pagamentoService.eventoJaProcessado(eventId)) {
            log.info("Evento Stripe já processado: eventId={}, type={}", eventId, event.getType());
            return;
        }
        
        // Processar invoice e registrar evento
        pagamentoService.processarInvoiceStripe(eventId, invoiceId, subscriptionId, status);
    }

    private void processarSubscription(Event event, StatusPagamento status) {
        Subscription subscription = desserializar(event, Subscription.class);
        pagamentoService.aplicarStatusPorSubscriptionStripe(subscription.getId(), status);
    }

    private void processarSubscriptionUpdated(Event event) {
        Subscription subscription = desserializar(event, Subscription.class);
        if ("canceled".equalsIgnoreCase(subscription.getStatus()) || "unpaid".equalsIgnoreCase(subscription.getStatus())) {
            pagamentoService.aplicarStatusPorSubscriptionStripe(subscription.getId(), StatusPagamento.PAYMENT_CANCELED);
        }
    }

    private <T> T desserializar(Event event, Class<T> tipo) {
        return event.getDataObjectDeserializer()
                .getObject()
                .filter(tipo::isInstance)
                .map(tipo::cast)
                .orElseThrow(() -> new BusinessException("Evento Stripe invalido: " + event.getType()));
    }

    private Long parseLong(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return Long.valueOf(valor);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

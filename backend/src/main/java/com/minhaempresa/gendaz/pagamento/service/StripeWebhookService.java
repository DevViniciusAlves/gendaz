package com.minhaempresa.gendaz.pagamento.service;

import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.gateway.StripeProperties;
import com.minhaempresa.gendaz.pagamento.repository.StripeWebhookEventRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookService {
    private static final Set<String> TIPOS_COM_DEDUPLICACAO_DE_NEGOCIO = Set.of(
            "checkout.session.completed",
            "checkout.session.expired",
            "invoice.payment_succeeded",
            "customer.subscription.deleted"
    );

    private final StripeProperties stripeProperties;
    private final PagamentoService pagamentoService;
    private final StripeWebhookEventRepository webhookEventRepository;

    @Transactional
    public void processar(String payload, String sigHeader) {
        Event event = construirEvento(payload, sigHeader);
        EventIdentity identity = identificar(event);
        int inseridos = webhookEventRepository.reservarEvento(
                event.getId(),
                event.getType(),
                identity.objectId(),
                identity.deduplicationKey()
        );
        if (inseridos == 0) {
            log.info("Webhook Stripe duplicado ignorado: eventId={}, type={}, objectId={}",
                    event.getId(), event.getType(), identity.objectId());
            return;
        }

        switch (event.getType()) {
            case "checkout.session.completed" -> processarCheckoutSessionCompleted(event);
            case "checkout.session.expired" -> processarCheckoutSessionExpired(event);
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

    private void processarCheckoutSessionExpired(Event event) {
        Session session = desserializar(event, Session.class);
        
        log.info("Processando checkout.session.expired: id={}", session.getId());
        pagamentoService.expirarCheckoutPorSessionStripe(session.getId());
    }

    private void processarInvoice(Event event, StatusPagamento status) {

        Invoice invoice = desserializar(event, Invoice.class);
        String invoiceId = invoice.getId();
        String subscriptionId = (String) new com.google.gson.Gson().fromJson(invoice.toJson(), java.util.Map.class).get("subscription");
        pagamentoService.processarInvoiceStripe(invoiceId, subscriptionId, status);
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

    @SuppressWarnings("unchecked")
    private EventIdentity identificar(Event event) {
        String objectId = null;
        try {
            Map<String, Object> eventJson = new com.google.gson.Gson().fromJson(event.toJson(), Map.class);
            Object dataValue = eventJson.get("data");
            if (dataValue instanceof Map<?, ?> data) {
                Object objectValue = data.get("object");
                if (objectValue instanceof Map<?, ?> object) {
                    Object idValue = object.get("id");
                    if (idValue != null) {
                        objectId = String.valueOf(idValue);
                    }
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Nao foi possivel extrair objectId do evento Stripe. eventId={}, type={}, erroTipo={}",
                    event.getId(), event.getType(), ex.getClass().getSimpleName());
        }

        String deduplicationKey = objectId != null && TIPOS_COM_DEDUPLICACAO_DE_NEGOCIO.contains(event.getType())
                ? event.getType() + ":" + objectId
                : null;
        return new EventIdentity(objectId, deduplicationKey);
    }

    private record EventIdentity(String objectId, String deduplicationKey) {}
}

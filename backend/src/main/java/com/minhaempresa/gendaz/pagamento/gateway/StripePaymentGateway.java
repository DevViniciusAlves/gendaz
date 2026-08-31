package com.minhaempresa.gendaz.pagamento.gateway;

import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoPlanoEntity;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.CustomerCollection;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerListParams;
import java.util.Map;
import com.stripe.model.checkout.Session;
import com.stripe.model.Subscription;
import com.stripe.param.checkout.SessionCreateParams;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(name = "payment.provider", havingValue = "STRIPE", matchIfMissing = true)
@RequiredArgsConstructor
public class StripePaymentGateway implements PaymentGateway {
    private final StripeProperties stripeProperties;
    private final PaymentGatewayProperties paymentGatewayProperties;
    private final EmpresaRepository empresaRepository;

    @Override
    public PaymentGatewayResponse criarPagamentoPlano(PagamentoPlanoEntity pagamento) {
        return criarPagamentoPlano(pagamento, pagamento.getEmpresa().getStripeCustomerId());
    }

    @Override
    public PaymentGatewayResponse criarPagamentoPlano(PagamentoPlanoEntity pagamento, String stripeCustomerId) {
        validarConfiguracao(pagamento);
        Stripe.apiKey = stripeProperties.getSecretKey();

        String plano = pagamento.getPlano().getNome();
        SessionCreateParams.LineItem lineItem = SessionCreateParams.LineItem.builder()
                .setPrice(stripeProperties.priceIdParaPlano(plano))
                .setQuantity(1L)
                .build();

        String customerId = resolverOuCriarStripeCustomer(pagamento.getEmpresa(), pagamento.getCustomerEmail(), pagamento.getCustomerName());

<<<<<<< HEAD
=======
        long expiresAtEpochSeconds = Instant.now().getEpochSecond() + 30 * 60;

>>>>>>> origin/stage
        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(successUrlComSessionId())
                .setCancelUrl(paymentGatewayProperties.getCancelUrl())
                .setCustomer(customerId)
<<<<<<< HEAD
=======
                .setExpiresAt(expiresAtEpochSeconds)
>>>>>>> origin/stage
                .addLineItem(lineItem)
                .putMetadata("empresaId", String.valueOf(pagamento.getEmpresa().getId()))
                .putMetadata("pagamentoPlanoId", String.valueOf(pagamento.getId()))
                .putMetadata("paymentReference", pagamento.getPaymentReference())
                .putMetadata("externalReference", pagamento.getExternalReference())
                .putMetadata("plano", plano);

        try {
            RequestOptions options = RequestOptions.builder()
                    .setIdempotencyKey("checkout-plano-" + pagamento.getId())
                    .build();
            Session session = Session.create(params.build(), options);
            
            pagamento.setStripeSessionId(session.getId());
            pagamento.setStripeCustomerId(customerId);
            return new PaymentGatewayResponse(
                    "STRIPE",
                    session.getId(),
                    pagamento.getExternalReference(),
                    pagamento.getPaymentReference(),
                    session.getUrl(),
                    session.getExpiresAt() == null ? null : LocalDateTime.ofInstant(Instant.ofEpochSecond(session.getExpiresAt()), ZoneId.systemDefault())
            );
        } catch (StripeException ex) {
            throw new BusinessException("Nao foi possivel criar checkout Stripe. Tente novamente em instantes.");
        }
    }

    private String resolverOuCriarStripeCustomer(com.minhaempresa.gendaz.empresa.entity.EmpresaEntity empresa, String email, String nome) {
        if (empresa.getStripeCustomerId() != null) {
            return empresa.getStripeCustomerId();
        }

        try {
            CustomerListParams listParams = CustomerListParams.builder().setEmail(email).build();
            CustomerCollection customers = Customer.list(listParams);
            for (Customer customer : customers.getData()) {
                if (customer.getMetadata() != null &&
                    String.valueOf(empresa.getId()).equals(customer.getMetadata().get("gendazEmpresaId"))) {
                    empresa.setStripeCustomerId(customer.getId());
                    empresaRepository.save(empresa);
                    return customer.getId();
                }
            }
        } catch (StripeException e) {
            log.warn("Erro ao buscar customer Stripe por email. erroTipo={}", e.getClass().getSimpleName());
        }

        try {
            CustomerCreateParams createParams = CustomerCreateParams.builder()
                    .setEmail(email)
                    .setName(nome)
                    .putMetadata("gendazEmpresaId", String.valueOf(empresa.getId()))
                    .build();
            RequestOptions options = RequestOptions.builder()
                    .setIdempotencyKey("gendaz-customer-empresa-" + empresa.getId())
                    .build();
            Customer customer = Customer.create(createParams, options);
            empresa.setStripeCustomerId(customer.getId());
            empresaRepository.save(empresa);
            return customer.getId();
        } catch (StripeException e) {
            throw new BusinessException("Falha ao criar Customer no Stripe.");
        }
    }

    private String successUrlComSessionId() {
        String url = paymentGatewayProperties.getSuccessUrl();
        if (url == null || url.isBlank()) {
            throw new BusinessException("PAYMENT_SUCCESS_URL nao configurada.");
        }
        if (url.contains("{CHECKOUT_SESSION_ID}")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + "session_id={CHECKOUT_SESSION_ID}";
    }

    @Override
    public Optional<PaymentGatewayWebhook> consultarPagamentoPlano(PagamentoPlanoEntity pagamento) {
        if (pagamento.getStripeSessionId() == null || pagamento.getStripeSessionId().isBlank()) {
            return Optional.empty();
        }
        Stripe.apiKey = stripeProperties.getSecretKey();
        try {
            Session session = Session.retrieve(pagamento.getStripeSessionId());
            if (session != null) {
                StatusPagamento status = StatusPagamento.PAYMENT_PENDING;
                if ("complete".equals(session.getStatus()) && "paid".equals(session.getPaymentStatus())) {
                    status = StatusPagamento.PAYMENT_APPROVED;
                } else if ("expired".equals(session.getStatus())) {
                    status = StatusPagamento.PAYMENT_EXPIRED;
                } else if ("open".equals(session.getStatus())) {
                    status = StatusPagamento.PAYMENT_PENDING;
                } else {
                    status = StatusPagamento.PAYMENT_CANCELED;
                }
                
                return Optional.of(new PaymentGatewayWebhook(
                    session.getId(),
                    session.getId(),
                    pagamento.getExternalReference(),
                    pagamento.getPaymentReference(),
                    status,
                    pagamento.getValor()
                ));
            }
        } catch (StripeException ex) {
            log.warn("Erro ao consultar session na Stripe para pagamentoId={}. erroTipo={}", pagamento.getId(), ex.getClass().getSimpleName());
        }
        return Optional.empty();
    }

    /**
     * Método legado inseguro. Não deve ser usado.
     * Webhook Stripe deve usar assinatura via Stripe-Signature e webhookSecret.
     */
    @Override
    @Deprecated
    public boolean validarWebhook(String assinatura, PaymentGatewayWebhook webhook) {
        throw new BusinessException("Método legado desativado. Use o webhook Stripe assinado.");
    }

    @Override
    public void cancelarSubscription(String subscriptionId) {
        if (subscriptionId == null || subscriptionId.isBlank()) {
            throw new BusinessException("Subscription ID não pode ser vazio.");
        }
        
        Stripe.apiKey = stripeProperties.getSecretKey();
        try {
            Subscription subscription = Subscription.retrieve(subscriptionId);
            subscription.cancel();
            log.info("Subscription Stripe cancelada com sucesso");
        } catch (Exception ex) {
            throw new BusinessException("Falha ao cancelar subscription Stripe: " + ex.getMessage());
        }
    }

    @Override
    public void expirarCheckoutSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        Stripe.apiKey = stripeProperties.getSecretKey();
        try {
            Session session = Session.retrieve(sessionId);
            if ("open".equals(session.getStatus())) {
                session.expire();
                log.info("Checkout Session Stripe expirada com sucesso");
            } else {
                log.info("Checkout Session Stripe com status terminal: status={}", session.getStatus());
            }
        } catch (StripeException ex) {
            log.warn("Erro ao expirar session na Stripe. erroTipo={}", ex.getClass().getSimpleName());
        }
    }

    @Override
<<<<<<< HEAD
=======
    public void expirarCheckoutSessionThrows(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        Stripe.apiKey = stripeProperties.getSecretKey();
        try {
            Session session = Session.retrieve(sessionId);
            if ("open".equals(session.getStatus())) {
                session.expire();
                log.info("Checkout Session Stripe expirada com sucesso (throws)");
            } else {
                log.info("Checkout Session Stripe com status terminal: status={}", session.getStatus());
            }
        } catch (StripeException ex) {
            throw new BusinessException("Falha ao expirar Checkout Session na Stripe: " + ex.getMessage());
        }
    }

    @Override
>>>>>>> origin/stage
    public PaymentGatewayWebhook consultarPagamentoWebhook(String providerPaymentId, String assinatura, String requestId) {
        throw new BusinessException("Consulta de webhook legado desativada. Use o webhook Stripe assinado.");
    }

    private void validarConfiguracao(PagamentoPlanoEntity pagamento) {
        if (stripeProperties.getSecretKey() == null || stripeProperties.getSecretKey().isBlank()) {
            throw new BusinessException("STRIPE_SECRET_KEY nao configurada.");
        }
        String priceId = stripeProperties.priceIdParaPlano(pagamento.getPlano().getNome());
        if (priceId == null || priceId.isBlank()) {
            throw new BusinessException("Price ID Stripe nao configurado para o plano " + pagamento.getPlano().getNome() + ".");
        }
    }
}

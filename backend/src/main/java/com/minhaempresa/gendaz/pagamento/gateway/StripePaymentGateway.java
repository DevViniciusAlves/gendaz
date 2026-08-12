package com.minhaempresa.gendaz.pagamento.gateway;

import com.minhaempresa.gendaz.pagamento.entity.PagamentoPlanoEntity;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "STRIPE", matchIfMissing = true)
@RequiredArgsConstructor
public class StripePaymentGateway implements PaymentGateway {
    private final StripeProperties stripeProperties;
    private final PaymentGatewayProperties paymentGatewayProperties;

    @Override
    public PaymentGatewayResponse criarPagamentoPlano(PagamentoPlanoEntity pagamento) {
        validarConfiguracao(pagamento);
        Stripe.apiKey = stripeProperties.getSecretKey();

        String plano = pagamento.getPlano().getNome();
        SessionCreateParams.CustomerCreation customerCreation = SessionCreateParams.CustomerCreation.ALWAYS;
        SessionCreateParams.LineItem lineItem = SessionCreateParams.LineItem.builder()
                .setPrice(stripeProperties.priceIdParaPlano(plano))
                .setQuantity(1L)
                .build();

        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(paymentGatewayProperties.getSuccessUrl())
                .setCancelUrl(paymentGatewayProperties.getCancelUrl())
                .setCustomerCreation(customerCreation)
                .addLineItem(lineItem)
                .putMetadata("empresaId", String.valueOf(pagamento.getEmpresa().getId()))
                .putMetadata("pagamentoPlanoId", String.valueOf(pagamento.getId()))
                .putMetadata("paymentReference", pagamento.getPaymentReference())
                .putMetadata("externalReference", pagamento.getExternalReference())
                .putMetadata("plano", plano);

        if (pagamento.getCustomerEmail() != null && !pagamento.getCustomerEmail().isBlank()) {
            params.setCustomerEmail(pagamento.getCustomerEmail());
        }

        try {
            Session session = Session.create(params.build());
            pagamento.setStripeSessionId(session.getId());
            pagamento.setStripeCustomerId(session.getCustomer());
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

    @Override
    public boolean validarWebhook(String assinatura, PaymentGatewayWebhook webhook) {
        return false;
    }

    @Override
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

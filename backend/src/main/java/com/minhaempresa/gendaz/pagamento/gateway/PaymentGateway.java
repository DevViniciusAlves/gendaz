package com.minhaempresa.gendaz.pagamento.gateway;

import com.minhaempresa.gendaz.pagamento.entity.PagamentoPlanoEntity;
import java.util.Optional;

public interface PaymentGateway {
    PaymentGatewayResponse criarPagamentoPlano(PagamentoPlanoEntity pagamento);
    PaymentGatewayResponse criarPagamentoPlano(PagamentoPlanoEntity pagamento, String stripeCustomerId);
    boolean validarWebhook(String assinatura, PaymentGatewayWebhook webhook);
    PaymentGatewayWebhook consultarPagamentoWebhook(String providerPaymentId, String assinatura, String requestId);
    void cancelarSubscription(String subscriptionId);
    void expirarCheckoutSession(String sessionId);

    default Optional<PaymentGatewayWebhook> consultarPagamentoPlano(PagamentoPlanoEntity pagamento) {
        return Optional.empty();
    }
}


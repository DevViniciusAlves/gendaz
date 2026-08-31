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

    /**
     * Expira a Checkout Session no gateway. Lança exceção se o gateway estiver indisponível
     * ou se a expiração falhar por motivo transiente. Não lança se a session já estiver em
     * estado terminal (expirada, completa, etc.).
     *
     * @throws com.minhaempresa.gendaz.shared.BusinessException se o gateway falhar ou estiver indisponível
     */
    void expirarCheckoutSessionThrows(String sessionId);

    default Optional<PaymentGatewayWebhook> consultarPagamentoPlano(PagamentoPlanoEntity pagamento) {
        return Optional.empty();
    }
}


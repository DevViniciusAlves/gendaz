package com.minhaempresa.gendaz.pagamento.gateway;

import com.minhaempresa.gendaz.pagamento.entity.PagamentoPlanoEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
public class PaymentLocalGateway implements PaymentGateway {
    private final PaymentGatewayProperties properties;

    @Override
    public PaymentGatewayResponse criarPagamentoPlano(PagamentoPlanoEntity pagamento) {
        String providerPaymentId = "pay_" + UUID.randomUUID();
        String checkoutUrl = properties.getSuccessUrl() + "?paymentId=" + providerPaymentId;
        String pixCopiaECola = "PIX-" + providerPaymentId;
        return new PaymentGatewayResponse(
                properties.getProvider(),
                providerPaymentId,
                pagamento.getExternalReference(),
                pagamento.getPaymentReference(),
                checkoutUrl,
                pixCopiaECola,
                null,
                LocalDateTime.now().plusMinutes(30)
        );
    }

    @Override
    public boolean validarWebhook(String assinatura, PaymentGatewayWebhook webhook) {
        if (assinatura == null || assinatura.isBlank() || properties.getWebhookSecret() == null || properties.getWebhookSecret().isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                assinatura.getBytes(StandardCharsets.UTF_8),
                properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    public PaymentGatewayWebhook consultarPagamentoWebhook(String providerPaymentId, String assinatura, String requestId) {
        return new PaymentGatewayWebhook(requestId, providerPaymentId, null, null, null, null);
    }
}


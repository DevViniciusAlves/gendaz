package com.minhaempresa.gendaz.pagamento.gateway;

import java.time.LocalDateTime;

public record PaymentGatewayResponse(
        String provider,
        String providerPaymentId,
        String externalReference,
        String paymentReference,
        String checkoutUrl,
        LocalDateTime dataExpiracao
) {}


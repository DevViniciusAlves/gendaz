package com.minhaempresa.agendapro.pagamento.gateway;

import java.time.LocalDateTime;

public record PaymentGatewayResponse(
        String provider,
        String providerPaymentId,
        String externalReference,
        String paymentReference,
        String checkoutUrl,
        String pixCopiaECola,
        String pixQrCodeBase64,
        LocalDateTime dataExpiracao
) {}

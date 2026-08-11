package com.minhaempresa.gendaz.pagamento.gateway;

import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import java.math.BigDecimal;

public record PaymentGatewayWebhook(
        String eventId,
        String providerPaymentId,
        String externalReference,
        String paymentReference,
        StatusPagamento status,
        BigDecimal valor
) {}


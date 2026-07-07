package com.minhaempresa.agendapro.pagamento.gateway;

import com.minhaempresa.agendapro.pagamento.enums.StatusPagamento;
import java.math.BigDecimal;

public record PaymentGatewayWebhook(
        String eventId,
        String providerPaymentId,
        String externalReference,
        String paymentReference,
        StatusPagamento status,
        BigDecimal valor
) {}

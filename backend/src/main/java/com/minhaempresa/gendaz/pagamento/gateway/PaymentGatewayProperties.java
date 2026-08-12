package com.minhaempresa.gendaz.pagamento.gateway;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "payment")
public class PaymentGatewayProperties {
    private String provider = "STRIPE";
    private String apiKey = "";
    private String webhookSecret = "";
    private String successUrl = "https://gendaz.site/sistema/planos";
    private String cancelUrl = "https://gendaz.site/sistema/planos";
}


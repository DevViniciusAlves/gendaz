package com.minhaempresa.gendaz.pagamento.gateway;

import com.minhaempresa.gendaz.shared.BusinessException;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "stripe")
public class StripeProperties {
    private String secretKey = "";
    private String publishableKey = "";
    private String webhookSecret = "";
    private String priceBasicoId = "";
    private String priceProId = "";
    private String pricePlusId = "";
    private String priceEnterpriseId = "";

    public String priceIdParaPlano(String planoNome) {
        if ("BASICO".equalsIgnoreCase(planoNome)) return priceBasicoId;
        if ("PRO".equalsIgnoreCase(planoNome)) return priceProId;
        if ("PLUS".equalsIgnoreCase(planoNome)) return pricePlusId;
        if ("ENTERPRISE".equalsIgnoreCase(planoNome)) return priceEnterpriseId;
        throw new BusinessException("Plano invalido para Stripe.");
    }
}

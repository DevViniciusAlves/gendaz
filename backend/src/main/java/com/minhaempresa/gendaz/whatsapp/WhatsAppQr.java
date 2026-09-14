package com.minhaempresa.gendaz.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * QR atualmente valido. Trafega apenas em memoria: nunca e persistido nem
 * logado (ver {@link WhatsAppServiceProvider}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsAppQr {

    @JsonProperty("qr")
    private String qr;

    @JsonProperty("updatedAt")
    private String updatedAt;

    public String getQr() {
        return qr;
    }

    public void setQr(String qr) {
        this.qr = qr;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}

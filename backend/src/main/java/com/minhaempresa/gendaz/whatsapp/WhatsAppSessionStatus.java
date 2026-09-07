package com.minhaempresa.gendaz.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Status publico da sessao, espelhando o contrato do whatsapp-service.
 *
 * <p>{@code state} e tratado como texto operacional (sem enum de codigos
 * internos do Baileys). Datas ISO trafegam como String para nao exigir
 * configuracao artificial de Jackson nesta fase.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsAppSessionStatus {

    @JsonProperty("companyId")
    private String companyId;

    @JsonProperty("state")
    private String state;

    @JsonProperty("hasQr")
    private boolean hasQr;

    @JsonProperty("qrUpdatedAt")
    private String qrUpdatedAt;

    @JsonProperty("connectedAt")
    private String connectedAt;

    @JsonProperty("reconnectAttempts")
    private Integer reconnectAttempts;

    @JsonProperty("lastDisconnectCode")
    private Integer lastDisconnectCode;

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public boolean isHasQr() {
        return hasQr;
    }

    public void setHasQr(boolean hasQr) {
        this.hasQr = hasQr;
    }

    public String getQrUpdatedAt() {
        return qrUpdatedAt;
    }

    public void setQrUpdatedAt(String qrUpdatedAt) {
        this.qrUpdatedAt = qrUpdatedAt;
    }

    public String getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(String connectedAt) {
        this.connectedAt = connectedAt;
    }

    public Integer getReconnectAttempts() {
        return reconnectAttempts;
    }

    public void setReconnectAttempts(Integer reconnectAttempts) {
        this.reconnectAttempts = reconnectAttempts;
    }

    public Integer getLastDisconnectCode() {
        return lastDisconnectCode;
    }

    public void setLastDisconnectCode(Integer lastDisconnectCode) {
        this.lastDisconnectCode = lastDisconnectCode;
    }
}

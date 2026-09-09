package com.minhaempresa.gendaz.whatsapp;

/**
 * Recurso WhatsApp para o restante do gendaz.
 *
 * <p>Abstracao desacoplada da tecnologia do provider atual: o Spring conversa
 * apenas com o contrato HTTP do whatsapp-service e nunca com detalhes internos
 * do Baileys (sem Signal Keys, sem credenciais, sem logica de sessao aqui).
 */
public interface WhatsAppProvider {

    WhatsAppResult<WhatsAppSessionStatus> conectar(String companyId);

    WhatsAppResult<WhatsAppSessionStatus> consultarStatus(String companyId);

    WhatsAppResult<WhatsAppQr> obterQr(String companyId);

    WhatsAppResult<WhatsAppSessionStatus> logout(String companyId);

    /**
     * Envia texto via POST /internal/whatsapp/sessions/{companyId}/messages/text.
     *
     * @param requestId chave estavel da notificacao (idempotencia no Node)
     */
    WhatsAppSendResult enviarTexto(String companyId, String recipient, String text, String requestId);

    boolean disponivel();
}

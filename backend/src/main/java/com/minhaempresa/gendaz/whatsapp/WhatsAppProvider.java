package com.minhaempresa.gendaz.whatsapp;

/**
 * Recurso WhatsApp para o restante do gendaz.
 *
 * <p>Abstracao desacoplada da tecnologia do provider atual: o Spring conversa
 * apenas com o contrato HTTP do whatsapp-service e nunca com detalhes internos
 * do Baileys (sem Signal Keys, sem credenciais, sem logica de sessao aqui).
 *
 * <p>Nesta fase nao existe envio de mensagens.
 */
public interface WhatsAppProvider {

    WhatsAppResult<WhatsAppSessionStatus> conectar(String companyId);

    WhatsAppResult<WhatsAppSessionStatus> consultarStatus(String companyId);

    WhatsAppResult<WhatsAppQr> obterQr(String companyId);

    WhatsAppResult<WhatsAppSessionStatus> logout(String companyId);

    boolean disponivel();
}

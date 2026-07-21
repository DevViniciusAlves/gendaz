package com.minhaempresa.agendapro.mensagem.gateway;

//  DESATIVADO — Esta interface é exclusiva para integração com WhatsApp.
//  DESATIVADO — Todos os métodos estão desativados. Não utilizar em produção.
public interface WhatsappGateway {
    void enviarMensagem(String telefone, String conteudo);
}

package com.minhaempresa.agendapro.mensagem.gateway;

public interface WhatsappGateway {
    void enviarMensagem(String telefone, String conteudo);
}

package com.minhaempresa.agendapro.mensagem.gateway;

import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.shared.CompanyContext;
import com.minhaempresa.agendapro.whatsapp.service.WhatsappNodeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WhatsappFakeGateway implements WhatsappGateway {
    private final WhatsappNodeClient whatsappNodeClient;

    @Override
    public void enviarMensagem(String telefone, String conteudo) {
        Long empresaId = CompanyContext.getCompanyId();
        if (empresaId == null) {
            throw new BusinessException("Empresa nao identificada para envio do WhatsApp.");
        }
        try {
            whatsappNodeClient.enviarMensagem(empresaId, telefone, conteudo);
        } catch (RuntimeException ex) {
            log.warn("Falha ao enviar mensagem WhatsApp pela integracao Node: empresa={}, telefone={}", empresaId, telefone);
            throw new BusinessException("Nao foi possivel enviar a mensagem pelo WhatsApp.");
        }
    }
}

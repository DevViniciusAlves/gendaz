package com.minhaempresa.agendapro.mensagem.gateway;

// ⚠️ DESATIVADO — Esta classe é exclusiva para integração com WhatsApp.
// ⚠️ DESATIVADO — Todos os métodos estão desativados. Não utilizar em produção.

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
        // ⚠️ DESATIVADO — Long empresaId = CompanyContext.getCompanyId();
        // ⚠️ DESATIVADO — if (empresaId == null) {
        // ⚠️ DESATIVADO —     throw new BusinessException("Empresa nao identificada para envio do WhatsApp.");
        // ⚠️ DESATIVADO — }
        // ⚠️ DESATIVADO — try {
        // ⚠️ DESATIVADO —     whatsappNodeClient.enviarMensagem(empresaId, telefone, conteudo);
        // ⚠️ DESATIVADO — } catch (RuntimeException ex) {
        // ⚠️ DESATIVADO —     log.warn("Falha ao enviar mensagem WhatsApp pela integracao Node: empresa={}, telefone={}", empresaId, telefone);
        // ⚠️ DESATIVADO —     throw new BusinessException("Nao foi possivel enviar a mensagem pelo WhatsApp.");
        // ⚠️ DESATIVADO — }
    }
}

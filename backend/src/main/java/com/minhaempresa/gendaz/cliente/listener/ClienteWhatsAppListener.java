package com.minhaempresa.gendaz.cliente.listener;

import com.minhaempresa.gendaz.cliente.event.ClienteWhatsAppOptOutEvent;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppNotificacaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Cancela pendencias WhatsApp apos commit do opt-out. Roda fora da
 * transacao do cliente, em transacao propria do servico de cancelamento:
 * falha aqui jamais desfaz uma atualizacao ja commitada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClienteWhatsAppListener {

    private final WhatsAppNotificacaoService notificacaoService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClienteWhatsAppOptOut(ClienteWhatsAppOptOutEvent event) {
        if (event == null || event.empresaId() == null || event.clienteId() == null) {
            return;
        }
        try {
            notificacaoService.cancelarPendenciasCliente(event.empresaId(), event.clienteId());
        } catch (Exception e) {
            log.error("[cliente-whatsapp] falha isolada ao cancelar pendencias cliente={} empresa={}. erroTipo={}",
                    event.clienteId(), event.empresaId(), e.getClass().getSimpleName());
        }
    }
}

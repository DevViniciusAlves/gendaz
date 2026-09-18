package com.minhaempresa.gendaz.agendamento.listener;

import com.minhaempresa.gendaz.agendamento.event.AgendamentoWhatsAppSyncEvent;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppLembreteAgendamentoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sincronizacao WhatsApp apos commit da agenda. Roda fora da transacao do
 * dominio: qualquer falha tecnica e registrada com IDs e jamais transforma
 * uma operacao de agenda commitada em erro para o usuario.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgendamentoWhatsAppListener {

    private final WhatsAppLembreteAgendamentoService lembreteService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAgendamentoWhatsAppSync(AgendamentoWhatsAppSyncEvent event) {
        if (event == null || event.agendamentoId() == null || event.empresaId() == null || event.acao() == null) {
            return;
        }
        try {
            if (event.acao() == AgendamentoWhatsAppSyncEvent.Acao.CANCELAR) {
                lembreteService.cancelar(event.empresaId(), event.agendamentoId());
            } else {
                lembreteService.sincronizar(event.empresaId(), event.agendamentoId());
            }
        } catch (Exception e) {
            log.error("[agendamento-whatsapp] falha isolada na sincronizacao agendamento={} empresa={} acao={}. erroTipo={}",
                    event.agendamentoId(), event.empresaId(), event.acao(), e.getClass().getSimpleName());
        }
    }
}

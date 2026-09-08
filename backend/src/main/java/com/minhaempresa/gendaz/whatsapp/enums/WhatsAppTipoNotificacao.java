package com.minhaempresa.gendaz.whatsapp.enums;

/**
 * Tipos de notificacao WhatsApp suportados pelo nucleo persistente.
 * A categoria de cota e derivada do tipo: lembrete e CRM sao franquias
 * independentes, e Resgate/Reconexao compartilham a mesma cota CRM.
 */
public enum WhatsAppTipoNotificacao {
    LEMBRETE_AGENDAMENTO,
    CRM_RESGATE,
    CRM_RECONEXAO;

    public WhatsAppCategoriaCota categoria() {
        return switch (this) {
            case LEMBRETE_AGENDAMENTO -> WhatsAppCategoriaCota.LEMBRETE;
            case CRM_RESGATE, CRM_RECONEXAO -> WhatsAppCategoriaCota.CRM;
        };
    }
}

package com.minhaempresa.gendaz.whatsapp.policy;

import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppCategoriaCota;

/**
 * Unica fonte de verdade dos limites da franquia WhatsApp por plano.
 *
 * O identificador canonico do plano e {@code planos.nome} (BASICO, PRO,
 * PLUS, ENTERPRISE), a mesma fonte utilizada pelo restante do gendaz.
 * Planos desconhecidos ou ausentes equivalem ao Basico: sem WhatsApp.
 *
 * Esperado: BASICO 0/0, PRO 150/10, PLUS 300/15, ENTERPRISE 500/20.
 */
public final class WhatsAppPlanoPolicy {

    private WhatsAppPlanoPolicy() {
    }

    public record Limites(int lembretes, int crm) {
        public boolean possuiWhatsApp() {
            return lembretes > 0 || crm > 0;
        }
    }

    public static Limites limitesPara(String nomePlano) {
        if (nomePlano == null) {
            return new Limites(0, 0);
        }
        return switch (nomePlano.trim().toUpperCase()) {
            case "PRO" -> new Limites(150, 10);
            case "PLUS" -> new Limites(300, 15);
            case "ENTERPRISE" -> new Limites(500, 20);
            default -> new Limites(0, 0);
        };
    }

    public static boolean possuiWhatsApp(String nomePlano) {
        return limitesPara(nomePlano).possuiWhatsApp();
    }

    public static int limitePara(WhatsAppCategoriaCota categoria, String nomePlano) {
        Limites limites = limitesPara(nomePlano);
        return categoria == WhatsAppCategoriaCota.LEMBRETE ? limites.lembretes() : limites.crm();
    }
}

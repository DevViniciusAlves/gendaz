package com.minhaempresa.gendaz.whatsapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppCategoriaCota;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppTipoNotificacao;
import com.minhaempresa.gendaz.whatsapp.policy.WhatsAppPlanoPolicy;
import org.junit.jupiter.api.Test;

class WhatsAppPlanoPolicyTest {

    @Test
    void basicoNaoPossuiWhatsApp() {
        WhatsAppPlanoPolicy.Limites limites = WhatsAppPlanoPolicy.limitesPara("BASICO");
        assertEquals(0, limites.lembretes());
        assertEquals(0, limites.crm());
        assertFalse(WhatsAppPlanoPolicy.possuiWhatsApp("BASICO"));
    }

    @Test
    void proTem150LembretesE10Crm() {
        WhatsAppPlanoPolicy.Limites limites = WhatsAppPlanoPolicy.limitesPara("PRO");
        assertEquals(150, limites.lembretes());
        assertEquals(10, limites.crm());
        assertTrue(WhatsAppPlanoPolicy.possuiWhatsApp("PRO"));
    }

    @Test
    void plusTem300LembretesE15Crm() {
        WhatsAppPlanoPolicy.Limites limites = WhatsAppPlanoPolicy.limitesPara("PLUS");
        assertEquals(300, limites.lembretes());
        assertEquals(15, limites.crm());
        assertTrue(WhatsAppPlanoPolicy.possuiWhatsApp("PLUS"));
    }

    @Test
    void enterpriseTem500LembretesE20Crm() {
        WhatsAppPlanoPolicy.Limites limites = WhatsAppPlanoPolicy.limitesPara("ENTERPRISE");
        assertEquals(500, limites.lembretes());
        assertEquals(20, limites.crm());
        assertTrue(WhatsAppPlanoPolicy.possuiWhatsApp("ENTERPRISE"));
    }

    @Test
    void planoDesconhecidoOuAusenteEquivaleASemWhatsApp() {
        assertEquals(new WhatsAppPlanoPolicy.Limites(0, 0), WhatsAppPlanoPolicy.limitesPara("INEXISTENTE"));
        assertEquals(new WhatsAppPlanoPolicy.Limites(0, 0), WhatsAppPlanoPolicy.limitesPara(null));
        assertFalse(WhatsAppPlanoPolicy.possuiWhatsApp("INEXISTENTE"));
        assertFalse(WhatsAppPlanoPolicy.possuiWhatsApp(null));
    }

    @Test
    void resgateEReconexaoCompartilhamCategoriaCrm() {
        assertEquals(
                WhatsAppCategoriaCota.CRM,
                WhatsAppTipoNotificacao.CRM_RESGATE.categoria());
        assertEquals(
                WhatsAppCategoriaCota.CRM,
                WhatsAppTipoNotificacao.CRM_RECONEXAO.categoria());
    }

    @Test
    void lembreteUsaCategoriaPropriaIndependenteDoCrm() {
        assertEquals(
                WhatsAppCategoriaCota.LEMBRETE,
                WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO.categoria());
    }

    @Test
    void limitePorCategoriaSeparaLembreteDeCrm() {
        assertEquals(150, WhatsAppPlanoPolicy.limitePara(WhatsAppCategoriaCota.LEMBRETE, "PRO"));
        assertEquals(10, WhatsAppPlanoPolicy.limitePara(WhatsAppCategoriaCota.CRM, "PRO"));
        assertEquals(0, WhatsAppPlanoPolicy.limitePara(WhatsAppCategoriaCota.LEMBRETE, "BASICO"));
        assertEquals(0, WhatsAppPlanoPolicy.limitePara(WhatsAppCategoriaCota.CRM, "BASICO"));
    }
}

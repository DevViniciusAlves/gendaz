package com.minhaempresa.gendaz.whatsapp.service;

import java.time.LocalDate;

/**
 * Fotografia da franquia WhatsApp da empresa no ciclo vigente da
 * assinatura. Reserva nao e consumo: disponibilidade = limite - enviados
 * - reservados.
 */
public record WhatsAppUsoResponse(
        Long empresaId,
        String planoNome,
        LocalDate cicloInicio,
        LocalDate cicloFim,
        int limiteLembretes,
        int limiteCrm,
        int lembretesReservados,
        int lembretesEnviados,
        int crmReservados,
        int crmEnviados,
        int lembretesDisponiveis,
        int crmDisponiveis) {
}

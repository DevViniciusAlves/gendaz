package com.minhaempresa.gendaz.cliente.event;

/**
 * Opt-out de WhatsApp: o cliente passou de receberWhatsapp true para false.
 * Contem apenas IDs tecnicos. O cancelamento das pendencias ocorre apos o
 * commit real da atualizacao do cliente.
 */
public record ClienteWhatsAppOptOutEvent(
        Long empresaId,
        Long clienteId
) {}

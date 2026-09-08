package com.minhaempresa.gendaz.whatsapp.service;

/**
 * Resposta semantica de disponibilidade de cota. Cota esgotada e fluxo
 * normal de dominio: nao lanca exception generica.
 */
public enum WhatsAppDisponibilidade {
    DISPONIVEL,
    LIMITE_ATINGIDO,
    PLANO_SEM_WHATSAPP
}

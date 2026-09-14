package com.minhaempresa.gendaz.whatsapp.service;

import java.time.LocalDate;

/**
 * Resultado da reserva com o ciclo em que foi feita. O worker persiste o
 * ciclo na notificacao para confirmar/liberar exatamente nele.
 */
public record ReservaCota(WhatsAppReserva resultado, LocalDate cicloInicio) {
}

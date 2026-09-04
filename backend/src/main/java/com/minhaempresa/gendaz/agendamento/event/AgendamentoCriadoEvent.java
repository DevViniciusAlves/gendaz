package com.minhaempresa.gendaz.agendamento.event;

import java.time.LocalDate;
import java.time.LocalTime;

public record AgendamentoCriadoEvent(
        Long agendamentoId,
        Long empresaId,
        String empresaNomeFantasia,
        String empresaAgendamentoSlug,
        String empresaEmail,
        Long clienteId,
        String clienteNome,
        String clienteEmail,
        Long servicoId,
        String servicoNome,
        Long profissionalId,
        String profissionalNome,
        LocalDate data,
        LocalTime horaInicio
) {}

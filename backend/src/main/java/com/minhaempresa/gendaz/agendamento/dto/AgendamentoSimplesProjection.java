package com.minhaempresa.gendaz.agendamento.dto;

import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import java.time.LocalDate;
import java.time.LocalTime;

public record AgendamentoSimplesProjection(
        Long id,
        String clienteNome,
        String clienteTelefone,
        String servicoNome,
        LocalDate data,
        LocalTime horaInicio,
        StatusAgendamento status
) {
    public String getStatus() {
        return status == null ? null : status.name();
    }
}


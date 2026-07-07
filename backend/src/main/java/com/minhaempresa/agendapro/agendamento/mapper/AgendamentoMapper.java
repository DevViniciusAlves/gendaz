package com.minhaempresa.agendapro.agendamento.mapper;

import com.minhaempresa.agendapro.agendamento.dto.AgendamentoDtos.AgendamentoResponse;
import com.minhaempresa.agendapro.agendamento.entity.AgendamentoEntity;

public class AgendamentoMapper {
    public AgendamentoResponse toResponse(AgendamentoEntity agendamento) {
        return new AgendamentoResponse(
                agendamento.getId(),
                agendamento.getProtocolo(),
                agendamento.getCliente().getId(),
                agendamento.getCliente().getNome(),
                agendamento.getServico().getId(),
                agendamento.getServico().getNome(),
                agendamento.getProfissional().getId(),
                agendamento.getProfissional().getNome(),
                agendamento.getEmpresa().getId(),
                agendamento.getData(),
                agendamento.getHoraInicio(),
                agendamento.getHoraFim(),
                agendamento.getStatus(),
                agendamento.getObservacoes()
        );
    }
}

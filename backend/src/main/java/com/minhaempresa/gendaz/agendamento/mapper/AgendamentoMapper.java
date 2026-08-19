package com.minhaempresa.gendaz.agendamento.mapper;

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AgendamentoResponse;
import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import java.math.BigDecimal;

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
                valorHistorico(agendamento),
                agendamento.getData(),
                agendamento.getHoraInicio(),
                agendamento.getHoraFim(),
                agendamento.getStatus(),
                agendamento.getObservacoes(),
                agendamento.getValorOriginal(),
                agendamento.getValorDesconto(),
                agendamento.getValorFinal(),
                agendamento.getCupomCodigo(),
                agendamento.getTipoPromocaoAplicada(),
                agendamento.getValorPromocaoAplicada(),
                agendamento.getCliente() != null ? agendamento.getCliente().getStatus() : null
        );
    }

    /**
     * Campo {@code valor} mantido para compatibilidade com consumidores atuais.
     * Novos agendamentos: o total final do snapshot (valorFinal).
     * Registros antigos sem snapshot: comportamento legado (preco do servico).
     */
    private BigDecimal valorHistorico(AgendamentoEntity agendamento) {
        if (agendamento.getValorFinal() != null) {
            return agendamento.getValorFinal();
        }
        return agendamento.getServico() != null ? agendamento.getServico().getValor() : null;
    }
}
package com.minhaempresa.gendaz.admin.dto;

import com.minhaempresa.gendaz.crm.dto.CrmDtos.CrmUltimaMensagem;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class AdminCrmDtos {

    public record AdminCrmEmpresaResponse(
            Long id,
            String nome,
            String email,
            String empresaNome,
            String telefone,
            String segment,
            int diasSemAgendar,
            LocalDateTime ultimaEntradaSite,
            LocalDate ultimoAgendamentoData,
            double totalGasto,
            double gastoMedio,
            int agendamentos,
            int padraoFrequencia,
            int scoreRisco,
            String planoAtual,
            int quantidadePlanos,
            CrmUltimaMensagem ultimaMensagem
    ) {}

    public record AdminEnviarMensagemRequest(
            String template,
            String canal,
            String customMessage
    ) {}
}

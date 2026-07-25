package com.minhaempresa.agendapro.crm.dto;

import java.time.LocalDateTime;

public class CrmDtos {

    public record CrmClienteResponse(
            Long id,
            String nome,
            String telefone,
            String email,
            String segment,
            int diasSemAgendar,
            java.time.LocalDate ultimoAgendamentoData,
            double totalGasto,
            double gastoMedio,
            int agendamentos,
            int padraoFrequencia,
            int scoreRisco,
            CrmUltimaMensagem ultimaMensagem
    ) {}

    public record CrmUltimaMensagem(
            String tipo,
            String template,
            LocalDateTime dataCriacao,
            String status
    ) {}

    public record EnviarMensagemRequest(
            String template,
            String canal,
            String customMessage
    ) {}

    public record HistoricoContatoResponse(
            Long id,
            String tipo,
            String template,
            String assunto,
            LocalDateTime dataCriacao,
            String status,
            LocalDateTime aberturaData
    ) {}
}

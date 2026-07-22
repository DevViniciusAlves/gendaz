package com.minhaempresa.agendapro.insights.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;

public final class InsightsDtos {
    private InsightsDtos() {}

    public record InsightsRequest(@NotBlank String pergunta) {}

    public record InsightItem(
            String titulo,
            String descricao,
            String impacto,
            String urgencia,
            String tipo
    ) {}

    public record InsightAction(
            String descricao,
            String urgencia,
            String impactoEstimado
    ) {}

    public record DashboardResponse(
            Long empresaId,
            String empresaNome,
            Integer scoreGeral,
            List<InsightItem> alertas,
            List<InsightItem> oportunidades,
            List<InsightAction> acoes,
            String impactoTotal,
            LocalDateTime geradoEm
    ) {}

    public record InsightsResponse(
            boolean sucesso,
            String resposta,
            LocalDateTime timestamp
    ) {}

    public record InsightHistoryResponse(
            Long id,
            Long empresaId,
            String tipo,
            String pergunta,
            String resposta,
            LocalDateTime dataCriacao
    ) {}
}

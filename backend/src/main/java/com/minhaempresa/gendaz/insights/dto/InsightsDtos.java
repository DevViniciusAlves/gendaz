package com.minhaempresa.gendaz.insights.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;

public final class InsightsDtos {
    private InsightsDtos() {}

    public record ChatMessageRequest(
            String role,
            String content
    ) {}

    public record InsightsRequest(@NotBlank String pergunta, List<ChatMessageRequest> historico) {}

    public record InsightItem(
            String titulo,
            String descrição,
            String impacto,
            String urgencia,
            String tipo
    ) {}

    public record InsightOpportunity(
            String titulo,
            String descrição,
            String motivo,
            String impactoEstimado,
            String prioridade
    ) {}

    public record InsightRecommendedAction(
            String titulo,
            String descrição,
            String motivo,
            String impactoEstimado,
            String prioridade,
            String status
    ) {}

    public record InsightAction(
            String descrição,
            String urgencia,
            String impactoEstimado
    ) {}

    public record DashboardResponse(
            Long empresaId,
            String empresaNome,
            Integer scoreGeral,
            List<InsightItem> principais,
            List<InsightItem> alertas,
            List<InsightItem> oportunidades,
            List<InsightAction> ações,
            String impactoTotal,
            LocalDateTime geradoEm
    ) {}

    public record InsightAnalysisResponse(
            Long empresaId,
            String empresaNome,
            Integer scoreGeral,
            List<InsightItem> principais,
            List<InsightOpportunity> oportunidades,
            List<InsightRecommendedAction> ações,
            String impactoTotal,
            LocalDateTime geradoEm,
            LocalDateTime validoAte,
            String origem
    ) {}

    public record InsightsResponse(
            boolean sucesso,
            String resposta,
            LocalDateTime timestamp
    ) {}

    public record MeuGendazIAResponse(
            String resposta,
            List<String> sugestoes,
            String ação,
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


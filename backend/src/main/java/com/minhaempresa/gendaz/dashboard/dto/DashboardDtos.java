package com.minhaempresa.gendaz.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public final class DashboardDtos {
    private DashboardDtos() {}

    public record PrimeirosPassosResponse(
            int concluidos,
            int total,
            List<PrimeiroPassoItem> etapas
    ) {}

    public record PrimeiroPassoItem(
            String chave,
            String titulo,
            String subtitulo,
            String rota,
            boolean concluido
    ) {}

    public record DashboardResumoResponse(
            Long agendamentosHoje,
            Long pendentesPagamento,
            Long conversasAbertas,
            Long clientesCadastrados,
            Long servicosAtivos,
            BigDecimal receitaConfirmada,
            BigDecimal pendenteCobranca,
            List<DashboardAgendamentoItem> proximosAgendamentos,
            List<DashboardAgendamentoItem> ultimosAgendamentos,
            List<DashboardItemResumo> servicosMaisAgendados,
            List<DashboardReceitaDiaItem> receitaPorDia,
            List<DashboardPagamentoItem> pagamentosPendentes,
            String empresaNome,
            PrimeirosPassosResponse primeirosPassos
    ) {}

    public record DashboardAgendamentoItem(
            Long id,
            String data,
            String horaInicio,
            String horaFim,
            String clienteNome,
            String servicoNome,
            String profissionalNome,
            String status
    ) {}

    public record DashboardItemResumo(
            String nome,
            Long quantidade,
            BigDecimal valor
    ) {}

    public record DashboardReceitaDiaItem(
            String data,
            String label,
            BigDecimal valor
    ) {}

    public record DashboardPagamentoItem(
            Long id,
            String clienteNome,
            String metodoPagamento,
            BigDecimal valor,
            String status
    ) {}
}


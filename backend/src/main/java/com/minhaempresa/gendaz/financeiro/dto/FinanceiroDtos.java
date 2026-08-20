package com.minhaempresa.gendaz.financeiro.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class FinanceiroDtos {
    private FinanceiroDtos() {}

    public record ItemResumoResponse(String nome, Long quantidade, BigDecimal valor) {}

    public record ResumoFinanceiroResponse(
            BigDecimal totalRecebidoMes,
            BigDecimal totalPendente,
            Long consultasRealizadas,
            List<PagamentoRecenteItem> pagamentosRecentes,
            List<ItemResumoResponse> clientesComMaisConsultas,
            List<ItemResumoResponse> servicosMaisVendidos
    ) {}

    public record PagamentoRecenteItem(
            Long id,
            String clienteNome,
            com.minhaempresa.gendaz.shared.enums.StatusCadastro statusCliente,
            BigDecimal valor,
            String metodo,
            String status,
            LocalDateTime dataPagamento
    ) {}
}


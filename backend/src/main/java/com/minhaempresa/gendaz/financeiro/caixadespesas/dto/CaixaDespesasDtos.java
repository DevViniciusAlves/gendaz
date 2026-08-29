package com.minhaempresa.gendaz.financeiro.caixadespesas.dto;

import com.minhaempresa.gendaz.financeiro.caixadespesas.enums.TipoCaixaDespesasLog;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class CaixaDespesasDtos {

    private CaixaDespesasDtos() {}

    public record AdicionarCaixaDespesasRequest(BigDecimal valor, String obs) {}

    public record CaixaDespesasTotaisResponse(BigDecimal caixaTotal, BigDecimal despesasTotal) {}

    public record HistoricoItemResponse(
            Long id,
            TipoCaixaDespesasLog tipo,
            String categoria,
            String descrição,
            BigDecimal valor,
            boolean positivo,
            String obs,
            LocalDateTime data,
            String usuarioNome
    ) {}

    public record HistoricoResponse(
            List<HistoricoItemResponse> itens,
            long total,
            int pagina,
            int totalPaginas,
            int tamanhoPagina
    ) {}
}

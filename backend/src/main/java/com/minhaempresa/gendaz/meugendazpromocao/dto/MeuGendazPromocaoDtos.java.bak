package com.minhaempresa.gendaz.meugendazpromocao.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class MeuGendazPromocaoDtos {
    private MeuGendazPromocaoDtos() {}

    public record PromocaoClienteResponse(
            Long id,
            String codigo,
            String descricao,
            String tipo,
            BigDecimal valor,
            LocalDateTime dataFim,
            Boolean aplicarTodosServicos,
            List<Map<String, Object>> servicos,
            Boolean jaUsou,
            Boolean valida
    ) {}

    public record PromocaoUsadaResponse(
            String cupomCodigo,
            String cupomDescricao,
            BigDecimal valorDesconto,
            LocalDateTime dataUso,
            Long agendamentoId
    ) {}

public record PromocaoNotificacaoResponse(
            Long promocaoId,
            String cupomCodigo,
            String cupomDescricao,
            LocalDateTime dataEnvio
    ) {}

    public record CupomAplicadoResult(
            String codigo,
            String tipo,
            BigDecimal valorPromocao,
            BigDecimal desconto,
            Long promocaoOrigemId
    ) {}
}


package com.minhaempresa.gendaz.promocao.dto;

import com.minhaempresa.gendaz.promocao.enums.TipoPromocao;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class PromocaoDtos {
    private PromocaoDtos() {}

    public record PromocaoRequest(
            @NotBlank @Size(max = 80) String codigo,
            @NotBlank @Size(max = 180) String descricao,
            @NotNull TipoPromocao tipo,
            @NotNull @Positive BigDecimal valor,
            @NotNull LocalDateTime dataInicio,
            @NotNull LocalDateTime dataFim,
            Integer quantidadeLimite,
            @NotNull Boolean aplicarTodosServicos,
            Set<Long> servicoIds
    ) {}

    public record PromocaoResponse(
            Long id,
            String codigo,
            String descricao,
            TipoPromocao tipo,
            BigDecimal valor,
            LocalDateTime dataInicio,
            LocalDateTime dataFim,
            Integer quantidadeLimite,
            Integer quantidadeUsada,
            StatusCadastro status,
            Boolean aplicarTodosServicos,
            List<ServicoResumo> servicos,
            LocalDateTime dataCriacao,
            LocalDateTime dataNotificacao,
            Long totalClientesUsaram,
            Long totalUsos,
            Long totalNotificacoes,
            Long totalNotificacoesEnviadas,
            Long totalNotificacoesErros
    ) {}

    public record ServicoResumo(Long id, String nome) {}

    public record PromocaoNotificarRequest(
            @NotBlank String tipo,
            Set<Long> clienteIds
    ) {}

    public record PromocaoUsoResponse(
            Long id,
            Long promocaoId,
            Long clienteId,
            String clienteNome,
            LocalDateTime dataUso,
            BigDecimal valorDesconto
    ) {}

    public record PromocaoResumoResponse(
            Long promocaoId,
            String codigo,
            String descricao,
            Long totalClientesUsaram,
            Long totalUsos,
            Long totalNotificacoes,
            Long totalNotificacoesEnviadas,
            Long totalNotificacoesErros,
            List<PromocaoUsoResponse> usos
    ) {}
}


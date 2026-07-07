package com.minhaempresa.agendapro.entrega.dto;

import com.minhaempresa.agendapro.entrega.enums.StatusEntrega;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class EntregaDtos {
    private EntregaDtos() {}

    public record CriarEntregaRequest(
            @NotNull Long clienteId,
            @NotNull Long empresaId,
            @Size(min = 5, max = 180)
            @NotBlank String endereco,
            @Size(max = 300)
            String observacoes,
            LocalDate dataPrevisao
    ) {}

    public record AtualizarStatusEntregaRequest(@NotNull StatusEntrega status) {}

    public record EntregaResponse(
            Long id,
            Long clienteId,
            String clienteNome,
            Long empresaId,
            String endereco,
            StatusEntrega status,
            String observacoes,
            LocalDate dataPrevisao
    ) {}
}

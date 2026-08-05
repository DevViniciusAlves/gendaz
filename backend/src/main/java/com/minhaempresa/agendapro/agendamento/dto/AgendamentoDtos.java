package com.minhaempresa.agendapro.agendamento.dto;

import com.minhaempresa.agendapro.agendamento.enums.StatusAgendamento;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class AgendamentoDtos {
    private AgendamentoDtos() {}

    public record CriarAgendamentoRequest(
            @NotNull Long clienteId,
            @NotNull Long servicoId,
            Long profissionalId,
            @NotNull Long empresaId,
            @NotNull LocalDate data,
            @NotNull LocalTime horaInicio,
            String cupomCodigo,
            @Size(max = 300)
            String observacoes
    ) {}

    public record RemarcarAgendamentoRequest(@NotNull LocalDate data, @NotNull LocalTime horaInicio) {}

    public record FinalizarAgendamentoRequest(Boolean pagamentoRealizado) {}

    public record AtualizarAgendamentoRequest(
            @NotNull Long clienteId,
            @NotNull Long servicoId,
            @NotNull Long profissionalId,
            @NotNull Long empresaId,
            @NotNull LocalDate data,
            @NotNull LocalTime horaInicio,
            @NotNull StatusAgendamento status,
            @Size(max = 300)
            String observacoes
    ) {}

    public record AgendamentoResponse(
            Long id,
            String protocolo,
            Long clienteId,
            String clienteNome,
            Long servicoId,
            String servicoNome,
            Long profissionalId,
            String profissionalNome,
            Long empresaId,
            BigDecimal valor,
            LocalDate data,
            LocalTime horaInicio,
            LocalTime horaFim,
            StatusAgendamento status,
            String observacoes
    ) {}

    public record AcaoEmMassaAgendamentoRequest(
            @NotNull @Size(max = 10) List<Long> ids,
            @NotNull String acao,
            Long empresaId
    ) {}

    public record AcaoEmMassaResponse(
            int totalSolicitado,
            int totalProcessado,
            List<FalhaAcaoItem> falhas
    ) {}

    public record FalhaAcaoItem(
            Long id,
            String motivo
    ) {}
}

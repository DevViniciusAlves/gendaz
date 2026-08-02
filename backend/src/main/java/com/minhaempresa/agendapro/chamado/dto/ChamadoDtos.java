package com.minhaempresa.agendapro.chamado.dto;

import com.minhaempresa.agendapro.chamado.enums.StatusChamado;
import com.minhaempresa.agendapro.chamado.enums.PrioridadeChamado;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class ChamadoDtos {
    private ChamadoDtos() {}

    public record CriarChamadoRequest(
            @NotBlank @Size(max = 100) String assunto,
            @NotNull PrioridadeChamado prioridade,
            @NotBlank @Size(max = 500) String mensagem
    ) {}

    public record AtualizarChamadoRequest(
            @NotNull StatusChamado status,
            @Size(max = 1200) String resposta
    ) {}

    public record ChamadoResponse(
            Long id,
            String assunto,
            String mensagem,
            PrioridadeChamado prioridade,
            String origem,
            Long empresaId,
            String empresa,
            Long usuarioId,
            String usuario,
            StatusChamado status,
            LocalDateTime dataCriacao,
            LocalDateTime dataAtualizacao,
            String resposta
    ) {}
}

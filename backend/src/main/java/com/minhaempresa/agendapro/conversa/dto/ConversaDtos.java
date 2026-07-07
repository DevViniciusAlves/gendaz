package com.minhaempresa.agendapro.conversa.dto;

import com.minhaempresa.agendapro.conversa.enums.StatusConversa;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public final class ConversaDtos {
    private ConversaDtos() {}

    public record CriarConversaRequest(@NotNull Long clienteId, @NotNull Long empresaId) {}

    public record ConversaResponse(
            Long id,
            Long clienteId,
            String clienteNome,
            String clienteTelefone,
            Long empresaId,
            StatusConversa status,
            String ultimaMensagem,
            LocalDateTime dataUltimaMensagem
    ) {}
}

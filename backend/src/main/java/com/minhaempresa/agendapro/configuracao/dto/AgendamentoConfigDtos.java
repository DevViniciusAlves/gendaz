package com.minhaempresa.agendapro.configuracao.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AgendamentoConfigDtos {
    private AgendamentoConfigDtos() {}

    public record AgendamentoLinkResponse(
            Long empresaId,
            String slug,
            String publicUrl
    ) {}

    public record AtualizarAgendamentoSlugRequest(
            @Size(min = 3, max = 80)
            @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug deve conter apenas letras minusculas, numeros e hifen.")
            String slug
    ) {}
}

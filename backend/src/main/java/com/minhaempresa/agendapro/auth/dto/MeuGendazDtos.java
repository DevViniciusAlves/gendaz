package com.minhaempresa.agendapro.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class MeuGendazDtos {
    private MeuGendazDtos() {}

    public record CriarSuporteRequest(
            @NotBlank @Size(max = 120) String tipoOcorrencia,
            @NotBlank @Size(max = 160) String motivo,
            @NotBlank @Size(max = 1200) String mensagem
    ) {}
}

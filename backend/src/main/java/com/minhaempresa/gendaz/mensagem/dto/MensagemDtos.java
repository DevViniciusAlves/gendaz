package com.minhaempresa.gendaz.mensagem.dto;

import com.minhaempresa.gendaz.mensagem.enums.DirecaoMensagem;
import com.minhaempresa.gendaz.mensagem.enums.TipoMensagem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class MensagemDtos {
    private MensagemDtos() {}

    public record EnviarMensagemRequest(
            @NotNull Long conversaId,
            @Size(min = 1, max = 500)
            @NotBlank String conteudo
    ) {}

    public record EnviarHorariosRequest(
            @NotNull Long conversaId,
            @NotNull Long empresaId,
            Long profissionalId,
            @NotNull Long servicoId,
            @NotNull LocalDate data
    ) {}

    public record MensagemResponse(
            Long id,
            Long conversaId,
            String conteudo,
            DirecaoMensagem direcao,
            TipoMensagem tipo,
            LocalDateTime dataEnvio
    ) {}
}


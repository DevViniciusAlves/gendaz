package com.minhaempresa.agendapro.profissional.dto;

import com.minhaempresa.agendapro.shared.enums.StatusCadastro;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ProfissionalDtos {
    private ProfissionalDtos() {}

    public record SalvarProfissionalRequest(
            @NotBlank @Size(min = 2, max = 80) @Pattern(regexp = "^[\\p{L}\\s]+$", message = "Nome deve conter apenas letras.") String nome,
            @Size(max = 80) @Pattern(regexp = "^$|^[\\p{L}\\s]+$", message = "Especialidade deve conter apenas letras.") String especialidade,
            @Pattern(regexp = "^$|^\\d{10,15}$", message = "Telefone deve conter entre 10 e 15 digitos.") String telefone,
            @NotNull Long empresaId
    ) {}

    public record ProfissionalResponse(
            Long id,
            String nome,
            String especialidade,
            String telefone,
            StatusCadastro status,
            Long empresaId,
            boolean sistema
    ) {}
}

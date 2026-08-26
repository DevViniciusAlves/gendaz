package com.minhaempresa.gendaz.profissional.dto;

import com.minhaempresa.gendaz.profissional.enums.DiaSemana;
import com.minhaempresa.gendaz.shared.TelefoneInternacional;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public final class ProfissionalDtos {
    private ProfissionalDtos() {}

    public record SalvarProfissionalRequest(
            @NotBlank @Size(min = 2, max = 80) @Pattern(regexp = "^[\\p{L}\\s]+$", message = "Nome deve conter apenas letras.") String nome,
            @Size(max = 80) @Pattern(regexp = "^$|^[\\p{L}\\s]+$", message = "Especialidade deve conter apenas letras.") String especialidade,
            @Size(max = 20) @TelefoneInternacional String telefone,
            @NotNull Long empresaId,
            Set<DiaSemana> diasTrabalho
    ) {}

    public record ProfissionalResponse(
            Long id,
            String nome,
            String especialidade,
            String telefone,
            StatusCadastro status,
            Long empresaId,
            boolean sistema,
            Set<DiaSemana> diasTrabalho
    ) {}
}


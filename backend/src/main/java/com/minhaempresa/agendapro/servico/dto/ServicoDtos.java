package com.minhaempresa.agendapro.servico.dto;

import com.minhaempresa.agendapro.shared.enums.StatusCadastro;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public final class ServicoDtos {
    private ServicoDtos() {}

    public record SalvarServicoRequest(
            @NotBlank @Size(min = 2, max = 80) @Pattern(regexp = "^[\\p{L}\\s]+$", message = "Nome deve conter apenas letras.") String nome,
            @Size(max = 300) String descricao,
            @Min(5) @Max(720) Integer duracaoMinutos,
            @DecimalMin(value = "0.00", message = "Valor deve ser maior ou igual a zero.") @DecimalMax(value = "999999.99", message = "Valor deve ser menor ou igual a 999999.99.") BigDecimal valor,
            @NotNull Long empresaId
    ) {}

    public record ServicoResponse(
            Long id,
            String nome,
            String descricao,
            Integer duracaoMinutos,
            BigDecimal valor,
            StatusCadastro status,
            Long empresaId
    ) {}
}

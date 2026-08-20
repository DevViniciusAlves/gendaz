package com.minhaempresa.gendaz.cliente.dto;

import com.minhaempresa.gendaz.shared.TelefoneInternacional;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.time.LocalDateTime;

public final class ClienteDtos {
    private ClienteDtos() {}

    public record SalvarClienteRequest(
            @NotBlank @Size(min = 2, max = 80) @Pattern(regexp = "^[\\p{L}\\s]+$", message = "Nome deve conter apenas letras.") String nome,
            @NotBlank @TelefoneInternacional String telefone,
            @NotBlank @Email @Size(max = 120) String email,
            @Size(max = 300) String observacoes,
            @NotNull Long empresaId
    ) {}

    public record ClienteResponse(
            Long id,
            String nome,
            String telefone,
            String email,
            String observacoes,
            StatusCadastro statusCliente,
            Long empresaId,
            LocalDateTime dataCriacao,
            LocalDateTime dataAtualizacao
    ) {}

    public record AcaoEmMassaClienteRequest(
            @NotNull @Size(max = 10) List<Long> ids,
            @NotBlank String acao,
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


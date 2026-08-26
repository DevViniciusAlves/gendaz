package com.minhaempresa.gendaz.empresa.dto;

import com.minhaempresa.gendaz.empresa.enums.RamoEmpresa;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.shared.TelefoneInternacional;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import java.time.LocalDateTime;

public final class EmpresaDtos {
    private EmpresaDtos() {}

    public record CriarEmpresaRequest(
            @NotBlank @Size(min = 2, max = 100) String nomeFantasia,
            @Size(max = 20) @TelefoneInternacional String telefone,
            @Email @NotBlank @Size(max = 120) String email
    ) {}

    public record AtualizarEmpresaRequest(
            @NotBlank @Size(min = 2, max = 100) String nomeFantasia,
            @Size(max = 20) @TelefoneInternacional String telefone,
            @Email @NotBlank @Size(max = 120) String email,
            @Size(max = 60) String timezone,
            StatusEmpresa status
    ) {}

    public record EmpresaResponse(
            Long id,
            String nomeFantasia,
            String telefone,
            String email,
            StatusEmpresa status,
            String timezone,
            RamoEmpresa ramo,
            String ramoDisplayName,
            Integer diasRegular,
            Integer diasAltoRisco,
            LocalDateTime ramoAtualizadoEm,
            LocalDateTime dataCriacao,
            LocalDateTime dataAtualizacao
    ) {}
}


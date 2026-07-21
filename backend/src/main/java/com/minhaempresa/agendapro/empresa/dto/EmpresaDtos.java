package com.minhaempresa.agendapro.empresa.dto;

import com.minhaempresa.agendapro.empresa.enums.StatusEmpresa;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import java.time.LocalDateTime;

public final class EmpresaDtos {
    private EmpresaDtos() {}

    public record CriarEmpresaRequest(
            @NotBlank @Size(min = 2, max = 100) String nomeFantasia,
            String documento,
            @Pattern(regexp = "^$|^[0-9()+\\-\\s]{10,20}$", message = "Informe um telefone valido.") String telefone,
            @Email @NotBlank @Size(max = 120) String email
    ) {}

    public record AtualizarEmpresaRequest(
            @NotBlank @Size(min = 2, max = 100) String nomeFantasia,
            @Pattern(regexp = "^$|^[0-9]{11,14}$", message = "Informe um documento valido.") String documento,
            @Pattern(regexp = "^$|^[0-9()+\\-\\s]{10,20}$", message = "Informe um telefone valido.") String telefone,
            @Email @NotBlank @Size(max = 120) String email,
            @Size(max = 60) String timezone,
            StatusEmpresa status
    ) {}

    public record EmpresaResponse(
            Long id,
            String nomeFantasia,
            String documento,
            String telefone,
            String email,
            StatusEmpresa status,
            //  DESATIVADO - WhatsApp
            // Boolean whatsappConnected,
            // String whatsappPhone,
            // Boolean whatsappNotificationsEnabled,
            // Boolean whatsappSecretariaIaEnabled,
            String timezone,
            LocalDateTime dataCriacao,
            LocalDateTime dataAtualizacao
    ) {}
}

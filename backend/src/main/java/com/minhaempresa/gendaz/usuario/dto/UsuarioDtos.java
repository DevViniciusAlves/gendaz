package com.minhaempresa.gendaz.usuario.dto;

import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class UsuarioDtos {
    private UsuarioDtos() {}

    public record CriarUsuarioRequest(
            @NotBlank @Size(min = 2, max = 80) @Pattern(regexp = "^[\\p{L}\\s]+$", message = "Nome deve conter apenas letras.") String nome,
            @Email @NotBlank @Size(max = 120) String email,
            @NotBlank @Size(min = 8, max = 72) String senha,
            @NotNull PerfilUsuario perfil,
            Long empresaId
    ) {}

    public record AtualizarUsuarioRequest(
            @NotBlank @Size(min = 2, max = 80) @Pattern(regexp = "^[\\p{L}\\s]+$", message = "Nome deve conter apenas letras.") String nome,
            @Email @NotBlank @Size(max = 120) String email,
            @NotNull PerfilUsuario perfil
    ) {}

    public record UsuarioResponse(
            Long id,
            String nome,
            String email,
            PerfilUsuario perfil,
            StatusUsuario status,
            Long empresaId,
            String empresaNome,
            Boolean owner,
            Boolean aceitouTermos,
            LocalDateTime dataAceiteTermos,
            String versaoTermos,
            LocalDateTime dataAceitePolitica,
            String versaoPolitica,
            LocalDateTime dataCriacao,
            LocalDateTime dataAtualizacao
    ) {}

    public record ConviteResponse(
            Long id,
            Long empresaId,
            String email,
            String status,
            LocalDateTime dataCriacao,
            LocalDateTime dataExpiracao,
            LocalDateTime dataAceite,
            Integer reenvios,
            Boolean owner
    ) {}
}


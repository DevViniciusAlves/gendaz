package com.minhaempresa.gendaz.usuario.dto;

import com.minhaempresa.gendaz.membresia.enums.FuncaoMembresia;
import com.minhaempresa.gendaz.membresia.enums.StatusMembresia;
import com.minhaempresa.gendaz.convite.enums.StatusConviteEmpresa;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class MembresiaDtos {
    private MembresiaDtos() {}

    public record MembroEmpresaResponse(
            Long id,
            Long usuarioId,
            String nome,
            String email,
            StatusMembresia status,
            FuncaoMembresia funcao,
            Boolean owner,
            LocalDateTime dataEntrada,
            LocalDateTime dataRemocao,
            LocalDateTime dataCriacao,
            LocalDateTime dataAtualizacao
    ) {}

    public record ConviteEmpresaResponse(
            Long id,
            Long empresaId,
            String email,
            StatusConviteEmpresa status,
            LocalDateTime dataCriacao,
            LocalDateTime dataExpiracao,
            LocalDateTime dataAceite,
            Integer reenvios,
            Boolean owner
    ) {}

    public record CriarConviteRequest(
            @NotBlank @Size(min = 2, max = 80) String nome,
            @NotBlank @Size(max = 19) String telefone,
            @Email @NotBlank @Size(max = 120) String email
    ) {}

    public record AceitarConviteRequest(
            @Email @NotBlank @Size(max = 120) String email,
            @NotBlank @Size(min = 2, max = 80) String nome,
            @NotBlank @Size(min = 8, max = 72) String senha,
            @NotBlank String token
    ) {}

    public record RecusarConviteRequest(@NotBlank String token) {}

    public record ConvitePublicoResponse(
            String nome,
            String email,
            String empresaNome,
            Boolean valido
    ) {}
}


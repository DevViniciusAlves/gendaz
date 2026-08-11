package com.minhaempresa.gendaz.auth.dto;

import com.minhaempresa.gendaz.assinatura.dto.AssinaturaDtos.AssinaturaResponse;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.PagamentoPlanoResponse;
import com.minhaempresa.gendaz.usuario.dto.UsuarioDtos.UsuarioResponse;
import com.minhaempresa.gendaz.empresa.enums.TipoDocumento;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(
            @Email @NotBlank @Size(max = 120) String email,
            @NotBlank @Size(min = 8, max = 72) String senha,
            String recaptchaToken
    ) {}

    public record MeuGendazSolicitarCodigoRequest(
            @NotBlank @Size(max = 120) String slug,
            @Email @NotBlank @Size(max = 120) String email
    ) {}

    public record MeuGendazValidarCodigoRequest(
            @NotBlank @Size(max = 120) String slug,
            @Email @NotBlank @Size(max = 120) String email,
            @NotBlank @Size(min = 6, max = 6) String codigo
    ) {}

    public record CriarContaRequest(
            @NotBlank @Size(min = 2, max = 100) @Pattern(regexp = "^[\\p{L}\\s]+$", message = "Nome da empresa deve conter apenas letras.") String nomeEmpresa,
            @NotBlank @Size(min = 2, max = 80) @Pattern(regexp = "^[\\p{L}\\s]+$", message = "Nome do proprietario deve conter apenas letras.") String nomeProprietario,
            @Email @NotBlank @Size(max = 120) String email,
            @NotBlank @Pattern(regexp = "^\\d{10,15}$", message = "Telefone deve conter entre 10 e 15 digitos.") String telefone,
            @NotNull TipoDocumento documentoTipo,
            @NotBlank @Size(min = 11, max = 14) String documentoNumero,
            @NotBlank @Size(min = 8, max = 72) String senha,
            @NotBlank @Size(min = 8, max = 72) String confirmarSenha,
            @NotBlank String plano,
            @NotNull @AssertTrue Boolean aceiteTermos
    ) {}

    public record LoginResponse(
            String mensagem,
            UsuarioResponse usuario,
            AssinaturaResponse assinatura,
            PagamentoPlanoResponse pagamentoPlano,
            String statusConta,
            String sessionToken,
            String motivoInatividade
    ) {
        public LoginResponse(String mensagem, UsuarioResponse usuario, AssinaturaResponse assinatura) {
            this(mensagem, usuario, assinatura, null, "ACTIVE", null, null);
        }

        public LoginResponse(String mensagem, UsuarioResponse usuario, AssinaturaResponse assinatura, PagamentoPlanoResponse pagamentoPlano) {
            this(mensagem, usuario, assinatura, pagamentoPlano, "ACTIVE", null, null);
        }

        public LoginResponse(String mensagem, UsuarioResponse usuario, AssinaturaResponse assinatura, PagamentoPlanoResponse pagamentoPlano, String statusConta) {
            this(mensagem, usuario, assinatura, pagamentoPlano, statusConta, null, null);
        }

        public LoginResponse(String mensagem, UsuarioResponse usuario, AssinaturaResponse assinatura, PagamentoPlanoResponse pagamentoPlano, String statusConta, String sessionToken) {
            this(mensagem, usuario, assinatura, pagamentoPlano, statusConta, sessionToken, null);
        }
    }

    public record RefreshResponse(
            String mensagem,
            UsuarioResponse usuario,
            AssinaturaResponse assinatura,
            PagamentoPlanoResponse pagamentoPlano,
            String statusConta,
            String sessionToken,
            String motivoInatividade
    ) {
        public RefreshResponse(String mensagem, UsuarioResponse usuario, AssinaturaResponse assinatura, PagamentoPlanoResponse pagamentoPlano, String statusConta) {
            this(mensagem, usuario, assinatura, pagamentoPlano, statusConta, null, null);
        }

        public RefreshResponse(String mensagem, UsuarioResponse usuario, AssinaturaResponse assinatura, PagamentoPlanoResponse pagamentoPlano, String statusConta, String sessionToken) {
            this(mensagem, usuario, assinatura, pagamentoPlano, statusConta, sessionToken, null);
        }
    }

    public record SolicitarRecuperacaoSenhaRequest(@Email @NotBlank @Size(max = 120) String email) {}

    public record RedefinirSenhaRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 72) String novaSenha,
            @NotBlank @Size(min = 8, max = 72) String confirmarNovaSenha
    ) {}

    public record TrocarSenhaRequest(
            @NotBlank String senhaAtual,
            @NotBlank @Size(min = 8, max = 72) String novaSenha,
            @NotBlank @Size(min = 8, max = 72) String confirmarNovaSenha
    ) {}

    public record RecuperacaoSenhaResponse(String mensagem) {}

    public record TrocarSenhaResponse(String mensagem) {}

    public record MeuGendazAuthResponse(
            String mensagem,
            String email,
            String sessionToken,
            String status
    ) {}

    public record MeuGendazCodigoResponse(
            String mensagem,
            String email,
            boolean reenviarDisponivel
    ) {}
}


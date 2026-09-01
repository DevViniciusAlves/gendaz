package com.minhaempresa.gendaz.admin.dto;

import com.minhaempresa.gendaz.admin.dto.AdminAssinaturaDtos.AdminAssinaturaOperacaoRequest;
import com.minhaempresa.gendaz.shared.TelefoneInternacional;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class AdminDtos {
    private AdminDtos() {}

    public record AdminLoginRequest(@Email @NotBlank String email, @NotBlank String senha) {}

    public record AdminLoginResponse(String token, AdminUsuarioResponse admin) {}

    public record AdminUsuarioResponse(Long id, String nome, String email, String perfil) {}

    public record AdminDashboardResponse(
            BigDecimal faturamentoTotal,
            BigDecimal faturamentoMes,
            long pagamentosConfirmados,
            long pagamentosPendentes,
            long assinaturasAtivas,
            long empresasTesteGratis,
            long empresasVencidas,
            long usuariosAtivos,
            long novosCadastros,
            List<ReceitaPontoResponse> receita,
            List<PlanoDistribuicaoResponse> distribuicaoPlanos
    ) {}

    public record ReceitaPontoResponse(String periodo, BigDecimal valor) {}

    public record PlanoDistribuicaoResponse(String plano, long total) {}

    public record AdminEmpresaUsuarioResponse(
            Long empresaId,
            Long usuarioId,
            String empresa,
            String responsavel,
            String email,
            String emailEmpresa,
            String telefone,
            String plano,
            String statusEmpresa,
            String statusAssinatura,
            LocalDateTime dataCriacao,
            LocalDateTime ultimoPagamento,
            BigDecimal valorUltimoPagamento
    ) {}

    public record AdminPagamentoResponse(
            Long id,
            String empresa,
            String responsavel,
            String email,
            String telefone,
            String plano,
            BigDecimal valor,
            String gateway,
            String status,
            String statusEmpresa,
            LocalDateTime dataCriacao,
            LocalDateTime vencimento,
            LocalDateTime dataPagamento,
            String externalPaymentId,
            String detalhes,
            String paymentReference,
            String checkoutUrl
    ) {}

    public record AprovarPagamentoManualRequest(
            @Size(max = 500, message = "O motivo deve ter no maximo 500 caracteres.") String motivo,
            @Size(max = 120, message = "O ID da transacao deve ter no maximo 120 caracteres.") String transacaoId
    ) {}

    public record DesaprovarPagamentoManualRequest(
            @NotBlank @Size(min = 8, max = 500, message = "Informe um motivo com pelo menos 8 caracteres.") String motivo,
            @Size(max = 120, message = "O ID da transacao deve ter no maximo 120 caracteres.") String transacaoId
    ) {}

    public record AdminAcaoEmpresaRequest(
            @NotBlank @Size(min = 8, max = 500, message = "Informe um motivo com pelo menos 8 caracteres.") String motivo
    ) {}

public record AdminAtualizarEmpresaRequest(
            @NotBlank @Size(min = 2, max = 100, message = "Nome fantasia deve ter entre 2 e 100 caracteres.") String nomeFantasia,
            @Size(max = 20, message = "Telefone deve ter no maximo 20 caracteres.") @TelefoneInternacional String telefone,
            @NotBlank @Email @Size(max = 120, message = "E-mail deve ter no maximo 120 caracteres.") String email,
            Long planoId,
            Integer diasPlano,
            @NotBlank @Size(min = 8, max = 500, message = "O motivo deve ter entre 8 e 500 caracteres.") String motivo,
            List<AdminAssinaturaOperacaoRequest> assinaturas
    ) {}

    public record AdminAuditLogResponse(
            Long id,
            String tipo,
            String severidade,
            String admin,
            String usuario,
            String empresa,
            String descricao,
            String motivo,
            String ip,
            String userAgent,
            LocalDateTime dataCriacao
    ) {}

    public record AdminConfigResponse(
            String paymentProvider,
            String frontendUrl,
            String apiUrl,
            String statusSistema,
            String backendVersion,
            String secrets
    ) {}

    public record AdminChamadoResponse(
            Long id,
            String assunto,
            String mensagem,
            String empresa,
            String usuario,
            String status,
            String resposta,
            LocalDateTime dataCriacao,
            LocalDateTime dataAtualizacao
    ) {}

    public record ImpersonarRequest(
            @Size(max = 500, message = "O motivo deve ter no maximo 500 caracteres.") String motivo
    ) {}

    public record ImpersonarResponse(
            Long sessionId,
            Long empresaId,
            Long usuarioId,
            String plano,
            String usuarioNome,
            String usuarioEmail,
            String empresa,
            String motivo,
            LocalDateTime dataInicio
    ) {}

    public record AdminFiltroRequest(String tipo, String empresa, String usuario, LocalDate dataInicio, LocalDate dataFim, String severidade, String status, String plano) {}
}


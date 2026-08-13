package com.minhaempresa.gendaz.pagamento.dto;

import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class PagamentoDtos {
    private PagamentoDtos() {}

    public record CriarPagamentoRequest(
            Long agendamentoId,
            @NotNull Long clienteId,
            @NotNull Long empresaId,
            @NotNull @Positive @DecimalMax(value = "999999.99", message = "Valor deve ser menor ou igual a 999999.99.") BigDecimal valor,
            @NotNull MetodoPagamento metodoPagamento
    ) {}

    public record AtualizarStatusPagamentoRequest(@NotNull StatusPagamento status) {}

    public record MarcarPagamentoPagoRequest(
            @NotNull MetodoPagamento metodoPagamento,
            Integer parcelas
    ) {}

    public record FormasPagamentoEmpresaResponse(
            Long empresaId,
            boolean pixAtivo,
            boolean debitoAtivo,
            boolean creditoAtivo,
            boolean parceladoAtivo,
            boolean dinheiroAtivo,
            int maxParcelas
    ) {}

    public record AtualizarFormasPagamentoEmpresaRequest(
            boolean pixAtivo,
            boolean debitoAtivo,
            boolean creditoAtivo,
            boolean parceladoAtivo,
            boolean dinheiroAtivo
    ) {}

    public record IniciarPagamentoPlanoRequest(
            @NotNull Long empresaId,
            @NotNull MetodoPagamento metodoPagamento,
            @Size(max = 20) String plano,
            @Size(max = 120) String customerName,
            @Size(max = 120) String customerEmail,
            @Size(max = 20) String customerPhone,
            @Size(max = 20) String customerDocType,
            @Size(max = 20) String customerDocNumber,
            @Size(max = 120) String antifraudProfilingAttemptReference
    ) {}

    public record WebhookPagamentoPlanoRequest(
            @NotNull @Size(max = 120) String eventId,
            @NotNull @Size(max = 120) String providerPaymentId,
            @NotNull StatusPagamento status,
            @NotNull @Positive @DecimalMax(value = "999999.99", message = "Valor deve ser menor ou igual a 999999.99.") BigDecimal valor
    ) {}

    public record PagamentoPlanoResponse(
            Long id,
            Long empresaId,
            String empresaNome,
            Long planoId,
            String planoNome,
            BigDecimal valor,
            MetodoPagamento metodoPagamento,
            StatusPagamento status,
            String provider,
            String providerPaymentId,
            String externalReference,
            String paymentReference,
            String customerName,
            String customerEmail,
            String customerPhone,
            String customerDocType,
            String customerDocNumber,
            String antifraudProfilingAttemptReference,
            String checkoutUrl,
            String subscriptionId,
            String stripeSessionId,
            String stripeCustomerId,
            LocalDateTime dataCriacao,
            LocalDateTime dataExpiracao,
            LocalDateTime dataPagamento
    ) {}

    public record VerificarPagamentoPlanoResponse(
            String statusVerificacao,
            String mensagem,
            StatusEmpresa statusEmpresa,
            StatusAssinatura statusAssinatura,
            PagamentoPlanoResponse pagamento
    ) {}

    public record PagamentoResponse(
            Long id,
            Long agendamentoId,
            String protocolo,
            String servicoNome,
            Long clienteId,
            String clienteNome,
            Long empresaId,
            BigDecimal valor,
            MetodoPagamento metodoPagamento,
            Integer parcelas,
            StatusPagamento status,
            LocalDateTime dataPagamento
    ) {}

    public record AcaoEmMassaPagamentoRequest(
            @NotNull @Size(max = 10) List<Long> ids,
            @NotNull String acao,
            Long empresaId,
            MetodoPagamento metodoPagamento,
            Integer parcelas
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


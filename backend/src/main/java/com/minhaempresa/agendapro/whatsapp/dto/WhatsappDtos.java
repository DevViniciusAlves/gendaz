/*
  ╔══════════════════════════════════════════════╗
  ║  ⚠️  DESATIVADO - FUNCIONALIDADE WhatsApp    ║
  ║  Todo código comentado. Remova comentários   ║
  ║  para reativar.                              ║
  ╚══════════════════════════════════════════════╝
*/
package com.minhaempresa.agendapro.whatsapp.dto;

import com.minhaempresa.agendapro.whatsapp.enums.WhatsappConnectionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.List;

public final class WhatsappDtos {
    private WhatsappDtos() {}

    public record WhatsappStatusResponse(
            Long connectionId,
            String provider,
            WhatsappConnectionStatus status,
            String statusLabel,
            String displayPhoneNumber,
            String phoneNumberId,
            String lastError,
            LocalDateTime connectedAt,
            LocalDateTime disconnectedAt,
            boolean metaConfigured,
            String message,
            String pairingCode,
            java.time.LocalDateTime expiresAt,
            boolean whatsappConnected,
            String whatsappPhone,
            boolean notificationsEnabled,
            boolean secretariaIaEnabled
    ) {}

    public record StartConnectionResponse(
            WhatsappConnectionStatus status,
            String statusLabel,
            String message,
            String appId,
            String configId,
            String graphApiVersion,
            String frontendUrl,
            String pairingCode
    ) {}

    public record CompleteConnectionRequest(
            @NotBlank @Size(max = 100) String wabaId,
            @NotBlank @Size(max = 100) String phoneNumberId,
            @NotBlank @Size(max = 30) String displayPhoneNumber
    ) {}

    public record SendTestMessageRequest(
            @NotBlank @Pattern(regexp = "\\d{10,15}") String to,
            @NotBlank @Size(max = 500) String message
    ) {}

    public record SendTestMessageResponse(
            String status,
            String message,
            String providerMessageId
    ) {}

    public record WhatsappConnectRequest(
            @NotNull Long empresaId,
            @NotBlank @Pattern(regexp = "\\d{10,15}") String phoneNumber
    ) {}

    public record WhatsappConnectResponse(
            String status,
            String statusLabel,
            String message,
            String pairingCode,
            java.time.LocalDateTime expiresAt,
            Long empresaId,
            String phoneNumber
    ) {}

    public record WhatsappStatusUpdateRequest(
            @NotNull Long empresaId,
            @NotBlank String status,
            String phoneNumber,
            String pairingCode
    ) {}

    public record WhatsappPreferenciasRequest(
            @NotNull Long empresaId,
            boolean notificacoesAutomaticas,
            boolean secretariaIaAtiva,
            String descricaoEmpresa,
            String mensagemBoasVindas,
            String respostaHorarios,
            String respostaServicos,
            String respostaNaoEntende,
            String mensagemHumano
    ) {}

    public record WhatsappConfigResponse(
            Long empresaId,
            String nomeEmpresa,
            String descricaoEmpresa,
            String agendamentoSlug,
            boolean assistenteAtivo,
            boolean notificacoesHabilitadas,
            boolean ativo,
            String numeroConectado,
            String mensagemBoasVindas,
            String respostaHorarios,
            String respostaServicos,
            String respostaNaoEntende,
            String mensagemHumano,
            String linkAgendamento,
            List<ServicoContextResponse> servicos,
            List<ProfissionalContextResponse> profissionais,
            List<HorarioDisponivelResponse> horariosDisponiveis
    ) {}

    public record WhatsappSessionSaveRequest(
            @NotBlank String credsJson,
            @NotBlank String keysJson,
            boolean registered,
            String phoneNumber,
            String meId,
            String meLid,
            String lastStatus,
            String lastError
    ) {}

    public record WhatsappSessionResponse(
            Long empresaId,
            String credsJson,
            String keysJson,
            boolean registered,
            String phoneNumber,
            String meId,
            String meLid,
            String lastStatus,
            String lastError,
            LocalDateTime updatedAt
    ) {}

    public record WhatsappSessionSummaryResponse(
            Long empresaId,
            boolean registered,
            String phoneNumber,
            String meId,
            String meLid,
            LocalDateTime updatedAt
    ) {}

    public record ServicoContextResponse(
            Long id,
            String nome,
            BigDecimal valor,
            Integer duracaoMinutos,
            String status
    ) {}

    public record ProfissionalContextResponse(
            Long id,
            String nome,
            String especialidade
    ) {}

    public record HorarioDisponivelResponse(
            LocalDate data,
            List<String> horarios
    ) {}

    public record ConectarWhatsappRequest(
            @NotBlank @Pattern(regexp = "\\d{10,15}") String phone
    ) {}

    public record WhatsappAgendamentoIaRequest(
            @NotNull Long empresaId,
            @NotBlank String clientePhone,
            @NotBlank @Size(max = 1000) String texto
    ) {}

    public record WhatsappServicoResposta(
            Long id,
            String nome,
            BigDecimal valor,
            Integer duracaoMinutos
    ) {}

    public record WhatsappDisponibilidadeResponse(
            LocalDate data,
            boolean disponivel,
            List<String> horarios
    ) {}

    public record WhatsappAgendarRequest(
            @NotNull Long empresaId,
            @NotBlank String telefoneCliente,
            @NotBlank String nomeCliente,
            @NotNull Long servicoId,
            Long profissionalId,
            @NotNull LocalDate data,
            @NotBlank String horario,
            String remoteJid,
            String origem
    ) {}

    public record WhatsappAgendarResumoResponse(
            String servico,
            LocalDate data,
            String horario
    ) {}

    public record WhatsappAgendarResponse(
            boolean sucesso,
            Long agendamentoId,
            String protocolo,
            String mensagem,
            WhatsappAgendarResumoResponse resumo
    ) {}

    public record WhatsappFluxoConversaRequest(
            @NotNull Long empresaId,
            @NotBlank String telefoneCliente,
            String remoteJid,
            @NotBlank String tipoFluxo,
            @NotBlank String etapa,
            boolean ativo,
            String modoSelecionado,
            Map<String, Object> payload,
            LocalDateTime expiraEm
    ) {}

    public record WhatsappFluxoConversaResponse(
            Long id,
            Long empresaId,
            String telefoneCliente,
            String remoteJid,
            String tipoFluxo,
            String etapa,
            boolean ativo,
            String modoSelecionado,
            Map<String, Object> payload,
            LocalDateTime criadoEm,
            LocalDateTime atualizadoEm,
            LocalDateTime expiraEm
    ) {}

    public record WhatsappContextResponse(
            EmpresaContextResponse empresa,
            ClienteContextResponse cliente
    ) {}

    public record EmpresaContextResponse(
            String nome,
            List<String> servicos,
            List<String> horarios,
            boolean whatsappConnected,
            String whatsappPhone,
            boolean notificacoesAutomaticas,
            boolean secretariaIaAtiva
    ) {}

    public record ClienteContextResponse(
            String nome,
            List<AgendamentoContextResponse> ultimosAgendamentos
    ) {}

    public record AgendamentoContextResponse(
            Long id,
            LocalDate data,
            LocalTime horaInicio,
            LocalTime horaFim,
            String servico,
            String profissional,
            String status
    ) {}
}

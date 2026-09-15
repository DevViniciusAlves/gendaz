package com.minhaempresa.gendaz.whatsapp.controller;

import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.whatsapp.WhatsAppOperationStatus;
import com.minhaempresa.gendaz.whatsapp.WhatsAppProvider;
import com.minhaempresa.gendaz.whatsapp.WhatsAppQr;
import com.minhaempresa.gendaz.whatsapp.WhatsAppResult;
import com.minhaempresa.gendaz.whatsapp.WhatsAppSessionStatus;
import com.minhaempresa.gendaz.whatsapp.dto.WhatsAppIntegracaoDtos.AtualizarConfiguracaoRequest;
import com.minhaempresa.gendaz.whatsapp.dto.WhatsAppIntegracaoDtos.CategoriaUsoResponse;
import com.minhaempresa.gendaz.whatsapp.dto.WhatsAppIntegracaoDtos.ConexaoResponse;
import com.minhaempresa.gendaz.whatsapp.dto.WhatsAppIntegracaoDtos.ConfiguracaoResponse;
import com.minhaempresa.gendaz.whatsapp.dto.WhatsAppIntegracaoDtos.QrResponse;
import com.minhaempresa.gendaz.whatsapp.dto.WhatsAppIntegracaoDtos.ResumoResponse;
import com.minhaempresa.gendaz.whatsapp.dto.WhatsAppIntegracaoDtos.UsoResponse;
import com.minhaempresa.gendaz.whatsapp.policy.WhatsAppPlanoPolicy;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppConfiguracaoService;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppQuotaService;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppUsoResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API publica da integracao WhatsApp para a UI (Fase 9). Autenticada pela
 * sessao: a empresa sempre vem de {@link CompanyContext}, nunca de
 * parametro do browser (impede Empresa A operar sessao da Empresa B).
 * Nunca expoe token, URL interna, JID, auth state ou detalhes do Node.
 */
@RestController
@RequestMapping("/api/whatsapp")
@RequiredArgsConstructor
public class WhatsAppIntegracaoController {

    private final WhatsAppProvider provider;
    private final WhatsAppConfiguracaoService configuracaoService;
    private final WhatsAppQuotaService quotaService;

    @GetMapping("/resumo")
    public ResponseEntity<ResumoResponse> resumo() {
        Long empresaId = CompanyContext.requireCompanyId();
        String companyId = String.valueOf(empresaId);
        String plano = planoEfetivo(empresaId);
        boolean disponivelNoPlano = WhatsAppPlanoPolicy.possuiWhatsApp(plano);

        ConexaoResponse conexao;
        if (!provider.disponivel()) {
            conexao = new ConexaoResponse("NOT_CONFIGURED", false, null);
        } else {
            WhatsAppResult<WhatsAppSessionStatus> status = provider.consultarStatus(companyId);
            conexao = status.isSuccess()
                    ? new ConexaoResponse(
                            status.getData().getState(),
                            status.getData().isHasQr(),
                            status.getData().getConnectedAt())
                    : new ConexaoResponse("UNAVAILABLE", false, null);
        }

        WhatsAppUsoResponse uso = quotaService.consultarUso(empresaId);
        return ResponseEntity.ok(new ResumoResponse(
                disponivelNoPlano,
                conexao,
                new ConfiguracaoResponse(
                        configuracaoService.lembretesAtivos(empresaId),
                        configuracaoService.obterLembreteTemplatePersonalizado(empresaId).orElse(null),
                        WhatsAppConfiguracaoService.DEFAULT_LEMBRETE_TEMPLATE),
                new UsoResponse(
                        uso.planoNome(),
                        uso.cicloInicio(),
                        uso.cicloFim(),
                        new CategoriaUsoResponse(
                                uso.limiteLembretes(), uso.lembretesEnviados(),
                                uso.lembretesReservados(), uso.lembretesDisponiveis()),
                        new CategoriaUsoResponse(
                                uso.limiteCrm(), uso.crmEnviados(),
                                uso.crmReservados(), uso.crmDisponiveis()))));
    }

    @PostMapping("/conectar")
    public ResponseEntity<?> conectar() {
        Long empresaId = CompanyContext.requireCompanyId();
        exigirPlanoComWhatsApp(empresaId);
        WhatsAppResult<WhatsAppSessionStatus> resultado =
                provider.conectar(String.valueOf(empresaId));
        if (resultado.isSuccess()) {
            WhatsAppSessionStatus status = resultado.getData();
            return ResponseEntity.ok(Map.of(
                    "estado", status.getState(),
                    "hasQr", status.isHasQr(),
                    "connectedAt", status.getConnectedAt() == null ? "" : status.getConnectedAt()));
        }
        return erroSemantico(resultado.getStatus(), false);
    }

    @GetMapping("/qr")
    public ResponseEntity<?> qr() {
        Long empresaId = CompanyContext.requireCompanyId();
        exigirPlanoComWhatsApp(empresaId);
        WhatsAppResult<WhatsAppQr> resultado = provider.obterQr(String.valueOf(empresaId));
        if (resultado.isSuccess()) {
            return ResponseEntity.ok(new QrResponse(
                    resultado.getData().getQr(), resultado.getData().getUpdatedAt()));
        }
        if (resultado.getStatus() == WhatsAppOperationStatus.QR_UNAVAILABLE) {
            return ResponseEntity.status(404).body(Map.of(
                    "code", "WHATSAPP_QR_UNAVAILABLE",
                    "message", "QR Code ainda nao disponivel. Aguarde e tente novamente."));
        }
        return erroSemantico(resultado.getStatus(), true);
    }

    @PostMapping("/desconectar")
    public ResponseEntity<?> desconectar() {
        Long empresaId = CompanyContext.requireCompanyId();
        WhatsAppResult<WhatsAppSessionStatus> resultado =
                provider.logout(String.valueOf(empresaId));
        if (resultado.isSuccess()) {
            return ResponseEntity.ok(Map.of("estado", resultado.getData().getState()));
        }
        return erroSemantico(resultado.getStatus(), false);
    }

    @PatchMapping("/configuracao")
    public ResponseEntity<?> configuracao(@RequestBody AtualizarConfiguracaoRequest request) {
        Long empresaId = CompanyContext.requireCompanyId();
        if (request == null || (request.lembretesAtivos() == null && request.lembreteTemplate() == null)) {
            throw new BusinessException("Informe a configuracao que deseja alterar.");
        }
        // Atomico: valida todo o payload antes da primeira escrita; eventos
        // publicados apos persistencia e consumidos em AFTER_COMMIT.
        configuracaoService.atualizarConfiguracao(empresaId, request.lembretesAtivos(), request.lembreteTemplate());
        return ResponseEntity.ok(new ConfiguracaoResponse(
                configuracaoService.lembretesAtivos(empresaId),
                configuracaoService.obterLembreteTemplatePersonalizado(empresaId).orElse(null),
                WhatsAppConfiguracaoService.DEFAULT_LEMBRETE_TEMPLATE));
    }

    private ResponseEntity<Map<String, String>> erroSemantico(WhatsAppOperationStatus status, boolean eQr) {
        return switch (status) {
            case NOT_CONFIGURED -> ResponseEntity.status(503).body(Map.of(
                    "code", "WHATSAPP_NOT_CONFIGURED",
                    "message", "Integracao WhatsApp nao configurada neste ambiente."));
            case QR_UNAVAILABLE -> ResponseEntity.status(404).body(Map.of(
                    "code", "WHATSAPP_QR_UNAVAILABLE",
                    "message", "QR Code ainda nao disponivel. Aguarde e tente novamente."));
            // 401 Spring -> Node = credencial interna do servico, nunca
            // "sessao do usuario". Sem vazar token/detalhe interno.
            case UNAUTHORIZED -> ResponseEntity.status(503).body(Map.of(
                    "code", "WHATSAPP_SERVICE_AUTH_ERROR",
                    "message", "Nao foi possivel autenticar com o servico do WhatsApp."));
            case INVALID_COMPANY_ID -> ResponseEntity.status(400).body(Map.of(
                    "code", "WHATSAPP_SESSION_ERROR",
                    "message", "Sessao WhatsApp invalida. Tente conectar novamente."));
            case INTERNAL_ERROR -> ResponseEntity.status(502).body(Map.of(
                    "code", "WHATSAPP_SESSION_ERROR",
                    "message", "Falha na sessao WhatsApp. Tente novamente."));
            case UNAVAILABLE -> ResponseEntity.status(503).body(Map.of(
                    "code", "WHATSAPP_SERVICE_UNAVAILABLE",
                    "message", "Servico WhatsApp indisponivel no momento. Tente novamente."));
            case CONNECT_TIMEOUT -> ResponseEntity.status(504).body(Map.of(
                    "code", "WHATSAPP_CONNECT_TIMEOUT",
                    "message", "Nao foi possivel concluir a conexao com o WhatsApp a tempo. Tente novamente."));
            default -> ResponseEntity.status(503).body(Map.of(
                    "code", "WHATSAPP_SERVICE_UNAVAILABLE",
                    "message", "Servico WhatsApp indisponivel no momento. Tente novamente."));
        };
    }

    private String planoEfetivo(Long empresaId) {
        return quotaService.planoEfetivoNome(empresaId);
    }

    private void exigirPlanoComWhatsApp(Long empresaId) {
        if (!WhatsAppPlanoPolicy.possuiWhatsApp(planoEfetivo(empresaId))) {
            throw new BusinessException("WhatsApp nao disponivel no plano atual.");
        }
    }
}

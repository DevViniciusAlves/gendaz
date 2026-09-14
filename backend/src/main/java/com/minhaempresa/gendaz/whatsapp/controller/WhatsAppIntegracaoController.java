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
        if (resultado.getStatus() == WhatsAppOperationStatus.NOT_CONFIGURED) {
            return ResponseEntity.status(503).body(Map.of("error", "service_unavailable"));
        }
        return ResponseEntity.status(503).body(Map.of("error", "service_unavailable"));
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
            return ResponseEntity.status(404).body(Map.of("error", "qr_unavailable"));
        }
        return ResponseEntity.status(503).body(Map.of("error", "service_unavailable"));
    }

    @PostMapping("/desconectar")
    public ResponseEntity<?> desconectar() {
        Long empresaId = CompanyContext.requireCompanyId();
        WhatsAppResult<WhatsAppSessionStatus> resultado =
                provider.logout(String.valueOf(empresaId));
        if (resultado.isSuccess()) {
            return ResponseEntity.ok(Map.of("estado", resultado.getData().getState()));
        }
        return ResponseEntity.status(503).body(Map.of("error", "service_unavailable"));
    }

    @PatchMapping("/configuracao")
    public ResponseEntity<?> configuracao(@RequestBody AtualizarConfiguracaoRequest request) {
        Long empresaId = CompanyContext.requireCompanyId();
        if (request == null || (request.lembretesAtivos() == null && request.lembreteTemplate() == null)) {
            throw new BusinessException("Informe a configuracao que deseja alterar.");
        }
        boolean lembretesAtivos = configuracaoService.lembretesAtivos(empresaId);
        if (request.lembretesAtivos() != null) {
            lembretesAtivos = configuracaoService.definirLembretesAtivos(empresaId, request.lembretesAtivos());
        }
        if (request.lembreteTemplate() != null) {
            configuracaoService.definirLembreteTemplate(empresaId, request.lembreteTemplate());
        }
        return ResponseEntity.ok(new ConfiguracaoResponse(
                lembretesAtivos,
                configuracaoService.obterLembreteTemplatePersonalizado(empresaId).orElse(null),
                WhatsAppConfiguracaoService.DEFAULT_LEMBRETE_TEMPLATE));
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

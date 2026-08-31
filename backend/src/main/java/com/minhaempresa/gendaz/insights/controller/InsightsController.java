package com.minhaempresa.gendaz.insights.controller;

import com.minhaempresa.gendaz.insights.dto.InsightsDtos.DashboardResponse;
import com.minhaempresa.gendaz.insights.dto.InsightsDtos.InsightHistoryResponse;
import com.minhaempresa.gendaz.insights.dto.InsightsDtos.InsightsRequest;
import com.minhaempresa.gendaz.insights.dto.InsightsDtos.InsightsResponse;
import com.minhaempresa.gendaz.insights.service.InsightsService;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightsController {
    private final InsightsService insightsService;
    private final AssinaturaService assinaturaService;

    private void validarPlanoPro() {
        if (!assinaturaService.isPlanoComRecursosAvancados(CompanyContext.requireCompanyId())) {
            throw new BusinessException("Esta funcionalidade requer o plano PRO.");
        }
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(
            @RequestParam(value = "periodo", defaultValue = "30") Integer periodo,
            @RequestParam(value = "empresaId", required = false) Long empresaId
    ) {
        validarPlanoPro();
        empresaId = resolverEmpresaId(empresaId);
        if (empresaId == null) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Empresa nao identificada."));
        }
        return insightsService.buscarUltimoDashboardPersistido(empresaId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(Map.of(
                        "sincronizado", false,
                        "mensagem", "Nenhuma analise sincronizada ainda. Clique em Sincronizar dados para gerar sua primeira analise."
                )));
    }

    @GetMapping("/resumo")
    public ResponseEntity<?> resumo(
            @RequestParam(value = "periodo", defaultValue = "30") Integer periodo,
            @RequestParam(value = "empresaId", required = false) Long empresaId
    ) {
        return dashboard(periodo, empresaId);
    }

    @PostMapping("/recalcular")
    public ResponseEntity<?> recalcular(
            @RequestParam(value = "periodo", defaultValue = "30") Integer periodo,
            @RequestParam(value = "empresaId", required = false) Long empresaId
    ) {
        validarPlanoPro();
        empresaId = resolverEmpresaId(empresaId);
        if (empresaId == null) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Empresa nao identificada."));
        }
        DashboardResponse dashboard = insightsService.recalcularDashboard(empresaId, periodo);
        return ResponseEntity.ok(dashboard);
    }

    @GetMapping("/principais")
    public ResponseEntity<?> principais(
            @RequestParam(value = "periodo", defaultValue = "30") Integer periodo,
            @RequestParam(value = "empresaId", required = false) Long empresaId
    ) {
        ResponseEntity<?> response = dashboard(periodo, empresaId);
        if (!response.getStatusCode().is2xxSuccessful()) {
            return response;
        }
        if (!(response.getBody() instanceof DashboardResponse dashboard)) {
            return ResponseEntity.ok(Map.of(
                    "sincronizado", false,
                    "scoreGeral", 0,
                    "alertas", List.of(),
                    "oportunidades", List.of(),
                    "acoes", List.of()
            ));
        }
        return ResponseEntity.ok(Map.of(
                "scoreGeral", dashboard.scoreGeral(),
                "alertas", dashboard.alertas(),
                "oportunidades", dashboard.oportunidades(),
                "acoes", dashboard.acoes()
        ));
    }

    @GetMapping("/oportunidades")
    public ResponseEntity<?> oportunidades(
            @RequestParam(value = "periodo", defaultValue = "30") Integer periodo,
            @RequestParam(value = "empresaId", required = false) Long empresaId
    ) {
        ResponseEntity<?> response = dashboard(periodo, empresaId);
        if (!response.getStatusCode().is2xxSuccessful()) {
            return response;
        }
        if (!(response.getBody() instanceof DashboardResponse dashboard)) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(dashboard.oportunidades());
    }

    @PostMapping("/analisar")
    public ResponseEntity<?> analisar(
            @Valid @RequestBody InsightsRequest request,
            @RequestParam(value = "empresaId", required = false) Long empresaId
    ) {
        validarPlanoPro();
        empresaId = resolverEmpresaId(empresaId);
        if (empresaId == null) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Empresa nao identificada."));
        }
        String respostaChat = insightsService.analisarPergunta(empresaId, request.pergunta(), request.historico());
        insightsService.salvarAnalise(empresaId, "pergunta", request.pergunta(), respostaChat);
        InsightsResponse resposta = new InsightsResponse(true, respostaChat, java.time.LocalDateTime.now(java.time.ZoneId.of("America/Cuiaba")));
        return ResponseEntity.ok(resposta);
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(
            @Valid @RequestBody InsightsRequest request,
            @RequestParam(value = "empresaId", required = false) Long empresaId
    ) {
        return analisar(request, empresaId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detalhe(@PathVariable Long id) {
        var insight = insightsService.obterInsight(id, CompanyContext.requireCompanyId());
        if (insight == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(insight);
    }

    @GetMapping("/historico")
    public ResponseEntity<List<InsightHistoryResponse>> historico(
            @RequestParam(value = "empresaId", required = false) Long empresaId
    ) {
        validarPlanoPro();
        empresaId = resolverEmpresaId(empresaId);
        return ResponseEntity.ok(insightsService.obterHistorico(empresaId));
    }

    private Long resolverEmpresaId(Long empresaId) {
        Long empresaContexto = CompanyContext.requireCompanyId();
        if (empresaId != null && !empresaContexto.equals(empresaId)) {
                throw new BusinessException("Empresa da sessao nao corresponde ao Insights solicitado.");
            }
        return empresaContexto;
    }
}


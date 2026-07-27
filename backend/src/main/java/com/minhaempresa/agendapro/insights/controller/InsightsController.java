package com.minhaempresa.agendapro.insights.controller;

import com.minhaempresa.agendapro.insights.dto.InsightsDtos.DashboardResponse;
import com.minhaempresa.agendapro.insights.dto.InsightsDtos.InsightHistoryResponse;
import com.minhaempresa.agendapro.insights.dto.InsightsDtos.InsightsRequest;
import com.minhaempresa.agendapro.insights.dto.InsightsDtos.InsightsResponse;
import com.minhaempresa.agendapro.insights.service.InsightsService;
import com.minhaempresa.agendapro.shared.CompanyContext;
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

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(
            @RequestParam(value = "periodo", defaultValue = "30") Integer periodo,
            @RequestParam(value = "empresaId", required = false) Long empresaId
    ) {
        empresaId = resolverEmpresaId(empresaId);
        if (empresaId == null) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Empresa nao identificada."));
        }
        DashboardResponse dashboard = insightsService.gerarDashboard(empresaId, periodo);
        return ResponseEntity.ok(dashboard);
    }

    @GetMapping("/resumo")
    public ResponseEntity<?> resumo(
            @RequestParam(value = "periodo", defaultValue = "30") Integer periodo,
            @RequestParam(value = "empresaId", required = false) Long empresaId
    ) {
        return dashboard(periodo, empresaId);
    }

    @GetMapping("/principais")
    public ResponseEntity<?> principais(
            @RequestParam(value = "periodo", defaultValue = "30") Integer periodo,
            @RequestParam(value = "empresaId", required = false) Long empresaId
    ) {
        ResponseEntity<?> response = dashboard(periodo, empresaId);
        if (!response.getStatusCode().is2xxSuccessful() || !(response.getBody() instanceof DashboardResponse dashboard)) {
            return response;
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
        if (!response.getStatusCode().is2xxSuccessful() || !(response.getBody() instanceof DashboardResponse dashboard)) {
            return response;
        }
        return ResponseEntity.ok(dashboard.oportunidades());
    }

    @PostMapping("/analisar")
    public ResponseEntity<?> analisar(
            @Valid @RequestBody InsightsRequest request,
            @RequestParam(value = "empresaId", required = false) Long empresaId
    ) {
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
        var insight = insightsService.obterInsight(id);
        if (insight == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(insight);
    }

    @GetMapping("/historico")
    public ResponseEntity<List<InsightHistoryResponse>> historico(
            @RequestParam(value = "empresaId", required = false) Long empresaId
    ) {
        empresaId = resolverEmpresaId(empresaId);
        if (empresaId == null) {
            return ResponseEntity.badRequest().body(List.of());
        }
        return ResponseEntity.ok(insightsService.obterHistorico(empresaId));
    }

    private Long resolverEmpresaId(Long empresaId) {
        return empresaId != null ? empresaId : CompanyContext.getCompanyId();
    }
}

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
    public ResponseEntity<?> dashboard(@RequestParam(value = "periodo", defaultValue = "30") Integer periodo) {
        Long empresaId = CompanyContext.getCompanyId();
        if (empresaId == null) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Empresa nao identificada."));
        }
        DashboardResponse dashboard = insightsService.gerarDashboard(empresaId, periodo);
        return ResponseEntity.ok(dashboard);
    }

    @PostMapping("/analisar")
    public ResponseEntity<?> analisar(@Valid @RequestBody InsightsRequest request) {
        Long empresaId = CompanyContext.getCompanyId();
        if (empresaId == null) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Empresa nao identificada."));
        }
        InsightsResponse resposta = insightsService.analisarERegistrar(empresaId, request.pergunta());
        return ResponseEntity.ok(resposta);
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
    public ResponseEntity<List<InsightHistoryResponse>> historico() {
        Long empresaId = CompanyContext.getCompanyId();
        if (empresaId == null) {
            return ResponseEntity.badRequest().body(List.of());
        }
        return ResponseEntity.ok(insightsService.obterHistorico(empresaId));
    }
}

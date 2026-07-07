package com.minhaempresa.agendapro.financeiro.controller;

import com.minhaempresa.agendapro.financeiro.dto.FinanceiroDtos.ResumoFinanceiroResponse;
import com.minhaempresa.agendapro.financeiro.service.FinanceiroService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/financeiro")
@RequiredArgsConstructor
public class FinanceiroController {
    private final FinanceiroService financeiroService;

    @GetMapping("/resumo")
    public ResponseEntity<ResumoFinanceiroResponse> resumo(@RequestParam Long empresaId, @RequestParam int mes, @RequestParam int ano) {
        return ResponseEntity.ok(financeiroService.resumo(empresaId, mes, ano));
    }
}

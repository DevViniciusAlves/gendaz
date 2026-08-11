package com.minhaempresa.gendaz.financeiro.controller;

import com.minhaempresa.gendaz.financeiro.dto.FinanceiroDtos.ResumoFinanceiroResponse;
import com.minhaempresa.gendaz.financeiro.service.FinanceiroService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/financeiro")
@RequiredArgsConstructor
public class FinanceiroController {
    private final FinanceiroService financeiroService;

    @GetMapping("/resumo")
    public ResponseEntity<ResumoFinanceiroResponse> resumo(@RequestParam(required = false) Long empresaId, @RequestParam int mes, @RequestParam int ano) {
        return ResponseEntity.ok(financeiroService.resumo(empresaId, mes, ano));
    }
}


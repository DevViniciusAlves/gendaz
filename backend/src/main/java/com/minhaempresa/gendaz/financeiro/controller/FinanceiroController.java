package com.minhaempresa.gendaz.financeiro.controller;

import com.minhaempresa.gendaz.financeiro.dto.FinanceiroDtos.ResumoFinanceiroResponse;
import com.minhaempresa.gendaz.financeiro.service.FinanceiroService;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.AtualizarFormasPagamentoEmpresaRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.FormasPagamentoEmpresaResponse;
import com.minhaempresa.gendaz.pagamento.service.FormaPagamentoEmpresaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/financeiro")
@RequiredArgsConstructor
public class FinanceiroController {
    private final FinanceiroService financeiroService;
    private final FormaPagamentoEmpresaService formaPagamentoEmpresaService;

    @GetMapping("/resumo")
    public ResponseEntity<ResumoFinanceiroResponse> resumo(@RequestParam(required = false) Long empresaId, @RequestParam int mes, @RequestParam int ano) {

        return ResponseEntity.ok(financeiroService.resumo(empresaId, mes, ano));
    }

    @GetMapping("/formas-pagamento")
    public ResponseEntity<FormasPagamentoEmpresaResponse> formasPagamento(@RequestParam(required = false) Long empresaId) {
        return ResponseEntity.ok(formaPagamentoEmpresaService.buscar(empresaId));
    }

    @PutMapping("/formas-pagamento")
    public ResponseEntity<FormasPagamentoEmpresaResponse> atualizarFormasPagamento(
            @RequestParam(required = false) Long empresaId,
            @Valid @RequestBody AtualizarFormasPagamentoEmpresaRequest request) {
        return ResponseEntity.ok(formaPagamentoEmpresaService.atualizar(empresaId, request));
    }
}


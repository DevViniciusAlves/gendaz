package com.minhaempresa.gendaz.crm.controller;

import com.minhaempresa.gendaz.crm.dto.CrmDtos.*;
import com.minhaempresa.gendaz.crm.service.CrmService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/crm")
@RequiredArgsConstructor
public class CrmController {
    private final CrmService crmService;

    @GetMapping("/clientes")
    public ResponseEntity<?> listarClientes(
            @RequestParam(required = false) Long empresaId,
            @RequestParam(required = false) String segment,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String orderBy,
            @RequestParam(required = false) Integer period
    ) {
        if (empresaId == null) {
            empresaId = CompanyContext.getCompanyId();
        }
        if (empresaId == null) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Empresa nao identificada."));
        }
        List<CrmClienteResponse> clientes = crmService.listarClientes(empresaId, segment, search, orderBy, period);
        return ResponseEntity.ok(Map.of("clientes", clientes, "total", clientes.size()));
    }

    @PostMapping("/clientes/{clienteId}/enviar-mensagem")
    public ResponseEntity<?> enviarMensagem(
            @PathVariable Long clienteId,
            @RequestParam(required = false) Long empresaId,
            @Valid @RequestBody EnviarMensagemRequest request
    ) {
        if (empresaId == null) {
            empresaId = CompanyContext.getCompanyId();
        }
        if (empresaId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "mensagem", "Empresa nao identificada."));
        }
        try {
            Map<String, Object> resultado = crmService.enviarMensagem(empresaId, clienteId, request);
            return ResponseEntity.ok(resultado);
        } catch (BusinessException e) {
            return ResponseEntity.ok(Map.of("success", false, "mensagem", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "mensagem", "Erro interno: " + e.getMessage()));
        }
    }

    @GetMapping("/clientes/{clienteId}/historico-contatos")
    public ResponseEntity<List<HistoricoContatoResponse>> historicoContatos(
            @PathVariable Long clienteId,
            @RequestParam(required = false) Long empresaId
    ) {
        if (empresaId == null) {
            empresaId = CompanyContext.getCompanyId();
        }
        if (empresaId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(crmService.historicoContatos(empresaId, clienteId));
    }
}


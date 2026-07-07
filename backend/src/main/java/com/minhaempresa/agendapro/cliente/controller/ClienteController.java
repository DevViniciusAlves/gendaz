package com.minhaempresa.agendapro.cliente.controller;

import com.minhaempresa.agendapro.cliente.dto.ClienteDtos.ClienteResponse;
import com.minhaempresa.agendapro.cliente.dto.ClienteDtos.AcaoEmMassaClienteRequest;
import com.minhaempresa.agendapro.cliente.dto.ClienteDtos.AcaoEmMassaResponse;
import com.minhaempresa.agendapro.cliente.dto.ClienteDtos.SalvarClienteRequest;
import com.minhaempresa.agendapro.cliente.service.ClienteService;
import com.minhaempresa.agendapro.cliente.service.ClienteBulkService;
import com.minhaempresa.agendapro.shared.BusinessException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
public class ClienteController {
    private final ClienteService clienteService;
    private final ClienteBulkService clienteBulkService;

    @PostMapping
    public ResponseEntity<?> criar(@Valid @RequestBody SalvarClienteRequest request) {
        try {
            return ResponseEntity.ok(clienteService.salvar(request));
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "erro", "TELEFONE_INVALIDO",
                    "mensagem", e.getMessage()
            ));
        }
    }

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<List<ClienteResponse>> listarPorEmpresa(@PathVariable Long empresaId) {
        return ResponseEntity.ok(clienteService.listarPorEmpresa(empresaId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(clienteService.buscarPorId(id));
    }

    @GetMapping("/telefone/{telefone}")
    public ResponseEntity<ClienteResponse> buscarPorTelefone(@PathVariable String telefone) {
        return ResponseEntity.ok(clienteService.buscarPorTelefone(telefone));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> atualizar(@PathVariable Long id, @Valid @RequestBody SalvarClienteRequest request) {
        try {
            return ResponseEntity.ok(clienteService.atualizar(id, request));
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "erro", "TELEFONE_INVALIDO",
                    "mensagem", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id, @RequestParam Long empresaId) {
        clienteService.excluir(id, empresaId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/acoes-em-massa")
    public ResponseEntity<AcaoEmMassaResponse> acoesEmMassa(@Valid @RequestBody AcaoEmMassaClienteRequest request) {
        return ResponseEntity.ok(clienteBulkService.excluir(request));
    }
}

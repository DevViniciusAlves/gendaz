package com.minhaempresa.agendapro.cliente.controller;

import com.minhaempresa.agendapro.cliente.dto.ClienteDtos.ClienteResponse;
import com.minhaempresa.agendapro.cliente.dto.ClienteDtos.AcaoEmMassaClienteRequest;
import com.minhaempresa.agendapro.cliente.dto.ClienteDtos.AcaoEmMassaResponse;
import com.minhaempresa.agendapro.cliente.dto.ClienteDtos.SalvarClienteRequest;
import com.minhaempresa.agendapro.cliente.service.ClienteService;
import com.minhaempresa.agendapro.cliente.service.ClienteBulkService;
import jakarta.validation.Valid;
import java.util.List;
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
    public ResponseEntity<ClienteResponse> criar(@Valid @RequestBody SalvarClienteRequest request) {
        return ResponseEntity.ok(clienteService.salvar(request));
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
    public ResponseEntity<ClienteResponse> atualizar(@PathVariable Long id, @Valid @RequestBody SalvarClienteRequest request) {
        return ResponseEntity.ok(clienteService.atualizar(id, request));
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

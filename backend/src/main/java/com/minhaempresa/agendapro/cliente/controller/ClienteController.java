package com.minhaempresa.agendapro.cliente.controller;

import com.minhaempresa.agendapro.cliente.dto.ClienteDtos.ClienteResponse;
import com.minhaempresa.agendapro.cliente.dto.ClienteDtos.AcaoEmMassaClienteRequest;
import com.minhaempresa.agendapro.cliente.dto.ClienteDtos.AcaoEmMassaResponse;
import com.minhaempresa.agendapro.cliente.dto.ClienteDtos.SalvarClienteRequest;
import com.minhaempresa.agendapro.cliente.service.ClienteService;
import com.minhaempresa.agendapro.cliente.service.ClienteBulkService;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.shared.enums.StatusCadastro;
import jakarta.validation.Valid;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
@Slf4j
public class ClienteController {
    private final ClienteService clienteService;
    private final ClienteBulkService clienteBulkService;

    @PostMapping
    public ResponseEntity<?> criar(@Valid @RequestBody SalvarClienteRequest request) {
        Map<String, Object> contexto = new LinkedHashMap<>();
        contexto.put("empresaId", request.empresaId());
        contexto.put("nome", request.nome());
        contexto.put("telefone", request.telefone());
        contexto.put("email", request.email());
        log.debug("[cliente-debug] clique em criar cliente {}", contexto);
        try {
            var response = clienteService.salvar(request);
            Map<String, Object> retorno = new LinkedHashMap<>();
            retorno.put("clienteId", response.id());
            retorno.put("nome", response.nome());
            retorno.put("empresaId", request.empresaId());
            log.info("[cliente-debug] resposta criar cliente sucesso {}", retorno);
            return ResponseEntity.ok(response);
        } catch (BusinessException e) {
            log.error("[cliente-debug] erro no clique criar cliente. mensagem='{}' contexto={}", e.getMessage(), contexto, e);
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
        Map<String, Object> contexto = new LinkedHashMap<>();
        contexto.put("clienteId", id);
        contexto.put("empresaId", request.empresaId());
        contexto.put("nome", request.nome());
        contexto.put("telefone", request.telefone());
        contexto.put("email", request.email());
        log.debug("[cliente-debug] clique em atualizar cliente {}", contexto);
        try {
            ClienteResponse response = clienteService.atualizar(id, request);
            Map<String, Object> retorno = new LinkedHashMap<>();
            retorno.put("clienteId", response.id());
            retorno.put("nome", response.nome());
            retorno.put("empresaId", request.empresaId());
            log.info("[cliente-debug] resposta atualizar cliente sucesso {}", retorno);
            return ResponseEntity.ok(response);
        } catch (BusinessException e) {
            log.error("[cliente-debug] erro no clique atualizar cliente. mensagem='{}' contexto={}", e.getMessage(), contexto, e);
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

    @PatchMapping("/{id}/ativar")
    public ResponseEntity<ClienteResponse> ativar(@PathVariable Long id, @RequestParam Long empresaId) {
        return ResponseEntity.ok(clienteService.alterarStatus(id, empresaId, StatusCadastro.ATIVO));
    }

    @PatchMapping("/{id}/desativar")
    public ResponseEntity<ClienteResponse> desativar(@PathVariable Long id, @RequestParam Long empresaId) {
        return ResponseEntity.ok(clienteService.alterarStatus(id, empresaId, StatusCadastro.INATIVO));
    }

    @PostMapping("/acoes-em-massa")
    public ResponseEntity<AcaoEmMassaResponse> acoesEmMassa(@Valid @RequestBody AcaoEmMassaClienteRequest request) {
        return ResponseEntity.ok(clienteBulkService.excluir(request));
    }
}

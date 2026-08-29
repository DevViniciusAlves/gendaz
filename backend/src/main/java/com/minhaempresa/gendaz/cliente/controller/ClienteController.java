package com.minhaempresa.gendaz.cliente.controller;

import com.minhaempresa.gendaz.cliente.dto.ClienteDtos.ClienteResponse;
import com.minhaempresa.gendaz.cliente.dto.ClienteDtos.AcaoEmMassaClienteRequest;
import com.minhaempresa.gendaz.cliente.dto.ClienteDtos.AcaoEmMassaResponse;
import com.minhaempresa.gendaz.cliente.dto.ClienteDtos.SalvarClienteRequest;
import com.minhaempresa.gendaz.cliente.service.ClienteService;
import com.minhaempresa.gendaz.cliente.service.ClienteBulkService;
import com.minhaempresa.gendaz.shared.PhoneNumberService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
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
    private final PhoneNumberService phoneNumberService;

    private void validarEmpresaAtual(Long empresaId) {
        Long empresaContexto = com.minhaempresa.gendaz.shared.CompanyContext.requireCompanyId();
        if (empresaId != null && !empresaContexto.equals(empresaId)) {
            throw new com.minhaempresa.gendaz.shared.BusinessException("Empresa da sessão não corresponde ao recurso solicitado.");
        }
    }

    @PostMapping
    public ResponseEntity<?> criar(@Valid @RequestBody SalvarClienteRequest request) {
        validarEmpresaAtual(request.empresaId());
        Map<String, Object> contexto = new LinkedHashMap<>();
        contexto.put("empresaId", request.empresaId());
        log.debug("[cliente-debug] clique em criar cliente {}", contexto);
        try {
            var response = clienteService.salvar(request);
            Map<String, Object> retorno = new LinkedHashMap<>();
            retorno.put("clienteId", response.id());
            retorno.put("empresaId", request.empresaId());
            log.info("[cliente-debug] resposta criar cliente sucesso {}", retorno);
            return ResponseEntity.ok(response);
        } catch (BusinessException e) {
            log.error("[cliente-debug] erro no clique criar cliente. erroTipo={} contexto={}", e.getClass().getSimpleName(), contexto);
            return ResponseEntity.badRequest().body(Map.of(
                    "erro", "TELEFONE_INVALIDO",
                    "mensagem", e.getMessage()
            ));
        }
    }

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<List<ClienteResponse>> listarPorEmpresa(@PathVariable Long empresaId) {
        validarEmpresaAtual(empresaId);
        return ResponseEntity.ok(clienteService.listarPorEmpresa(empresaId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(clienteService.buscarPorId(id));
    }

    @GetMapping("/telefone/{telefone}")
    public ResponseEntity<ClienteResponse> buscarPorTelefone(@PathVariable String telefone) {
        String telefoneNormalizado = phoneNumberService.normalizarObrigatorio(telefone);
        return ResponseEntity.ok(clienteService.buscarPorTelefone(telefoneNormalizado));
    }


    @PutMapping("/{id}")
    public ResponseEntity<?> atualizar(@PathVariable Long id, @Valid @RequestBody SalvarClienteRequest request) {
        validarEmpresaAtual(request.empresaId());
        Map<String, Object> contexto = new LinkedHashMap<>();
        contexto.put("clienteId", id);
        contexto.put("empresaId", request.empresaId());
        log.debug("[cliente-debug] clique em atualizar cliente {}", contexto);
        try {
            ClienteResponse response = clienteService.atualizar(id, request);
            Map<String, Object> retorno = new LinkedHashMap<>();
            retorno.put("clienteId", response.id());
            retorno.put("empresaId", request.empresaId());
            log.info("[cliente-debug] resposta atualizar cliente sucesso {}", retorno);
            return ResponseEntity.ok(response);
        } catch (BusinessException e) {
            log.error("[cliente-debug] erro no clique atualizar cliente. erroTipo={} contexto={}", e.getClass().getSimpleName(), contexto);
            return ResponseEntity.badRequest().body(Map.of(
                    "erro", "TELEFONE_INVALIDO",
                    "mensagem", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id, @RequestParam Long empresaId) {
        validarEmpresaAtual(empresaId);
        clienteService.excluir(id, empresaId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/ativar")
    public ResponseEntity<ClienteResponse> ativar(@PathVariable Long id, @RequestParam Long empresaId) {
        validarEmpresaAtual(empresaId);
        return ResponseEntity.ok(clienteService.alterarStatus(id, empresaId, StatusCadastro.ATIVO));
    }

    @PatchMapping("/{id}/desativar")
    public ResponseEntity<ClienteResponse> desativar(@PathVariable Long id, @RequestParam Long empresaId) {
        validarEmpresaAtual(empresaId);
        return ResponseEntity.ok(clienteService.alterarStatus(id, empresaId, StatusCadastro.INATIVO));
    }

    @PostMapping("/ações-em-massa")
    public ResponseEntity<AcaoEmMassaResponse> acoesEmMassa(@Valid @RequestBody AcaoEmMassaClienteRequest request) {
        return ResponseEntity.ok(clienteBulkService.excluir(request));
    }
}


package com.minhaempresa.gendaz.servico.controller;

import com.minhaempresa.gendaz.servico.dto.ServicoDtos.SalvarServicoRequest;
import com.minhaempresa.gendaz.servico.dto.ServicoDtos.ServicoResponse;
import com.minhaempresa.gendaz.servico.service.ServicoService;
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
@RequestMapping("/api/servicos")
@RequiredArgsConstructor
@Slf4j
public class ServicoController {
    private final ServicoService servicoService;

    @PostMapping
    public ResponseEntity<ServicoResponse> criar(@Valid @RequestBody SalvarServicoRequest request) {
        Map<String, Object> contexto = new LinkedHashMap<>();
        contexto.put("empresaId", request.empresaId());
        contexto.put("duracaoMinutos", request.duracaoMinutos());
        contexto.put("valor", request.valor());
        log.debug("[servico-debug] clique em criar servico {}", contexto);
        try {
            ServicoResponse response = servicoService.salvar(request);
            Map<String, Object> retorno = new LinkedHashMap<>();
            retorno.put("servicoId", response.id());
            retorno.put("empresaId", request.empresaId());
            log.info("[servico-debug] resposta criar servico sucesso {}", retorno);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[servico-debug] erro no clique criar servico. erroTipo={} contexto={}", e.getClass().getSimpleName(), contexto);
            throw e;
        }
    }

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<List<ServicoResponse>> listarPorEmpresa(@PathVariable Long empresaId) {
        return ResponseEntity.ok(servicoService.listarPorEmpresa(empresaId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServicoResponse> atualizar(@PathVariable Long id, @Valid @RequestBody SalvarServicoRequest request) {
        Map<String, Object> contexto = new LinkedHashMap<>();
        contexto.put("servicoId", id);
        contexto.put("empresaId", request.empresaId());
        contexto.put("duracaoMinutos", request.duracaoMinutos());
        contexto.put("valor", request.valor());
        log.debug("[servico-debug] clique em atualizar servico {}", contexto);
        try {
            ServicoResponse response = servicoService.atualizar(id, request);
            Map<String, Object> retorno = new LinkedHashMap<>();
            retorno.put("servicoId", response.id());
            retorno.put("empresaId", request.empresaId());
            log.info("[servico-debug] resposta atualizar servico sucesso {}", retorno);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[servico-debug] erro no clique atualizar servico. erroTipo={} contexto={}", e.getClass().getSimpleName(), contexto);
            throw e;
        }
    }

    @PatchMapping("/{id}/ativar")
    public ResponseEntity<ServicoResponse> ativar(@PathVariable Long id) {
        return ResponseEntity.ok(servicoService.alterarStatus(id, StatusCadastro.ATIVO));
    }

    @PatchMapping("/{id}/desativar")
    public ResponseEntity<ServicoResponse> desativar(@PathVariable Long id) {
        return ResponseEntity.ok(servicoService.alterarStatus(id, StatusCadastro.INATIVO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ServicoResponse> excluir(@PathVariable Long id, @RequestParam Long empresaId) {
        return ResponseEntity.ok(servicoService.excluirOuInativar(id, empresaId));
    }
}


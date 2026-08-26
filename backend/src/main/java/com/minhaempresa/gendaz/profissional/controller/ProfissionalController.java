package com.minhaempresa.gendaz.profissional.controller;

import com.minhaempresa.gendaz.profissional.dto.ProfissionalDtos.ProfissionalResponse;
import com.minhaempresa.gendaz.profissional.dto.ProfissionalDtos.SalvarProfissionalRequest;
import com.minhaempresa.gendaz.profissional.service.ProfissionalService;
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
@RequestMapping("/api/profissionais")
@RequiredArgsConstructor
@Slf4j
public class ProfissionalController {
    private final ProfissionalService profissionalService;

    @PostMapping
    public ResponseEntity<ProfissionalResponse> criar(@Valid @RequestBody SalvarProfissionalRequest request) {
        Map<String, Object> contexto = new LinkedHashMap<>();
        contexto.put("empresaId", request.empresaId());
        log.debug("[profissional-debug] clique em criar profissional {}", contexto);
        try {
            ProfissionalResponse response = profissionalService.salvar(request);
            Map<String, Object> retorno = new LinkedHashMap<>();
            retorno.put("profissionalId", response.id());
            retorno.put("empresaId", request.empresaId());
            log.info("[profissional-debug] resposta criar profissional sucesso {}", retorno);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[profissional-debug] erro no clique criar profissional. erroTipo={} contexto={}", e.getClass().getSimpleName(), contexto);
            throw e;
        }
    }

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<List<ProfissionalResponse>> listarPorEmpresa(@PathVariable Long empresaId) {
        return ResponseEntity.ok(profissionalService.listarPorEmpresa(empresaId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProfissionalResponse> atualizar(@PathVariable Long id, @Valid @RequestBody SalvarProfissionalRequest request) {
        Map<String, Object> contexto = new LinkedHashMap<>();
        contexto.put("profissionalId", id);
        contexto.put("empresaId", request.empresaId());
        log.debug("[profissional-debug] clique em atualizar profissional {}", contexto);
        try {
            ProfissionalResponse response = profissionalService.atualizar(id, request);
            Map<String, Object> retorno = new LinkedHashMap<>();
            retorno.put("profissionalId", response.id());
            retorno.put("empresaId", request.empresaId());
            log.info("[profissional-debug] resposta atualizar profissional sucesso {}", retorno);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[profissional-debug] erro no clique atualizar profissional. erroTipo={} contexto={}", e.getClass().getSimpleName(), contexto);
            throw e;
        }
    }

    @PatchMapping("/{id}/ativar")
    public ResponseEntity<ProfissionalResponse> ativar(@PathVariable Long id) {
        return ResponseEntity.ok(profissionalService.alterarStatus(id, StatusCadastro.ATIVO));
    }

    @PatchMapping("/{id}/desativar")
    public ResponseEntity<ProfissionalResponse> desativar(@PathVariable Long id) {
        return ResponseEntity.ok(profissionalService.alterarStatus(id, StatusCadastro.INATIVO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id, @RequestParam Long empresaId) {
        profissionalService.excluir(id, empresaId);
        return ResponseEntity.noContent().build();
    }
}


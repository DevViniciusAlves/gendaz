package com.minhaempresa.agendapro.profissional.controller;

import com.minhaempresa.agendapro.profissional.dto.ProfissionalDtos.ProfissionalResponse;
import com.minhaempresa.agendapro.profissional.dto.ProfissionalDtos.SalvarProfissionalRequest;
import com.minhaempresa.agendapro.profissional.service.ProfissionalService;
import com.minhaempresa.agendapro.shared.enums.StatusCadastro;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profissionais")
@RequiredArgsConstructor
public class ProfissionalController {
    private final ProfissionalService profissionalService;

    @PostMapping
    public ResponseEntity<ProfissionalResponse> criar(@Valid @RequestBody SalvarProfissionalRequest request) {
        return ResponseEntity.ok(profissionalService.salvar(request));
    }

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<List<ProfissionalResponse>> listarPorEmpresa(@PathVariable Long empresaId) {
        return ResponseEntity.ok(profissionalService.listarPorEmpresa(empresaId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProfissionalResponse> atualizar(@PathVariable Long id, @Valid @RequestBody SalvarProfissionalRequest request) {
        return ResponseEntity.ok(profissionalService.atualizar(id, request));
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

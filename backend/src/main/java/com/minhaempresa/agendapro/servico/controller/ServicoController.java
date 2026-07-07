package com.minhaempresa.agendapro.servico.controller;

import com.minhaempresa.agendapro.servico.dto.ServicoDtos.SalvarServicoRequest;
import com.minhaempresa.agendapro.servico.dto.ServicoDtos.ServicoResponse;
import com.minhaempresa.agendapro.servico.service.ServicoService;
import com.minhaempresa.agendapro.shared.enums.StatusCadastro;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/servicos")
@RequiredArgsConstructor
public class ServicoController {
    private final ServicoService servicoService;

    @PostMapping
    public ResponseEntity<ServicoResponse> criar(@Valid @RequestBody SalvarServicoRequest request) {
        return ResponseEntity.ok(servicoService.salvar(request));
    }

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<List<ServicoResponse>> listarPorEmpresa(@PathVariable Long empresaId) {
        return ResponseEntity.ok(servicoService.listarPorEmpresa(empresaId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServicoResponse> atualizar(@PathVariable Long id, @Valid @RequestBody SalvarServicoRequest request) {
        return ResponseEntity.ok(servicoService.atualizar(id, request));
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

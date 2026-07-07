package com.minhaempresa.agendapro.empresa.controller;

import com.minhaempresa.agendapro.empresa.dto.EmpresaDtos.AtualizarEmpresaRequest;
import com.minhaempresa.agendapro.empresa.dto.EmpresaDtos.CriarEmpresaRequest;
import com.minhaempresa.agendapro.empresa.dto.EmpresaDtos.EmpresaResponse;
import com.minhaempresa.agendapro.empresa.service.EmpresaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/empresas")
@RequiredArgsConstructor
public class EmpresaController {
    private final EmpresaService empresaService;

    @PostMapping
    public ResponseEntity<EmpresaResponse> criar(@Valid @RequestBody CriarEmpresaRequest request) {
        return ResponseEntity.ok(empresaService.criar(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmpresaResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(empresaService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmpresaResponse> atualizar(@PathVariable Long id, @Valid @RequestBody AtualizarEmpresaRequest request) {
        return ResponseEntity.ok(empresaService.atualizar(id, request));
    }
}

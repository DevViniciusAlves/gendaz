package com.minhaempresa.agendapro.entrega.controller;

import com.minhaempresa.agendapro.entrega.dto.EntregaDtos.AtualizarStatusEntregaRequest;
import com.minhaempresa.agendapro.entrega.dto.EntregaDtos.CriarEntregaRequest;
import com.minhaempresa.agendapro.entrega.dto.EntregaDtos.EntregaResponse;
import com.minhaempresa.agendapro.entrega.service.EntregaService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/entregas")
@RequiredArgsConstructor
public class EntregaController {
    private final EntregaService entregaService;

    @PostMapping
    public ResponseEntity<EntregaResponse> criar(@Valid @RequestBody CriarEntregaRequest request) {
        return ResponseEntity.ok(entregaService.criar(request));
    }

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<List<EntregaResponse>> listarPorEmpresa(@PathVariable Long empresaId) {
        return ResponseEntity.ok(entregaService.listarPorEmpresa(empresaId));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<EntregaResponse> atualizarStatus(@PathVariable Long id, @Valid @RequestBody AtualizarStatusEntregaRequest request) {
        return ResponseEntity.ok(entregaService.atualizarStatus(id, request));
    }
}

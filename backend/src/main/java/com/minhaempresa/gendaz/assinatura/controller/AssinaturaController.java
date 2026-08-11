package com.minhaempresa.gendaz.assinatura.controller;

import com.minhaempresa.gendaz.assinatura.dto.AssinaturaDtos.AssinaturaResponse;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assinaturas")
@RequiredArgsConstructor
public class AssinaturaController {
    private final AssinaturaService assinaturaService;

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<List<AssinaturaResponse>> listarPorEmpresa(@PathVariable Long empresaId) {
        return ResponseEntity.ok(assinaturaService.listarPorEmpresa(empresaId));
    }

    @GetMapping("/empresa/{empresaId}/atual")
    public ResponseEntity<AssinaturaResponse> buscarAtualPorEmpresa(@PathVariable Long empresaId) {
        return ResponseEntity.ok(assinaturaService.buscarAtualResponsePorEmpresa(empresaId));
    }
}


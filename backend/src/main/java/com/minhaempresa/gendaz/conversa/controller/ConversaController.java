package com.minhaempresa.gendaz.conversa.controller;

import com.minhaempresa.gendaz.conversa.dto.ConversaDtos.ConversaResponse;
import com.minhaempresa.gendaz.conversa.dto.ConversaDtos.CriarConversaRequest;
import com.minhaempresa.gendaz.conversa.service.ConversaService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/conversas")
@RequiredArgsConstructor
public class ConversaController {
    private final ConversaService conversaService;

    @PostMapping
    public ResponseEntity<ConversaResponse> criar(@Valid @RequestBody CriarConversaRequest request) {
        return ResponseEntity.ok(conversaService.criar(request));
    }

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<List<ConversaResponse>> listarPorEmpresa(@PathVariable Long empresaId) {
        return ResponseEntity.ok(conversaService.listarPorEmpresa(empresaId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversaResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(conversaService.buscarPorId(id));
    }

    @PatchMapping("/{id}/finalizar")
    public ResponseEntity<ConversaResponse> finalizar(@PathVariable Long id) {
        return ResponseEntity.ok(conversaService.finalizar(id));
    }
}


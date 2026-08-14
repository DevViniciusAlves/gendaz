package com.minhaempresa.gendaz.chamado.controller;

import com.minhaempresa.gendaz.chamado.dto.ChamadoDtos.AtualizarChamadoRequest;
import com.minhaempresa.gendaz.chamado.dto.ChamadoDtos.ChamadoResponse;
import com.minhaempresa.gendaz.chamado.dto.ChamadoDtos.CriarChamadoRequest;
import com.minhaempresa.gendaz.chamado.service.ChamadoService;
import com.minhaempresa.gendaz.shared.security.UsuarioAutenticadoProvider;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chamados")
@RequiredArgsConstructor
public class ChamadoController {
    private final ChamadoService chamadoService;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    @PostMapping
    public ResponseEntity<ChamadoResponse> criar(
            @Valid @RequestBody CriarChamadoRequest request
    ) {
        Long usuarioAutenticado = usuarioAutenticadoProvider.exigirUsuarioId();
        return ResponseEntity.ok(chamadoService.criar(request, usuarioAutenticado));
    }

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<List<ChamadoResponse>> listarPorEmpresa(
            @PathVariable Long empresaId
    ) {
        Long usuarioAutenticado = usuarioAutenticadoProvider.exigirUsuarioId();
        return ResponseEntity.ok(chamadoService.listarPorEmpresa(empresaId, usuarioAutenticado));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ChamadoResponse> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody AtualizarChamadoRequest request
    ) {
        return ResponseEntity.ok(chamadoService.atualizar(id, request, usuarioAutenticadoProvider.exigirUsuario()));
    }
}


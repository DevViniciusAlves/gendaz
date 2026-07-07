package com.minhaempresa.agendapro.usuario.controller;

import com.minhaempresa.agendapro.usuario.dto.UsuarioDtos.AtualizarUsuarioRequest;
import com.minhaempresa.agendapro.usuario.dto.UsuarioDtos.CriarUsuarioRequest;
import com.minhaempresa.agendapro.usuario.dto.UsuarioDtos.UsuarioResponse;
import com.minhaempresa.agendapro.usuario.service.UsuarioService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {
    private final UsuarioService usuarioService;

    @PostMapping
    public ResponseEntity<UsuarioResponse> criar(@Valid @RequestBody CriarUsuarioRequest request) {
        return ResponseEntity.ok(usuarioService.criar(request));
    }

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<List<UsuarioResponse>> listarPorEmpresa(@PathVariable Long empresaId) {
        return ResponseEntity.ok(usuarioService.listarPorEmpresa(empresaId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponse> atualizar(@PathVariable Long id, @Valid @RequestBody AtualizarUsuarioRequest request) {
        return ResponseEntity.ok(usuarioService.atualizar(id, request));
    }

    @PatchMapping("/{id}/ativar")
    public ResponseEntity<UsuarioResponse> ativar(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.ativar(id));
    }

    @PatchMapping("/{id}/desativar")
    public ResponseEntity<UsuarioResponse> desativar(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.desativar(id));
    }
}

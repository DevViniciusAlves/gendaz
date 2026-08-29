package com.minhaempresa.gendaz.empresa.controller;

import com.minhaempresa.gendaz.empresa.dto.EmpresaDtos.AtualizarEmpresaRequest;
import com.minhaempresa.gendaz.empresa.dto.EmpresaDtos.CriarEmpresaRequest;
import com.minhaempresa.gendaz.empresa.dto.EmpresaDtos.EmpresaResponse;
import com.minhaempresa.gendaz.empresa.service.EmpresaService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.security.UsuarioAutenticadoProvider;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/empresas")
@RequiredArgsConstructor
public class EmpresaController {
    private final EmpresaService empresaService;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    @PostMapping
    public ResponseEntity<EmpresaResponse> criar(@Valid @RequestBody CriarEmpresaRequest request) {
        return ResponseEntity.ok(empresaService.criar(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmpresaResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(empresaService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmpresaResponse> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody AtualizarEmpresaRequest request
    ) {
        validarNaoAtendente();
        return ResponseEntity.ok(empresaService.atualizar(id, request));
    }

    private void validarNaoAtendente() {
        PerfilUsuario perfil = usuarioAutenticadoProvider.exigirPerfil();
        if (perfil == PerfilUsuario.ATENDENTE) {
            throw new BusinessException("Seu perfil não permite alterar dados da empresa.");
        }
    }
}


package com.minhaempresa.gendaz.empresa.controller;

import com.minhaempresa.gendaz.empresa.dto.EmpresaDtos.AtualizarEmpresaRequest;
import com.minhaempresa.gendaz.empresa.dto.EmpresaDtos.CriarEmpresaRequest;
import com.minhaempresa.gendaz.empresa.dto.EmpresaDtos.EmpresaResponse;
import com.minhaempresa.gendaz.empresa.service.EmpresaService;
import com.minhaempresa.gendaz.auth.service.AuthService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/empresas")
@RequiredArgsConstructor
public class EmpresaController {
    private final EmpresaService empresaService;
    private final AuthService authService;

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
            @Valid @RequestBody AtualizarEmpresaRequest request,
            HttpServletRequest http
    ) {
        validarNaoAtendente(http);
        return ResponseEntity.ok(empresaService.atualizar(id, request));
    }

    private void validarNaoAtendente(HttpServletRequest http) {
        Long usuarioId = extrairUsuarioId(http.getHeader("X-Usuario-Id"));
        String sessao = com.minhaempresa.gendaz.shared.CookieHelper.lerCookie(http, "Gendaz_session").orElse(null);
        PerfilUsuario perfil = authService.buscarUsuarioAutenticado(usuarioId, sessao).getPerfil();
        if (perfil == PerfilUsuario.ATENDENTE) {
            throw new BusinessException("Seu perfil nao permite alterar dados da empresa.");
        }
    }

    private Long extrairUsuarioId(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return Long.valueOf(valor);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}


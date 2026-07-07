package com.minhaempresa.agendapro.chamado.controller;

import com.minhaempresa.agendapro.auth.service.AuthService;
import com.minhaempresa.agendapro.chamado.dto.ChamadoDtos.AtualizarChamadoRequest;
import com.minhaempresa.agendapro.chamado.dto.ChamadoDtos.ChamadoResponse;
import com.minhaempresa.agendapro.chamado.dto.ChamadoDtos.CriarChamadoRequest;
import com.minhaempresa.agendapro.chamado.service.ChamadoService;
import com.minhaempresa.agendapro.shared.CookieHelper;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chamados")
@RequiredArgsConstructor
public class ChamadoController {
    private final ChamadoService chamadoService;
    private final AuthService authService;

    @PostMapping
    public ResponseEntity<ChamadoResponse> criar(
            @Valid @RequestBody CriarChamadoRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            HttpServletRequest httpRequest
    ) {
        String sessionToken = CookieHelper.lerCookie(httpRequest, "agendapro_session").orElse(null);
        Long usuarioAutenticado = authService.buscarUsuarioAutenticado(usuarioId, sessionToken).getId();
        return ResponseEntity.ok(chamadoService.criar(request, usuarioAutenticado));
    }

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<List<ChamadoResponse>> listarPorEmpresa(
            @PathVariable Long empresaId,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            HttpServletRequest httpRequest
    ) {
        String sessionToken = CookieHelper.lerCookie(httpRequest, "agendapro_session").orElse(null);
        Long usuarioAutenticado = authService.buscarUsuarioAutenticado(usuarioId, sessionToken).getId();
        return ResponseEntity.ok(chamadoService.listarPorEmpresa(empresaId, usuarioAutenticado));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ChamadoResponse> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody AtualizarChamadoRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            HttpServletRequest httpRequest
    ) {
        String sessionToken = CookieHelper.lerCookie(httpRequest, "agendapro_session").orElse(null);
        return ResponseEntity.ok(chamadoService.atualizar(id, request, authService.buscarUsuarioAutenticado(usuarioId, sessionToken)));
    }
}

package com.minhaempresa.gendaz.lgpd.controller;

import com.minhaempresa.gendaz.lgpd.dto.LgpdDtos.ExcluirContaResponse;
import com.minhaempresa.gendaz.lgpd.dto.LgpdDtos.ExportacaoDadosResponse;
import com.minhaempresa.gendaz.lgpd.service.LgpdService;
import com.minhaempresa.gendaz.auth.service.AuthService;
import com.minhaempresa.gendaz.shared.CookieHelper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lgpd")
@RequiredArgsConstructor
public class LgpdController {
    private final LgpdService lgpdService;
    private final AuthService authService;

    @GetMapping("/exportar")
    public ResponseEntity<ExportacaoDadosResponse> exportar(
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            HttpServletRequest request
    ) {
        String sessionToken = CookieHelper.lerCookie(request, "Gendaz_session").orElse(null);
        Long usuarioAutenticado = authService.buscarUsuarioAutenticado(usuarioId, sessionToken).getId();
        return ResponseEntity.ok(lgpdService.exportar(usuarioAutenticado));
    }

    @DeleteMapping("/excluir-conta")
    public ResponseEntity<ExcluirContaResponse> excluirConta(
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            HttpServletRequest request
    ) {
        String sessionToken = CookieHelper.lerCookie(request, "Gendaz_session").orElse(null);
        Long usuarioAutenticado = authService.buscarUsuarioAutenticado(usuarioId, sessionToken).getId();
        return ResponseEntity.ok(lgpdService.excluirConta(usuarioAutenticado));
    }
}


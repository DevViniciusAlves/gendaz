package com.minhaempresa.agendapro.dashboard.controller;

import com.minhaempresa.agendapro.dashboard.dto.DashboardDtos.PrimeirosPassosResponse;
import com.minhaempresa.agendapro.dashboard.dto.DashboardDtos.DashboardResumoResponse;
import com.minhaempresa.agendapro.dashboard.service.DashboardService;
import com.minhaempresa.agendapro.auth.service.AuthService;
import com.minhaempresa.agendapro.shared.CookieHelper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;
    private final AuthService authService;

    @GetMapping("/primeiros-passos")
    public ResponseEntity<PrimeirosPassosResponse> primeirosPassos(
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            HttpServletRequest request
    ) {
        String sessionToken = CookieHelper.lerCookie(request, "agendapro_session").orElse(null);
        Long usuarioAutenticado = authService.buscarUsuarioAutenticado(usuarioId, sessionToken).getId();
        return ResponseEntity.ok(dashboardService.primeirosPassos(usuarioAutenticado));
    }

    @GetMapping("/resumo")
    public ResponseEntity<DashboardResumoResponse> resumo(
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            HttpServletRequest request
    ) {
        String sessionToken = CookieHelper.lerCookie(request, "agendapro_session").orElse(null);
        Long usuarioAutenticado = authService.buscarUsuarioAutenticado(usuarioId, sessionToken).getId();
        return ResponseEntity.ok(dashboardService.resumo(usuarioAutenticado));
    }
}

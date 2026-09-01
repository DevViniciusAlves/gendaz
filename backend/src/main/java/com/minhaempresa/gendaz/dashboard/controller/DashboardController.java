package com.minhaempresa.gendaz.dashboard.controller;

import com.minhaempresa.gendaz.dashboard.dto.DashboardDtos.PrimeirosPassosResponse;
import com.minhaempresa.gendaz.dashboard.dto.DashboardDtos.DashboardResumoResponse;
import com.minhaempresa.gendaz.dashboard.service.DashboardService;
import com.minhaempresa.gendaz.shared.security.UsuarioAutenticadoProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    @GetMapping("/primeiros-passos")
    public ResponseEntity<PrimeirosPassosResponse> primeirosPassos() {
        Long usuarioAutenticado = usuarioAutenticadoProvider.exigirUsuarioId();
        return ResponseEntity.ok(dashboardService.primeirosPassos(usuarioAutenticado));
    }

    @GetMapping("/resumo")
    public ResponseEntity<DashboardResumoResponse> resumo(
            @RequestParam(required = false) Long empresaId,
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) Integer ano) {
        Long usuarioAutenticado = usuarioAutenticadoProvider.exigirUsuarioId();
        return ResponseEntity.ok(dashboardService.resumo(usuarioAutenticado, empresaId, mes, ano));
    }
}


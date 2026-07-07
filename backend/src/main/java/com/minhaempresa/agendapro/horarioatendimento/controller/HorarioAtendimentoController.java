package com.minhaempresa.agendapro.horarioatendimento.controller;

import com.minhaempresa.agendapro.auth.service.AuthService;
import com.minhaempresa.agendapro.configuracao.dto.HorarioAtendimentoDtos.HorarioAtendimentoResponse;
import com.minhaempresa.agendapro.configuracao.dto.HorarioAtendimentoDtos.SalvarHorariosAtendimentoRequest;
import com.minhaempresa.agendapro.horarioatendimento.service.HorarioAtendimentoService;
import com.minhaempresa.agendapro.shared.CookieHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/configuracoes/horario-atendimento")
@RequiredArgsConstructor
public class HorarioAtendimentoController {
    private final HorarioAtendimentoService service;
    private final AuthService authService;

    @GetMapping
    public ResponseEntity<List<HorarioAtendimentoResponse>> listar(
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            HttpServletRequest request
    ) {
        String sessionToken = CookieHelper.lerCookie(request, "agendapro_session").orElse(null);
        Long usuarioAutenticado = authService.buscarUsuarioAutenticado(usuarioId, sessionToken).getId();
        return ResponseEntity.ok(service.listarPorUsuario(usuarioAutenticado));
    }

    @PutMapping
    public ResponseEntity<List<HorarioAtendimentoResponse>> salvar(
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            HttpServletRequest request,
            @Valid @RequestBody SalvarHorariosAtendimentoRequest body
    ) {
        String sessionToken = CookieHelper.lerCookie(request, "agendapro_session").orElse(null);
        Long usuarioAutenticado = authService.buscarUsuarioAutenticado(usuarioId, sessionToken).getId();
        return ResponseEntity.ok(service.salvar(usuarioAutenticado, body));
    }
}

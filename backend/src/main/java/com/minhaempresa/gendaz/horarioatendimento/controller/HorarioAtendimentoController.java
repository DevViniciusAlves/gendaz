package com.minhaempresa.gendaz.horarioatendimento.controller;

import com.minhaempresa.gendaz.auth.service.AuthService;
import com.minhaempresa.gendaz.configuracao.dto.HorarioAtendimentoDtos.HorarioAtendimentoResponse;
import com.minhaempresa.gendaz.configuracao.dto.HorarioAtendimentoDtos.SalvarHorariosAtendimentoRequest;
import com.minhaempresa.gendaz.horarioatendimento.service.HorarioAtendimentoService;
import com.minhaempresa.gendaz.shared.CookieHelper;
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
        String sessionToken = CookieHelper.lerCookie(request, "meu_gendaz_session").orElse(null);
        Long usuarioAutenticado = authService.buscarUsuarioAutenticado(usuarioId, sessionToken).getId();
        return ResponseEntity.ok(service.listarPorUsuario(usuarioAutenticado));
    }

    @PutMapping
    public ResponseEntity<List<HorarioAtendimentoResponse>> salvar(
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            HttpServletRequest request,
            @Valid @RequestBody SalvarHorariosAtendimentoRequest body
    ) {
        String sessionToken = CookieHelper.lerCookie(request, "meu_gendaz_session").orElse(null);
        Long usuarioAutenticado = authService.buscarUsuarioAutenticado(usuarioId, sessionToken).getId();
        return ResponseEntity.ok(service.salvar(usuarioAutenticado, body));
    }
}


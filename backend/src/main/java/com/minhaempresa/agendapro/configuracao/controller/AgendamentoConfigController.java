package com.minhaempresa.agendapro.configuracao.controller;

import com.minhaempresa.agendapro.configuracao.dto.AgendamentoConfigDtos.AgendamentoLinkResponse;
import com.minhaempresa.agendapro.configuracao.dto.AgendamentoConfigDtos.AtualizarAgendamentoSlugRequest;
import com.minhaempresa.agendapro.configuracao.service.AgendamentoConfigService;
import com.minhaempresa.agendapro.auth.service.AuthService;
import com.minhaempresa.agendapro.shared.CookieHelper;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/configuracoes/agendamento")
@RequiredArgsConstructor
public class AgendamentoConfigController {
    private final AgendamentoConfigService service;
    private final AuthService authService;

    @GetMapping("/link")
    public ResponseEntity<AgendamentoLinkResponse> obterLink(
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            HttpServletRequest request
    ) {
        String sessionToken = CookieHelper.lerCookie(request, "agendapro_session").orElse(null);
        Long usuarioAutenticado = authService.buscarUsuarioAutenticado(usuarioId, sessionToken).getId();
        return ResponseEntity.ok(service.obterLink(usuarioAutenticado));
    }

    @PutMapping("/link")
    public ResponseEntity<AgendamentoLinkResponse> atualizarSlug(
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            HttpServletRequest request,
            @Valid @RequestBody AtualizarAgendamentoSlugRequest body
    ) {
        String sessionToken = CookieHelper.lerCookie(request, "agendapro_session").orElse(null);
        Long usuarioAutenticado = authService.buscarUsuarioAutenticado(usuarioId, sessionToken).getId();
        return ResponseEntity.ok(service.atualizarSlug(usuarioAutenticado, body));
    }
}

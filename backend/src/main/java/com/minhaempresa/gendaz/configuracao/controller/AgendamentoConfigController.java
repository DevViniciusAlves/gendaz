package com.minhaempresa.gendaz.configuração.controller;

import com.minhaempresa.gendaz.configuração.dto.AgendamentoConfigDtos.AgendamentoLinkResponse;
import com.minhaempresa.gendaz.configuração.dto.AgendamentoConfigDtos.AtualizarAgendamentoSlugRequest;
import com.minhaempresa.gendaz.configuração.service.AgendamentoConfigService;
import com.minhaempresa.gendaz.shared.security.UsuarioAutenticadoProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/configurações/agendamento")
@RequiredArgsConstructor
public class AgendamentoConfigController {
    private final AgendamentoConfigService service;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    @GetMapping("/link")
    public ResponseEntity<AgendamentoLinkResponse> obterLink() {
        Long usuarioAutenticado = usuarioAutenticadoProvider.exigirUsuarioId();
        return ResponseEntity.ok(service.obterLink(usuarioAutenticado));
    }

    @PutMapping("/link")
    public ResponseEntity<AgendamentoLinkResponse> atualizarSlug(
            @Valid @RequestBody AtualizarAgendamentoSlugRequest body
    ) {
        Long usuarioAutenticado = usuarioAutenticadoProvider.exigirUsuarioId();
        return ResponseEntity.ok(service.atualizarSlug(usuarioAutenticado, body));
    }
}


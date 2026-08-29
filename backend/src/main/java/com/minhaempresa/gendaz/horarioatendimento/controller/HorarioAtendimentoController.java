package com.minhaempresa.gendaz.horarioatendimento.controller;

import com.minhaempresa.gendaz.configuração.dto.HorarioAtendimentoDtos.HorarioAtendimentoResponse;
import com.minhaempresa.gendaz.configuração.dto.HorarioAtendimentoDtos.SalvarHorariosAtendimentoRequest;
import com.minhaempresa.gendaz.horarioatendimento.service.HorarioAtendimentoService;
import com.minhaempresa.gendaz.shared.security.UsuarioAutenticadoProvider;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/configurações/horario-atendimento")
@RequiredArgsConstructor
public class HorarioAtendimentoController {
    private final HorarioAtendimentoService service;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    @GetMapping
    public ResponseEntity<List<HorarioAtendimentoResponse>> listar() {
        Long usuarioAutenticado = usuarioAutenticadoProvider.exigirUsuarioId();
        return ResponseEntity.ok(service.listarPorUsuario(usuarioAutenticado));
    }

    @PutMapping
    public ResponseEntity<List<HorarioAtendimentoResponse>> salvar(
            @Valid @RequestBody SalvarHorariosAtendimentoRequest body
    ) {
        Long usuarioAutenticado = usuarioAutenticadoProvider.exigirUsuarioId();
        return ResponseEntity.ok(service.salvar(usuarioAutenticado, body));
    }
}


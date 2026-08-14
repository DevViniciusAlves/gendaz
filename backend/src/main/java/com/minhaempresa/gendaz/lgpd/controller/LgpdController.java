package com.minhaempresa.gendaz.lgpd.controller;

import com.minhaempresa.gendaz.lgpd.dto.LgpdDtos.ExcluirContaResponse;
import com.minhaempresa.gendaz.lgpd.dto.LgpdDtos.ExportacaoDadosResponse;
import com.minhaempresa.gendaz.lgpd.service.LgpdService;
import com.minhaempresa.gendaz.shared.security.UsuarioAutenticadoProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lgpd")
@RequiredArgsConstructor
public class LgpdController {
    private final LgpdService lgpdService;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    @GetMapping("/exportar")
    public ResponseEntity<ExportacaoDadosResponse> exportar() {
        Long usuarioAutenticado = usuarioAutenticadoProvider.exigirUsuarioId();
        return ResponseEntity.ok(lgpdService.exportar(usuarioAutenticado));
    }

    @DeleteMapping("/excluir-conta")
    public ResponseEntity<ExcluirContaResponse> excluirConta() {
        Long usuarioAutenticado = usuarioAutenticadoProvider.exigirUsuarioId();
        return ResponseEntity.ok(lgpdService.excluirConta(usuarioAutenticado));
    }
}


package com.minhaempresa.agendapro.agendamento.controller;

import com.minhaempresa.agendapro.agendamento.dto.AgendaBlockedDayDtos.BloquearDiaRequest;
import com.minhaempresa.agendapro.agendamento.dto.AgendaBlockedDayDtos.DiaBloqueadoResponse;
import com.minhaempresa.agendapro.agendamento.service.AgendaBlockedDayService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agendamentos/bloqueios")
@RequiredArgsConstructor
public class AgendaBlockedDayController {
    private final AgendaBlockedDayService service;

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<List<DiaBloqueadoResponse>> listar(@PathVariable Long empresaId) {
        return ResponseEntity.ok(service.listar(empresaId));
    }

    @PostMapping
    public ResponseEntity<DiaBloqueadoResponse> bloquear(@Valid @RequestBody BloquearDiaRequest request) {
        return ResponseEntity.ok(service.bloquear(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desbloquear(@PathVariable Long id, @RequestParam Long empresaId) {
        service.desbloquear(id, empresaId);
        return ResponseEntity.noContent().build();
    }
}

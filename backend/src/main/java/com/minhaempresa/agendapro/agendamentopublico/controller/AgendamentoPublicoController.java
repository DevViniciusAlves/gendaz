package com.minhaempresa.agendapro.agendamentopublico.controller;

import com.minhaempresa.agendapro.agendamentopublico.dto.AgendamentoPublicoDtos.AgendamentoPublicoResponse;
import com.minhaempresa.agendapro.agendamentopublico.dto.AgendamentoPublicoDtos.BookingEmpresaResponse;
import com.minhaempresa.agendapro.agendamentopublico.dto.AgendamentoPublicoDtos.CriarAgendamentoPublicoRequest;
import com.minhaempresa.agendapro.agendamentopublico.service.AgendamentoPublicoService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/agendamento-publico", "/api/public/agendamento"})
@RequiredArgsConstructor
public class AgendamentoPublicoController {
    private final AgendamentoPublicoService service;

    @GetMapping("/{slugOuEmpresaId}")
    public ResponseEntity<BookingEmpresaResponse> carregar(@PathVariable String slugOuEmpresaId) {
        return ResponseEntity.ok(service.carregar(slugOuEmpresaId));
    }

    @GetMapping("/{slugOuEmpresaId}/horarios")
    public ResponseEntity<List<String>> horarios(
            @PathVariable String slugOuEmpresaId,
            @RequestParam(required = false) Long profissionalId,
            @RequestParam Long servicoId,
            @RequestParam LocalDate data
    ) {
        return ResponseEntity.ok(service.horarios(slugOuEmpresaId, profissionalId, servicoId, data));
    }

    @GetMapping("/{slugOuEmpresaId}/disponibilidade")
    public ResponseEntity<List<String>> disponibilidade(
            @PathVariable String slugOuEmpresaId,
            @RequestParam(required = false) Long profissionalId,
            @RequestParam Long servicoId,
            @RequestParam LocalDate data
    ) {
        return ResponseEntity.ok(service.horarios(slugOuEmpresaId, profissionalId, servicoId, data));
    }

    @PostMapping("/{slugOuEmpresaId}/agendar")
    public ResponseEntity<AgendamentoPublicoResponse> agendar(
            @PathVariable String slugOuEmpresaId,
            @Valid @RequestBody CriarAgendamentoPublicoRequest request
    ) {
        return ResponseEntity.ok(service.agendar(slugOuEmpresaId, request));
    }

    @PostMapping("/{slugOuEmpresaId}/confirmar")
    public ResponseEntity<AgendamentoPublicoResponse> confirmar(
            @PathVariable String slugOuEmpresaId,
            @Valid @RequestBody CriarAgendamentoPublicoRequest request
    ) {
        return ResponseEntity.ok(service.agendar(slugOuEmpresaId, request));
    }
}

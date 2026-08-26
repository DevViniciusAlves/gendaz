package com.minhaempresa.gendaz.agendamentopublico.controller;

import com.minhaempresa.gendaz.agendamentopublico.dto.AgendamentoPublicoDtos.AgendamentoPublicoResponse;
import com.minhaempresa.gendaz.agendamentopublico.dto.AgendamentoPublicoDtos.BookingEmpresaResponse;
import com.minhaempresa.gendaz.agendamentopublico.dto.AgendamentoPublicoDtos.CriarAgendamentoPublicoRequest;
import com.minhaempresa.gendaz.agendamentopublico.service.AgendamentoPublicoService;
import com.minhaempresa.gendaz.shared.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
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
    private final ClientIpResolver clientIpResolver;

    @GetMapping("/{slugOuEmpresaId}")
    public ResponseEntity<BookingEmpresaResponse> carregar(@PathVariable String slugOuEmpresaId) {
        return ResponseEntity.ok(service.carregar(slugOuEmpresaId));
    }

    @GetMapping("/{slugOuEmpresaId}/horarios")
    public ResponseEntity<List<String>> horarios(
            @PathVariable String slugOuEmpresaId,
            @RequestParam(required = false) Long profissionalId,
            @RequestParam Long servicoId,
            @RequestParam LocalDate data,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(service.horarios(slugOuEmpresaId, profissionalId, servicoId, data, clientIpResolver.resolve(httpRequest)));
    }

    @GetMapping("/{slugOuEmpresaId}/disponibilidade")
    public ResponseEntity<List<String>> disponibilidade(
            @PathVariable String slugOuEmpresaId,
            @RequestParam(required = false) Long profissionalId,
            @RequestParam Long servicoId,
            @RequestParam LocalDate data,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(service.horarios(slugOuEmpresaId, profissionalId, servicoId, data, clientIpResolver.resolve(httpRequest)));
    }

    @PostMapping("/{slugOuEmpresaId}/agendar")
    public ResponseEntity<?> agendar(
            @PathVariable String slugOuEmpresaId,
            @Valid @RequestBody CriarAgendamentoPublicoRequest request,
            HttpServletRequest httpRequest
    ) {
        try {
            return ResponseEntity.ok(service.agendar(slugOuEmpresaId, request, clientIpResolver.resolve(httpRequest)));
        } catch (com.minhaempresa.gendaz.shared.BusinessException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("erro", "TELEFONE_INVALIDO", "mensagem", e.getMessage()));
        }
    }

    @PostMapping("/{slugOuEmpresaId}/confirmar")
    public ResponseEntity<?> confirmar(
            @PathVariable String slugOuEmpresaId,
            @Valid @RequestBody CriarAgendamentoPublicoRequest request,
            HttpServletRequest httpRequest
    ) {
        try {
            return ResponseEntity.ok(service.agendar(slugOuEmpresaId, request, clientIpResolver.resolve(httpRequest)));
        } catch (com.minhaempresa.gendaz.shared.BusinessException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("erro", "TELEFONE_INVALIDO", "mensagem", e.getMessage()));
        }
    }
}


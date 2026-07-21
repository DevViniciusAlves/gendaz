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
    public ResponseEntity<?> agendar(
            @PathVariable String slugOuEmpresaId,
            @Valid @RequestBody CriarAgendamentoPublicoRequest request
    ) {
        ResponseEntity<?> erroValidador = validarTelefoneRequest(request.clienteTelefone());
        if (erroValidador != null) {
            return erroValidador;
        }
        try {
            return ResponseEntity.ok(service.agendar(slugOuEmpresaId, request));
        } catch (com.minhaempresa.agendapro.shared.BusinessException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("erro", "TELEFONE_INVALIDO", "mensagem", e.getMessage()));
        }
    }

    @PostMapping("/{slugOuEmpresaId}/confirmar")
    public ResponseEntity<?> confirmar(
            @PathVariable String slugOuEmpresaId,
            @Valid @RequestBody CriarAgendamentoPublicoRequest request
    ) {
        ResponseEntity<?> erroValidador = validarTelefoneRequest(request.clienteTelefone());
        if (erroValidador != null) {
            return erroValidador;
        }
        try {
            return ResponseEntity.ok(service.agendar(slugOuEmpresaId, request));
        } catch (com.minhaempresa.agendapro.shared.BusinessException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("erro", "TELEFONE_INVALIDO", "mensagem", e.getMessage()));
        }
    }

    private ResponseEntity<?> validarTelefoneRequest(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                    "erro", "TELEFONE_INVALIDO",
                    "mensagem", "Telefone é obrigatório"
            ));
        }

        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.length() < 11 || digitos.length() > 14) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                    "erro", "TELEFONE_INVALIDO",
                    "mensagem", "Telefone invalido. Use codigo da cidade + numero. Voce informou apenas " + digitos.length() + " digitos."
            ));
        }

        return null;
    }
}

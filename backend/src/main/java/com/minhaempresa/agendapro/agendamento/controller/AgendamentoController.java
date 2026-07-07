package com.minhaempresa.agendapro.agendamento.controller;

import com.minhaempresa.agendapro.agendamento.dto.AgendamentoDtos.AgendamentoResponse;
import com.minhaempresa.agendapro.agendamento.dto.AgendamentoDtos.AcaoEmMassaAgendamentoRequest;
import com.minhaempresa.agendapro.agendamento.dto.AgendamentoDtos.AcaoEmMassaResponse;
import com.minhaempresa.agendapro.agendamento.dto.AgendamentoDtos.AtualizarAgendamentoRequest;
import com.minhaempresa.agendapro.agendamento.dto.AgendamentoDtos.CriarAgendamentoRequest;
import com.minhaempresa.agendapro.agendamento.dto.AgendamentoDtos.RemarcarAgendamentoRequest;
import com.minhaempresa.agendapro.agendamento.service.AgendamentoService;
import com.minhaempresa.agendapro.agendamento.service.AgendamentoBulkService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agendamentos")
@RequiredArgsConstructor
public class AgendamentoController {
    private final AgendamentoService agendamentoService;
    private final AgendamentoBulkService agendamentoBulkService;

    @PostMapping
    public ResponseEntity<AgendamentoResponse> criar(@Valid @RequestBody CriarAgendamentoRequest request) {
        return ResponseEntity.ok(agendamentoService.criar(request));
    }

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<List<AgendamentoResponse>> listarPorEmpresa(@PathVariable Long empresaId) {
        return ResponseEntity.ok(agendamentoService.listarPorEmpresa(empresaId));
    }

    @GetMapping("/data")
    public ResponseEntity<List<AgendamentoResponse>> listarPorData(@RequestParam Long empresaId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return ResponseEntity.ok(agendamentoService.listarPorData(empresaId, data));
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<AgendamentoResponse>> listarPorCliente(@PathVariable Long clienteId) {
        return ResponseEntity.ok(agendamentoService.listarPorCliente(clienteId));
    }

    @GetMapping("/horarios-disponiveis")
    public ResponseEntity<List<String>> horariosDisponiveis(@RequestParam Long empresaId, @RequestParam Long profissionalId, @RequestParam Long servicoId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return ResponseEntity.ok(agendamentoService.horariosDisponiveis(empresaId, profissionalId, servicoId, data));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AgendamentoResponse> atualizar(@PathVariable Long id, @Valid @RequestBody AtualizarAgendamentoRequest request) {
        return ResponseEntity.ok(agendamentoService.atualizar(id, request));
    }

    @PatchMapping("/{id}/confirmar")
    public ResponseEntity<AgendamentoResponse> confirmar(@PathVariable Long id) {
        return ResponseEntity.ok(agendamentoService.confirmar(id));
    }

    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<AgendamentoResponse> cancelar(@PathVariable Long id, @RequestParam(required = false) Long empresaId) {
        return ResponseEntity.ok(agendamentoService.cancelar(id, empresaId));
    }

    @PatchMapping("/{id}/finalizar")
    public ResponseEntity<AgendamentoResponse> finalizar(@PathVariable Long id) {
        return ResponseEntity.ok(agendamentoService.finalizar(id));
    }

    @PutMapping("/{id}/remarcar")
    public ResponseEntity<AgendamentoResponse> remarcar(@PathVariable Long id, @Valid @RequestBody RemarcarAgendamentoRequest request) {
        return ResponseEntity.ok(agendamentoService.remarcar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id, @RequestParam Long empresaId) {
        agendamentoService.excluir(id, empresaId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/acoes-em-massa")
    public ResponseEntity<AcaoEmMassaResponse> acoesEmMassa(@Valid @RequestBody AcaoEmMassaAgendamentoRequest request) {
        return ResponseEntity.ok(agendamentoBulkService.executar(request));
    }
}

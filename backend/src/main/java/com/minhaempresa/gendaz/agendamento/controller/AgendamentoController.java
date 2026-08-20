package com.minhaempresa.gendaz.agendamento.controller;

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AgendamentoResponse;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AcaoEmMassaAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AcaoEmMassaResponse;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AtualizarAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.CriarAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.FinalizarAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.RemarcarAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.service.AgendamentoService;
import com.minhaempresa.gendaz.agendamento.service.AgendamentoBulkService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agendamentos")
@RequiredArgsConstructor
@Slf4j
public class AgendamentoController {
    private final AgendamentoService agendamentoService;
    private final AgendamentoBulkService agendamentoBulkService;

    @PostMapping
    public ResponseEntity<AgendamentoResponse> criar(@Valid @RequestBody CriarAgendamentoRequest request) {
        Map<String, Object> contexto = new LinkedHashMap<>();
        contexto.put("empresaId", request.empresaId());
        contexto.put("clienteId", request.clienteId());
        contexto.put("servicoId", request.servicoId());
        contexto.put("profissionalId", request.profissionalId());
        log.debug("[agendamento-debug] clique em criar agendamento {}", contexto);
        try {
            AgendamentoResponse response = agendamentoService.criar(request);
            Map<String, Object> retorno = new LinkedHashMap<>();
            retorno.put("agendamentoId", response.id());
            retorno.put("status", response.status());
            retorno.put("empresaId", request.empresaId());
            log.info("[agendamento-debug] resposta criar agendamento sucesso {}", retorno);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[agendamento-debug] erro no clique criar agendamento. erroTipo={} contexto={}", e.getClass().getSimpleName(), contexto);
            throw e;
        }
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
        Map<String, Object> contexto = new LinkedHashMap<>();
        contexto.put("agendamentoId", id);
        contexto.put("empresaId", request.empresaId());
        contexto.put("clienteId", request.clienteId());
        contexto.put("servicoId", request.servicoId());
        contexto.put("profissionalId", request.profissionalId());
        contexto.put("status", request.status());
        log.debug("[agendamento-debug] clique em atualizar agendamento {}", contexto);
        try {
            AgendamentoResponse response = agendamentoService.atualizar(id, request);
            Map<String, Object> retorno = new LinkedHashMap<>();
            retorno.put("agendamentoId", response.id());
            retorno.put("status", response.status());
            retorno.put("empresaId", request.empresaId());
            log.info("[agendamento-debug] resposta atualizar agendamento sucesso {}", retorno);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[agendamento-debug] erro no clique atualizar agendamento. erroTipo={} contexto={}", e.getClass().getSimpleName(), contexto);
            throw e;
        }
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
    public ResponseEntity<AgendamentoResponse> finalizar(@PathVariable Long id, @RequestBody(required = false) FinalizarAgendamentoRequest request) {
        Boolean pagamentoRealizado = request == null ? null : request.pagamentoRealizado();
        return ResponseEntity.ok(agendamentoService.finalizar(
                id,
                pagamentoRealizado,
                request == null ? null : request.metodoPagamento(),
                request == null ? null : request.parcelas()
        ));
    }

    @PatchMapping("/{id}/iniciar")
    public ResponseEntity<AgendamentoResponse> iniciar(@PathVariable Long id) {
        return ResponseEntity.ok(agendamentoService.iniciar(id));
    }

    @PatchMapping("/{id}/pausar")
    public ResponseEntity<AgendamentoResponse> pausar(@PathVariable Long id) {
        return ResponseEntity.ok(agendamentoService.pausar(id));
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


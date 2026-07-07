package com.minhaempresa.agendapro.notafiscal.controller;

import com.minhaempresa.agendapro.notafiscal.dto.NotaFiscalDtos.EmitirNotaFiscalRequest;
import com.minhaempresa.agendapro.notafiscal.dto.NotaFiscalDtos.NotaFiscalResponse;
import com.minhaempresa.agendapro.notafiscal.service.NotaFiscalService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notas-fiscais")
@RequiredArgsConstructor
public class NotaFiscalController {
    private final NotaFiscalService notaFiscalService;

    @PostMapping("/emitir")
    public ResponseEntity<NotaFiscalResponse> emitir(@Valid @RequestBody EmitirNotaFiscalRequest request) {
        return ResponseEntity.ok(notaFiscalService.emitir(request));
    }

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<List<NotaFiscalResponse>> listarPorEmpresa(@PathVariable Long empresaId) {
        return ResponseEntity.ok(notaFiscalService.listarPorEmpresa(empresaId));
    }
}

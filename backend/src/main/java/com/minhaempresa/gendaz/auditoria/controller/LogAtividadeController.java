package com.minhaempresa.gendaz.auditoria.controller;

import com.minhaempresa.gendaz.auditoria.dto.LogAtividadeDtos.LogAtividadeResponse;
import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
import com.minhaempresa.gendaz.shared.CompanyContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/logs-atividade")
@RequiredArgsConstructor
public class LogAtividadeController {

    private final LogAtividadeService logAtividadeService;

    @GetMapping
    public ResponseEntity<Page<LogAtividadeResponse>> listar(
            @RequestParam(required = false) String entidade,
            @RequestParam(required = false) String termo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        // Tenant SEMPRE resolvido no servidor. Nunca confiar em empresaId vindo do frontend.
        Long empresaId = CompanyContext.requireCompanyId();
        int tamanho = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(Math.max(page, 0), tamanho, Sort.by(Sort.Direction.DESC, "dataHora"));
        Page<LogAtividadeResponse> resultado = logAtividadeService.listar(empresaId, entidade, termo, pageable);
        return ResponseEntity.ok(resultado);
    }
}

package com.minhaempresa.agendapro.plano.controller;

import com.minhaempresa.agendapro.plano.dto.PlanoDtos.PlanoResponse;
import com.minhaempresa.agendapro.plano.service.PlanoService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/planos")
@RequiredArgsConstructor
public class PlanoController {
    private final PlanoService planoService;

    @GetMapping
    public ResponseEntity<List<PlanoResponse>> listar() {
        return ResponseEntity.ok(planoService.listar());
    }
}

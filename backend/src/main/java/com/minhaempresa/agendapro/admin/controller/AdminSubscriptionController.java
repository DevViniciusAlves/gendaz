package com.minhaempresa.agendapro.admin.controller;

import com.minhaempresa.agendapro.admin.dto.AdminAssinaturaDtos.AssinaturaAdminResponse;
import com.minhaempresa.agendapro.admin.dto.AdminAssinaturaDtos.CriarAssinaturaRequest;
import com.minhaempresa.agendapro.admin.dto.AdminAssinaturaDtos.EditarAssinaturaRequest;
import com.minhaempresa.agendapro.admin.service.SubscriptionAdminService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/empresas/{empresaId}/subscriptions")
@RequiredArgsConstructor
public class AdminSubscriptionController {

    private final SubscriptionAdminService subscriptionAdminService;

    @GetMapping
    public ResponseEntity<List<AssinaturaAdminResponse>> listarAssinaturas(@PathVariable Long empresaId) {
        return ResponseEntity.ok(subscriptionAdminService.listarAssinaturas(empresaId));
    }

    @PostMapping
    public ResponseEntity<List<AssinaturaAdminResponse>> criarAssinatura(
            @PathVariable Long empresaId,
            @Valid @RequestBody CriarAssinaturaRequest request
    ) {
        return ResponseEntity.ok(subscriptionAdminService.criarAssinatura(empresaId, request));
    }

    @PutMapping("/{subscriptionId}")
    public ResponseEntity<List<AssinaturaAdminResponse>> editarAssinatura(
            @PathVariable Long empresaId,
            @PathVariable Long subscriptionId,
            @Valid @RequestBody EditarAssinaturaRequest request
    ) {
        return ResponseEntity.ok(subscriptionAdminService.editarAssinatura(empresaId, subscriptionId, request));
    }
}

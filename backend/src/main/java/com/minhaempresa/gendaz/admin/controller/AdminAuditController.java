package com.minhaempresa.gendaz.admin.controller;

import com.minhaempresa.gendaz.admin.entity.AdminAuditEntity;
import com.minhaempresa.gendaz.admin.service.AdminAuditService;
import com.minhaempresa.gendaz.shared.security.CompanyContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AdminAuditService adminAuditService;

    /**
     * Retorna todos os logs de auditoria da empresa.
     *
     * @return Lista de logs de auditoria.
     */
    @GetMapping
    public ResponseEntity<List<AdminAuditEntity>> getAll() {
        UUID empresaId = CompanyContext.getCompanyId();
        List<AdminAuditEntity> logs = adminAuditService.findByEmpresaIdOrderByDataHoraDesc(empresaId);
        return ResponseEntity.ok(logs);
    }
}
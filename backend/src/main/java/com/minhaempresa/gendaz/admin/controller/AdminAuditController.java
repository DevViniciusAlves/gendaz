package com.minhaempresa.gendaz.admin.controller;

import com.minhaempresa.gendaz.admin.entity.AdminAuditEntity;
import com.minhaempresa.gendaz.admin.service.AdminAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AdminAuditService adminAuditService;

    /**
     * Retorna todos os logs de auditoria (Super Admin tem visao global).
     *
     * @return Lista de logs de auditoria.
     */
    @GetMapping
    public ResponseEntity<List<AdminAuditEntity>> getAll() {
        List<AdminAuditEntity> logs = adminAuditService.listarEntidades();
        return ResponseEntity.ok(logs);
    }
}
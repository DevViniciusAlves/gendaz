package com.minhaempresa.gendaz.admin.repository;

import com.minhaempresa.gendaz.admin.entity.AdminAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminAuditRepository extends JpaRepository<AdminAuditEntity, Long> {
    
    // Método para buscar logs por empresaId (multi-tenant)
    List<AdminAuditEntity> findByEmpresaIdOrderByDataHoraDesc(Long empresaId);
}
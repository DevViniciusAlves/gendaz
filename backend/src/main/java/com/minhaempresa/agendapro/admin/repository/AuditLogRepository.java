package com.minhaempresa.agendapro.admin.repository;

import com.minhaempresa.agendapro.admin.entity.AuditLogEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    List<AuditLogEntity> findTop200ByOrderByDataCriacaoDesc();
    List<AuditLogEntity> findByTipoContainingIgnoreCaseAndDataCriacaoBetweenOrderByDataCriacaoDesc(String tipo, LocalDateTime inicio, LocalDateTime fim);
}

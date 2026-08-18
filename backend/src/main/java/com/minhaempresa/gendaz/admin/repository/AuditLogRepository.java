package com.minhaempresa.gendaz.admin.repository;

import com.minhaempresa.gendaz.admin.entity.AuditLogEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    List<AuditLogEntity> findByEmpresaIdOrderByDataCriacaoDesc(Long empresaId);
    List<AuditLogEntity> findByTipoContainingIgnoreCaseAndDataCriacaoBetweenAndEmpresaIdOrderByDataCriacaoDesc(String tipo, LocalDateTime inicio, LocalDateTime fim, Long empresaId);
    List<AuditLogEntity> findTop200ByOrderByDataCriacaoDesc();

    @Modifying
    @Query("delete from AuditLogEntity a where a.dataCriacao < :limite")
    int deleteBefore(@Param("limite") LocalDateTime limite);

    @Modifying
    @Query("update AuditLogEntity a set a.usuario = null where a.usuario.id = :usuarioId")
    void desvincularUsuario(@Param("usuarioId") Long usuarioId);

    @Modifying
    @Query("update AuditLogEntity a set a.admin = null where a.admin.id = :usuarioId")
    void desvincularAdmin(@Param("usuarioId") Long usuarioId);
}


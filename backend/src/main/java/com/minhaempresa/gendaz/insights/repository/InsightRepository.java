package com.minhaempresa.gendaz.insights.repository;

import com.minhaempresa.gendaz.insights.entity.InsightEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InsightRepository extends JpaRepository<InsightEntity, Long> {
    List<InsightEntity> findByEmpresaIdOrderByDataCriacaoDesc(Long empresaId);
    List<InsightEntity> findTop20ByEmpresaIdAndTipoOrderByDataCriacaoDesc(Long empresaId, String tipo);
    Optional<InsightEntity> findFirstByEmpresaIdAndTipoOrderByDataCriacaoDesc(Long empresaId, String tipo);
    long countByEmpresaIdAndTipo(Long empresaId, String tipo);

    @Modifying
    @Query("delete from InsightEntity i where i.dataExpiracao is not null and i.dataExpiracao < :limite")
    int deleteExpiredBefore(@Param("limite") LocalDateTime limite);
}


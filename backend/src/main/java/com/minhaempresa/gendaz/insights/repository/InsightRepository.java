package com.minhaempresa.gendaz.insights.repository;

import com.minhaempresa.gendaz.insights.entity.InsightEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InsightRepository extends JpaRepository<InsightEntity, Long> {
    List<InsightEntity> findByEmpresaIdOrderByDataCriacaoDesc(Long empresaId);
    List<InsightEntity> findTop20ByEmpresaIdAndTipoOrderByDataCriacaoDesc(Long empresaId, String tipo);
    Optional<InsightEntity> findFirstByEmpresaIdAndTipoOrderByDataCriacaoDesc(Long empresaId, String tipo);
    long countByEmpresaIdAndTipo(Long empresaId, String tipo);
}


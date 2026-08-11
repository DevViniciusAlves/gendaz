package com.minhaempresa.gendaz.insights.repository;

import com.minhaempresa.gendaz.insights.entity.InsightEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InsightRepository extends JpaRepository<InsightEntity, Long> {
    List<InsightEntity> findByEmpresaIdOrderByDataCriacaoDesc(Long empresaId);
    List<InsightEntity> findTop20ByEmpresaIdAndTipoOrderByDataCriacaoDesc(Long empresaId, String tipo);
}


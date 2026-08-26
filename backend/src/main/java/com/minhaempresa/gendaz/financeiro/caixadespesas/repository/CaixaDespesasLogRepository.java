package com.minhaempresa.gendaz.financeiro.caixadespesas.repository;

import com.minhaempresa.gendaz.financeiro.caixadespesas.entity.CaixaDespesasLogEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CaixaDespesasLogRepository extends JpaRepository<CaixaDespesasLogEntity, Long> {
    org.springframework.data.domain.Page<CaixaDespesasLogEntity> findByBusinessIdOrderByCriadoEmDesc(
            Long businessId, org.springframework.data.domain.Pageable pageable);

    Optional<CaixaDespesasLogEntity> findByIdAndBusinessId(Long id, Long businessId);
}

package com.minhaempresa.agendapro.promocao.repository;

import com.minhaempresa.agendapro.promocao.entity.PromocaoUsoEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromocaoUsoRepository extends JpaRepository<PromocaoUsoEntity, Long> {
    long countByPromocaoId(Long promocaoId);
    long countDistinctClienteIdByPromocaoId(Long promocaoId);
    List<PromocaoUsoEntity> findByPromocaoIdOrderByDataUsoDesc(Long promocaoId);
}

package com.minhaempresa.gendaz.assinatura.repository;

import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssinaturaRepository extends JpaRepository<AssinaturaEntity, Long> {
    List<AssinaturaEntity> findByEmpresaId(Long empresaId);
    Optional<AssinaturaEntity> findFirstByEmpresaIdOrderByDataInicioDesc(Long empresaId);
    Optional<AssinaturaEntity> findFirstByEmpresaIdOrderByIdDesc(Long empresaId);

    @EntityGraph(attributePaths = {"empresa", "plano"})
    @Query("SELECT a FROM AssinaturaEntity a")
    List<AssinaturaEntity> findAllComPlano();

    @Query("SELECT DISTINCT a.empresa.id FROM AssinaturaEntity a "
            + "WHERE a.status IN :statuses "
            + "AND a.dataFim IS NOT NULL "
            + "AND a.dataFim <= :hoje")
    List<Long> findEmpresasComAssinaturaVencida(@Param("statuses") List<StatusAssinatura> statuses, @Param("hoje") LocalDate hoje);
}


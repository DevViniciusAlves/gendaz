package com.minhaempresa.gendaz.assinatura.repository;

import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
<<<<<<< HEAD
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
=======
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
>>>>>>> origin/stage

public interface AssinaturaRepository extends JpaRepository<AssinaturaEntity, Long> {
    List<AssinaturaEntity> findByEmpresaId(Long empresaId);
    Optional<AssinaturaEntity> findFirstByEmpresaIdOrderByDataInicioDesc(Long empresaId);
    Optional<AssinaturaEntity> findFirstByEmpresaIdOrderByIdDesc(Long empresaId);
<<<<<<< HEAD
=======

    @Query("SELECT DISTINCT a.empresa.id FROM AssinaturaEntity a "
            + "WHERE a.status IN :statuses "
            + "AND a.dataFim IS NOT NULL "
            + "AND a.dataFim <= :hoje")
    List<Long> findEmpresasComAssinaturaVencida(@Param("statuses") List<StatusAssinatura> statuses, @Param("hoje") LocalDate hoje);
>>>>>>> origin/stage
}


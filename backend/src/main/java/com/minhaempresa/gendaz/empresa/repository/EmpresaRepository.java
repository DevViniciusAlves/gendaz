package com.minhaempresa.gendaz.empresa.repository;

import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmpresaRepository extends JpaRepository<EmpresaEntity, Long> {
    boolean existsByDocumento(String documento);

    boolean existsByTelefone(String telefone);

    @Query("select e from EmpresaEntity e where lower(trim(e.nomeFantasia)) = lower(trim(:nomeFantasia))")
    Optional<EmpresaEntity> findByNomeFantasiaNormalizado(@Param("nomeFantasia") String nomeFantasia);

    Optional<EmpresaEntity> findByAgendamentoSlug(String agendamentoSlug);

    boolean existsByAgendamentoSlug(String agendamentoSlug);
}


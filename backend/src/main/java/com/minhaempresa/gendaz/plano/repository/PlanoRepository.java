package com.minhaempresa.gendaz.plano.repository;

import com.minhaempresa.gendaz.plano.entity.PlanoEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanoRepository extends JpaRepository<PlanoEntity, Long> {
    Optional<PlanoEntity> findByNome(String nome);
}


package com.minhaempresa.gendaz.assinatura.repository;

import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssinaturaRepository extends JpaRepository<AssinaturaEntity, Long> {
    List<AssinaturaEntity> findByEmpresaId(Long empresaId);
    Optional<AssinaturaEntity> findFirstByEmpresaIdOrderByDataInicioDesc(Long empresaId);
    Optional<AssinaturaEntity> findFirstByEmpresaIdOrderByIdDesc(Long empresaId);
}


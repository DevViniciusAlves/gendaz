package com.minhaempresa.agendapro.promocao.repository;

import com.minhaempresa.agendapro.promocao.entity.PromocaoEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface PromocaoRepository extends JpaRepository<PromocaoEntity, Long> {
    @EntityGraph(attributePaths = {"empresa", "servicos"})
    List<PromocaoEntity> findByEmpresaIdOrderByDataCriacaoDesc(Long empresaId);

    @EntityGraph(attributePaths = {"empresa", "servicos"})
    Optional<PromocaoEntity> findByIdAndEmpresaId(Long id, Long empresaId);

    boolean existsByEmpresaIdAndCodigoIgnoreCase(Long empresaId, String codigo);
}

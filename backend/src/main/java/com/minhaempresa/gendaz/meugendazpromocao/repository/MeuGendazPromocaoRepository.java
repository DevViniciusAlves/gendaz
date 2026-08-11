package com.minhaempresa.gendaz.meugendazpromocao.repository;

import com.minhaempresa.gendaz.meugendazpromocao.entity.MeuGendazPromocaoEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeuGendazPromocaoRepository extends JpaRepository<MeuGendazPromocaoEntity, Long> {
    @EntityGraph(attributePaths = {"servicos"})
    List<MeuGendazPromocaoEntity> findByEmpresaIdOrderByDataCriacaoDesc(Long empresaId);

    @EntityGraph(attributePaths = {"servicos"})
    List<MeuGendazPromocaoEntity> findByEmpresaIdAndStatusOrderByDataCriacaoDesc(Long empresaId, com.minhaempresa.gendaz.shared.enums.StatusCadastro status);

    @EntityGraph(attributePaths = {"servicos"})
    Optional<MeuGendazPromocaoEntity> findByEmpresaIdAndCodigoIgnoreCase(Long empresaId, String codigo);

    @EntityGraph(attributePaths = {"servicos"})
    Optional<MeuGendazPromocaoEntity> findByEmpresaIdAndPromocaoOrigemId(Long empresaId, Long promocaoOrigemId);
}


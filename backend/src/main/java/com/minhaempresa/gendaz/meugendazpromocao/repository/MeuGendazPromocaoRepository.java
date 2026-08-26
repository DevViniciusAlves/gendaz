package com.minhaempresa.gendaz.meugendazpromocao.repository;

import com.minhaempresa.gendaz.meugendazpromocao.entity.MeuGendazPromocaoEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeuGendazPromocaoRepository extends JpaRepository<MeuGendazPromocaoEntity, Long> {
    @EntityGraph(attributePaths = {"servicos"})
    List<MeuGendazPromocaoEntity> findByEmpresaIdOrderByDataCriacaoDesc(Long empresaId);

    @EntityGraph(attributePaths = {"servicos"})
    List<MeuGendazPromocaoEntity> findByEmpresaIdAndStatusOrderByDataCriacaoDesc(Long empresaId, com.minhaempresa.gendaz.shared.enums.StatusCadastro status);

    @EntityGraph(attributePaths = {"servicos"})
    Optional<MeuGendazPromocaoEntity> findByEmpresaIdAndCodigoIgnoreCase(Long empresaId, String codigo);

@EntityGraph(attributePaths = {"servicos"})
    Optional<MeuGendazPromocaoEntity> findByEmpresaIdAndPromocaoOrigemId(Long empresaId, Long promocaoOrigemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from MeuGendazPromocaoEntity p where p.id = :id")
    Optional<MeuGendazPromocaoEntity> findByIdComLock(@Param("id") Long id);
}


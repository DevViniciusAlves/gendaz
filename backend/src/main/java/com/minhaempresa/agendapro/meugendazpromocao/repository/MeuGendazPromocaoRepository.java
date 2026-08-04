package com.minhaempresa.agendapro.meugendazpromocao.repository;

import com.minhaempresa.agendapro.meugendazpromocao.entity.MeuGendazPromocaoEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeuGendazPromocaoRepository extends JpaRepository<MeuGendazPromocaoEntity, Long> {
    List<MeuGendazPromocaoEntity> findByEmpresaIdOrderByDataCriacaoDesc(Long empresaId);
    List<MeuGendazPromocaoEntity> findByEmpresaIdAndStatusOrderByDataCriacaoDesc(Long empresaId, com.minhaempresa.agendapro.shared.enums.StatusCadastro status);
    Optional<MeuGendazPromocaoEntity> findByEmpresaIdAndCodigoIgnoreCase(Long empresaId, String codigo);
    Optional<MeuGendazPromocaoEntity> findByEmpresaIdAndPromocaoOrigemId(Long empresaId, Long promocaoOrigemId);
}

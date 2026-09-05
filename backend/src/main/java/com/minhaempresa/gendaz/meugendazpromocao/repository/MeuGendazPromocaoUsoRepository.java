package com.minhaempresa.gendaz.meugendazpromocao.repository;

import com.minhaempresa.gendaz.meugendazpromocao.entity.MeuGendazPromocaoUsoEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeuGendazPromocaoUsoRepository extends JpaRepository<MeuGendazPromocaoUsoEntity, Long> {
    boolean existsByPromocaoIdAndClienteId(Long promocaoId, Long clienteId);
    List<MeuGendazPromocaoUsoEntity> findByClienteIdOrderByDataUsoDesc(Long clienteId);
    long countByPromocaoId(Long promocaoId);
    long countDistinctClienteIdByPromocaoId(Long promocaoId);
    List<MeuGendazPromocaoUsoEntity> findByPromocaoIdOrderByDataUsoDesc(Long promocaoId);
}


package com.minhaempresa.gendaz.promocao.repository;

import com.minhaempresa.gendaz.promocao.entity.PromocaoNotificacaoEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromocaoNotificacaoRepository extends JpaRepository<PromocaoNotificacaoEntity, Long> {
    List<PromocaoNotificacaoEntity> findByPromocaoIdOrderByIdDesc(Long promocaoId);
    long countByPromocaoIdAndStatus(Long promocaoId, String status);
    void deleteByClienteId(Long clienteId);
}


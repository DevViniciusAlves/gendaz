package com.minhaempresa.agendapro.entrega.repository;

import com.minhaempresa.agendapro.entrega.entity.EntregaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntregaRepository extends JpaRepository<EntregaEntity, Long> {
    List<EntregaEntity> findByEmpresaId(Long empresaId);

    void deleteByClienteId(Long clienteId);
}

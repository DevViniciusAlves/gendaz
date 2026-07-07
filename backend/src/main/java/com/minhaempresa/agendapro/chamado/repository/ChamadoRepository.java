package com.minhaempresa.agendapro.chamado.repository;

import com.minhaempresa.agendapro.chamado.entity.ChamadoEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChamadoRepository extends JpaRepository<ChamadoEntity, Long> {
    List<ChamadoEntity> findByEmpresaIdOrderByDataCriacaoDesc(Long empresaId);
    List<ChamadoEntity> findAllByOrderByDataCriacaoDesc();
}

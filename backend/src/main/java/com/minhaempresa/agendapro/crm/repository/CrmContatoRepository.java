package com.minhaempresa.agendapro.crm.repository;

import com.minhaempresa.agendapro.crm.entity.CrmContatoEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface CrmContatoRepository extends JpaRepository<CrmContatoEntity, Long> {
    List<CrmContatoEntity> findByClienteIdOrderByDataCriacaoDesc(Long clienteId);
    long countByClienteIdAndTemplate(Long clienteId, String template);
    Optional<CrmContatoEntity> findFirstByClienteIdOrderByDataCriacaoDesc(Long clienteId);
    List<CrmContatoEntity> findByClienteIdAndTemplateOrderByDataCriacaoDesc(Long clienteId, String template);

    @Transactional
    @Modifying
    void deleteByClienteId(Long clienteId);
}

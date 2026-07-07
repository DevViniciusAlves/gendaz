package com.minhaempresa.agendapro.conversa.repository;

import com.minhaempresa.agendapro.conversa.entity.ConversaEntity;
import com.minhaempresa.agendapro.conversa.enums.StatusConversa;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface ConversaRepository extends JpaRepository<ConversaEntity, Long> {
    @EntityGraph(attributePaths = {"cliente", "empresa"})
    List<ConversaEntity> findByEmpresaIdOrderByDataUltimaMensagemDesc(Long empresaId);

    @EntityGraph(attributePaths = {"cliente", "empresa"})
    List<ConversaEntity> findByClienteId(Long clienteId);

    @Query("""
            select count(c) from ConversaEntity c
            where c.empresa.id = :empresaId
              and c.status = com.minhaempresa.agendapro.conversa.enums.StatusConversa.ABERTA
            """)
    long countAbertasByEmpresaId(Long empresaId);

    @Query("""
            select count(c) from ConversaEntity c
            where c.empresa.id = :empresaId
              and c.status = :status
            """)
    long countByEmpresaIdAndStatus(Long empresaId, StatusConversa status);

    @Transactional
    @Modifying
    void deleteByClienteId(Long clienteId);
}

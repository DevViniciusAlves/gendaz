package com.minhaempresa.agendapro.cliente.repository;

import com.minhaempresa.agendapro.cliente.entity.ClienteEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface ClienteRepository extends JpaRepository<ClienteEntity, Long> {
    @EntityGraph(attributePaths = {"empresa"})
    List<ClienteEntity> findByEmpresaId(Long empresaId);
    long countByEmpresaId(Long empresaId);
    @EntityGraph(attributePaths = {"empresa"})
    Optional<ClienteEntity> findFirstByTelefone(String telefone);
    @EntityGraph(attributePaths = {"empresa"})
    Optional<ClienteEntity> findFirstByEmpresaIdAndTelefone(Long empresaId, String telefone);
}

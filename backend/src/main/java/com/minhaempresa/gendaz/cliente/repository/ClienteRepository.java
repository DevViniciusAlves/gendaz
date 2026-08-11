package com.minhaempresa.gendaz.cliente.repository;

import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
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
    @EntityGraph(attributePaths = {"empresa"})
    Optional<ClienteEntity> findFirstByEmpresaIdAndEmail(Long empresaId, String email);
    @EntityGraph(attributePaths = {"empresa"})
    Optional<ClienteEntity> findFirstByEmpresaIdAndEmailIgnoreCase(Long empresaId, String email);
    boolean existsByEmpresaIdAndTelefoneAndIdNot(Long empresaId, String telefone, Long id);
    boolean existsByEmpresaIdAndEmailAndIdNot(Long empresaId, String email, Long id);
    boolean existsByEmpresaIdAndTelefone(Long empresaId, String telefone);
    boolean existsByEmpresaIdAndEmail(Long empresaId, String email);
}


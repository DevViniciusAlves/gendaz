package com.minhaempresa.gendaz.cliente.repository;

import com.minhaempresa.gendaz.cliente.entity.ClienteEmailBloqueadoEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteEmailBloqueadoRepository extends JpaRepository<ClienteEmailBloqueadoEntity, Long> {
    boolean existsByEmpresaIdAndEmailIgnoreCase(Long empresaId, String email);
    Optional<ClienteEmailBloqueadoEntity> findFirstByEmpresaIdAndEmailIgnoreCase(Long empresaId, String email);
    long deleteByEmpresaIdAndEmailIgnoreCase(Long empresaId, String email);
}


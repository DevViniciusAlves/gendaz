package com.minhaempresa.gendaz.notafiscal.repository;

import com.minhaempresa.gendaz.notafiscal.entity.NotaFiscalEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotaFiscalRepository extends JpaRepository<NotaFiscalEntity, Long> {
    List<NotaFiscalEntity> findByEmpresaId(Long empresaId);

    void deleteByClienteId(Long clienteId);
}


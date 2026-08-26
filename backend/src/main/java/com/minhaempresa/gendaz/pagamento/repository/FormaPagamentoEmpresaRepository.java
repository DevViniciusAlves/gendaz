package com.minhaempresa.gendaz.pagamento.repository;

import com.minhaempresa.gendaz.pagamento.entity.FormaPagamentoEmpresaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormaPagamentoEmpresaRepository extends JpaRepository<FormaPagamentoEmpresaEntity, Long> {
    Optional<FormaPagamentoEmpresaEntity> findByEmpresaId(Long empresaId);
}

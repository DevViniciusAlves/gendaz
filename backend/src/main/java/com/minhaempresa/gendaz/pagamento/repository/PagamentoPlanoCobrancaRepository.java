package com.minhaempresa.gendaz.pagamento.repository;

import com.minhaempresa.gendaz.pagamento.entity.PagamentoPlanoCobrancaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PagamentoPlanoCobrancaRepository extends JpaRepository<PagamentoPlanoCobrancaEntity, Long> {
    boolean existsByStripeInvoiceId(String stripeInvoiceId);
    Optional<PagamentoPlanoCobrancaEntity> findByStripeInvoiceId(String stripeInvoiceId);
}
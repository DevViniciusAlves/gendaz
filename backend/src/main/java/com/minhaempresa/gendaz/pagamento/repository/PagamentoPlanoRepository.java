package com.minhaempresa.gendaz.pagamento.repository;

import com.minhaempresa.gendaz.pagamento.entity.PagamentoPlanoEntity;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface PagamentoPlanoRepository extends JpaRepository<PagamentoPlanoEntity, Long> {
    @EntityGraph(attributePaths = {"empresa", "plano", "assinatura"})
    List<PagamentoPlanoEntity> findByEmpresaIdOrderByDataCriacaoDesc(Long empresaId);
    @EntityGraph(attributePaths = {"empresa", "plano", "assinatura"})
    List<PagamentoPlanoEntity> findByEmpresaIdAndStatusOrderByDataCriacaoDesc(Long empresaId, StatusPagamento status);
    @EntityGraph(attributePaths = {"empresa", "plano", "assinatura"})
    Optional<PagamentoPlanoEntity> findByIdAndEmpresaId(Long id, Long empresaId);
    Optional<PagamentoPlanoEntity> findByProviderPaymentId(String providerPaymentId);
    Optional<PagamentoPlanoEntity> findByExternalReference(String externalReference);
    Optional<PagamentoPlanoEntity> findByPaymentReference(String paymentReference);
    Optional<PagamentoPlanoEntity> findByStripeSessionId(String stripeSessionId);
    Optional<PagamentoPlanoEntity> findBySubscriptionId(String subscriptionId);
    @EntityGraph(attributePaths = {"empresa", "plano", "assinatura"})
    Optional<PagamentoPlanoEntity> findFirstByEmpresaIdAndStatusOrderByDataCriacaoDesc(Long empresaId, StatusPagamento status);
    @EntityGraph(attributePaths = {"empresa", "plano", "assinatura"})
    Optional<PagamentoPlanoEntity> findFirstByEmpresa_EmailIgnoreCaseAndPlano_NomeOrderByDataCriacaoDesc(String email, String planoNome);
    @EntityGraph(attributePaths = {"empresa", "plano", "assinatura"})
    Optional<PagamentoPlanoEntity> findFirstByEmpresa_EmailIgnoreCaseAndStatusOrderByDataCriacaoDesc(String email, StatusPagamento status);
    @EntityGraph(attributePaths = {"empresa", "plano", "assinatura"})
    List<PagamentoPlanoEntity> findByAssinaturaId(Long assinaturaId);
}


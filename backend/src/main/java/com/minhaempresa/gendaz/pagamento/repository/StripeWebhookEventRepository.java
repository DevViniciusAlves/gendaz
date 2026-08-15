package com.minhaempresa.gendaz.pagamento.repository;

import com.minhaempresa.gendaz.pagamento.entity.StripeWebhookEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StripeWebhookEventRepository extends JpaRepository<StripeWebhookEventEntity, Long> {
    boolean existsByEventId(String eventId);
    Optional<StripeWebhookEventEntity> findByEventId(String eventId);
}
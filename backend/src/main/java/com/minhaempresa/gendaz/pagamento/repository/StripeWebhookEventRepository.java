package com.minhaempresa.gendaz.pagamento.repository;

import com.minhaempresa.gendaz.pagamento.entity.StripeWebhookEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StripeWebhookEventRepository extends JpaRepository<StripeWebhookEventEntity, Long> {
    @Modifying
    @Query(value = """
            INSERT INTO stripe_webhook_events (
                event_id,
                event_type,
                object_id,
                deduplication_key,
                processed_at
            ) VALUES (
                :eventId,
                :eventType,
                :objectId,
                :deduplicationKey,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int reservarEvento(
            @Param("eventId") String eventId,
            @Param("eventType") String eventType,
            @Param("objectId") String objectId,
            @Param("deduplicationKey") String deduplicationKey
    );
}

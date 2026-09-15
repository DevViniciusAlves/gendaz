package com.minhaempresa.gendaz.whatsapp.repository;

import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppEntregaReceiptEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WhatsAppEntregaReceiptRepository extends JpaRepository<WhatsAppEntregaReceiptEntity, Long> {

    Optional<WhatsAppEntregaReceiptEntity> findByEmpresaIdAndProviderMessageId(
            Long empresaId, String providerMessageId);

    /**
     * Lock pessimista para o upsert idempotente do receipt: callbacks
     * simultaneos para o mesmo par serializam e o segundo vira no-op.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r
            from WhatsAppEntregaReceiptEntity r
            where r.empresa.id = :empresaId
              and r.providerMessageId = :providerMessageId
            """)
    Optional<WhatsAppEntregaReceiptEntity> findByEmpresaIdAndProviderMessageIdForUpdate(
            @Param("empresaId") Long empresaId,
            @Param("providerMessageId") String providerMessageId);
}

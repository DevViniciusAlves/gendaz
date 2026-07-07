package com.minhaempresa.agendapro.whatsapp.repository;

import com.minhaempresa.agendapro.whatsapp.entity.WhatsappMessageEntity;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsappMessageRepository extends JpaRepository<WhatsappMessageEntity, Long> {
    boolean existsByProviderMessageId(String providerMessageId);

    Page<WhatsappMessageEntity> findByCreatedAtBeforeOrderByCreatedAtAsc(LocalDateTime createdAt, Pageable pageable);
}

/*
  ╔══════════════════════════════════════════════╗
  ║  ⚠️  DESATIVADO - FUNCIONALIDADE WhatsApp    ║
  ║  Todo código comentado. Remova comentários   ║
  ║  para reativar.                              ║
  ╚══════════════════════════════════════════════╝
*/
package com.minhaempresa.agendapro.whatsapp.repository;

import com.minhaempresa.agendapro.whatsapp.entity.WhatsappMessageEntity;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsappMessageRepository extends JpaRepository<WhatsappMessageEntity, Long> {
    // ⚠️ DESATIVADO
    /*
    boolean existsByProviderMessageId(String providerMessageId);

    Page<WhatsappMessageEntity> findByCreatedAtBeforeOrderByCreatedAtAsc(LocalDateTime createdAt, Pageable pageable);
    */
    default boolean existsByProviderMessageId(String providerMessageId) { return false; }
    default Page<WhatsappMessageEntity> findByCreatedAtBeforeOrderByCreatedAtAsc(LocalDateTime createdAt, Pageable pageable) { return Page.empty(); }
}

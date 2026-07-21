/*
  ╔══════════════════════════════════════════════╗
  ║    DESATIVADO - FUNCIONALIDADE WhatsApp    ║
  ║  Todo código comentado. Remova comentários   ║
  ║  para reativar.                              ║
  ╚══════════════════════════════════════════════╝
*/
package com.minhaempresa.agendapro.whatsapp.repository;

import com.minhaempresa.agendapro.whatsapp.entity.WhatsappConversationEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsappConversationRepository extends JpaRepository<WhatsappConversationEntity, Long> {
    //  DESATIVADO
    /*
    Optional<WhatsappConversationEntity> findByEmpresaIdAndContactPhone(Long empresaId, String contactPhone);
    */
    default Optional<WhatsappConversationEntity> findByEmpresaIdAndContactPhone(Long empresaId, String contactPhone) { return Optional.empty(); }
}

/*
  ╔══════════════════════════════════════════════╗
  ║    DESATIVADO - FUNCIONALIDADE WhatsApp    ║
  ║  Todo código comentado. Remova comentários   ║
  ║  para reativar.                              ║
  ╚══════════════════════════════════════════════╝
*/
package com.minhaempresa.agendapro.whatsapp.repository;

import com.minhaempresa.agendapro.whatsapp.entity.WhatsappConnectionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsappConnectionRepository extends JpaRepository<WhatsappConnectionEntity, Long> {
    //  DESATIVADO
    /*
    Optional<WhatsappConnectionEntity> findByEmpresaId(Long empresaId);
    Optional<WhatsappConnectionEntity> findByPhoneNumberId(String phoneNumberId);
    */
    default Optional<WhatsappConnectionEntity> findByEmpresaId(Long empresaId) { return Optional.empty(); }
    default Optional<WhatsappConnectionEntity> findByPhoneNumberId(String phoneNumberId) { return Optional.empty(); }
}

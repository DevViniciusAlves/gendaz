/*
  ╔══════════════════════════════════════════════╗
  ║    DESATIVADO - FUNCIONALIDADE WhatsApp    ║
  ║  Todo código comentado. Remova comentários   ║
  ║  para reativar.                              ║
  ╚══════════════════════════════════════════════╝
*/
package com.minhaempresa.agendapro.whatsapp.repository;

import com.minhaempresa.agendapro.whatsapp.entity.WhatsappSessionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsappSessionRepository extends JpaRepository<WhatsappSessionEntity, Long> {
    //  DESATIVADO
    /*
    Optional<WhatsappSessionEntity> findByEmpresa_Id(Long empresaId);
    List<WhatsappSessionEntity> findAllByOrderByUpdatedAtDesc();
    void deleteByEmpresa_Id(Long empresaId);
    */
    default Optional<WhatsappSessionEntity> findByEmpresa_Id(Long empresaId) { return Optional.empty(); }
    default List<WhatsappSessionEntity> findAllByOrderByUpdatedAtDesc() { return List.of(); }
    default void deleteByEmpresa_Id(Long empresaId) { }
}

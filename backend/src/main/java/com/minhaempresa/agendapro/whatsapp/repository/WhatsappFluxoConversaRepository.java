/*
  ╔══════════════════════════════════════════════╗
  ║  ⚠️  DESATIVADO - FUNCIONALIDADE WhatsApp    ║
  ║  Todo código comentado. Remova comentários   ║
  ║  para reativar.                              ║
  ╚══════════════════════════════════════════════╝
*/
package com.minhaempresa.agendapro.whatsapp.repository;

import com.minhaempresa.agendapro.whatsapp.entity.WhatsappFluxoConversaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsappFluxoConversaRepository extends JpaRepository<WhatsappFluxoConversaEntity, Long> {
    // ⚠️ DESATIVADO
    /*
    Optional<WhatsappFluxoConversaEntity> findByEmpresa_IdAndTelefoneCliente(Long empresaId, String telefoneCliente);

    Optional<WhatsappFluxoConversaEntity> findByEmpresa_IdAndTelefoneClienteAndRemoteJid(Long empresaId, String telefoneCliente, String remoteJid);

    void deleteAllByEmpresa_IdAndTelefoneCliente(Long empresaId, String telefoneCliente);
    */
    default Optional<WhatsappFluxoConversaEntity> findByEmpresa_IdAndTelefoneCliente(Long empresaId, String telefoneCliente) { return Optional.empty(); }
    default Optional<WhatsappFluxoConversaEntity> findByEmpresa_IdAndTelefoneClienteAndRemoteJid(Long empresaId, String telefoneCliente, String remoteJid) { return Optional.empty(); }
    default void deleteAllByEmpresa_IdAndTelefoneCliente(Long empresaId, String telefoneCliente) { }
}

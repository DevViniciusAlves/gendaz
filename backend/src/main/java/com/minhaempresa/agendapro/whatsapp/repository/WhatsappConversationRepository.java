package com.minhaempresa.agendapro.whatsapp.repository;

import com.minhaempresa.agendapro.whatsapp.entity.WhatsappConversationEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsappConversationRepository extends JpaRepository<WhatsappConversationEntity, Long> {
    Optional<WhatsappConversationEntity> findByEmpresaIdAndContactPhone(Long empresaId, String contactPhone);
}

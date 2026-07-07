package com.minhaempresa.agendapro.whatsapp.repository;

import com.minhaempresa.agendapro.whatsapp.entity.WhatsappConnectionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsappConnectionRepository extends JpaRepository<WhatsappConnectionEntity, Long> {
    Optional<WhatsappConnectionEntity> findByEmpresaId(Long empresaId);
    Optional<WhatsappConnectionEntity> findByPhoneNumberId(String phoneNumberId);
}

package com.minhaempresa.agendapro.whatsapp.repository;

import com.minhaempresa.agendapro.whatsapp.entity.WhatsappSessionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsappSessionRepository extends JpaRepository<WhatsappSessionEntity, Long> {
    Optional<WhatsappSessionEntity> findByEmpresa_Id(Long empresaId);
    List<WhatsappSessionEntity> findAllByOrderByUpdatedAtDesc();
    void deleteByEmpresa_Id(Long empresaId);
}

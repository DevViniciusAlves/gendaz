package com.minhaempresa.agendapro.whatsapp.repository;

import com.minhaempresa.agendapro.whatsapp.entity.WhatsappLembretePagamentoEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsappLembretePagamentoRepository extends JpaRepository<WhatsappLembretePagamentoEntity, Long> {
    Optional<WhatsappLembretePagamentoEntity> findByAgendamento_Id(Long agendamentoId);

    void deleteByAgendamento_Id(Long agendamentoId);
}

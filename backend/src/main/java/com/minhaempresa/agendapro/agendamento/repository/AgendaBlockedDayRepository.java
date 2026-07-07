package com.minhaempresa.agendapro.agendamento.repository;

import com.minhaempresa.agendapro.agendamento.entity.AgendaBlockedDayEntity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgendaBlockedDayRepository extends JpaRepository<AgendaBlockedDayEntity, Long> {
    List<AgendaBlockedDayEntity> findByEmpresaIdOrderByDataAsc(Long empresaId);
    boolean existsByEmpresaIdAndDataAndProfissionalIsNull(Long empresaId, LocalDate data);
    boolean existsByEmpresaIdAndProfissionalIdAndData(Long empresaId, Long profissionalId, LocalDate data);
}

package com.minhaempresa.gendaz.agendamento.repository;

import com.minhaempresa.gendaz.agendamento.entity.AgendaBlockedDayEntity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgendaBlockedDayRepository extends JpaRepository<AgendaBlockedDayEntity, Long> {
    List<AgendaBlockedDayEntity> findByEmpresaIdOrderByDataAsc(Long empresaId);
    boolean existsByEmpresaIdAndDataAndProfissionalIsNull(Long empresaId, LocalDate data);
    boolean existsByEmpresaIdAndProfissionalIdAndData(Long empresaId, Long profissionalId, LocalDate data);
}


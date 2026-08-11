package com.minhaempresa.gendaz.horarioatendimento.repository;

import com.minhaempresa.gendaz.horarioatendimento.entity.HorarioAtendimentoEntity;
import com.minhaempresa.gendaz.horarioatendimento.enums.DiaSemanaAtendimento;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HorarioAtendimentoRepository extends JpaRepository<HorarioAtendimentoEntity, Long> {
    List<HorarioAtendimentoEntity> findByEmpresaIdOrderByDiaSemanaAsc(Long empresaId);

    Optional<HorarioAtendimentoEntity> findByEmpresaIdAndDiaSemana(Long empresaId, DiaSemanaAtendimento diaSemana);

    void deleteByEmpresaId(Long empresaId);
}


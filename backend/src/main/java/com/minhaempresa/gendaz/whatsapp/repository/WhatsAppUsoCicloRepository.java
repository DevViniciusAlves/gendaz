package com.minhaempresa.gendaz.whatsapp.repository;

import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppUsoCicloEntity;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WhatsAppUsoCicloRepository extends JpaRepository<WhatsAppUsoCicloEntity, Long> {
    Optional<WhatsAppUsoCicloEntity> findByEmpresaIdAndCicloInicio(Long empresaId, LocalDate cicloInicio);

    /**
     * Carga com lock pessimista de escrita para reserva/consumo/liberacao de
     * cota. Serializa writers concorrentes do MESMO registro empresa/ciclo:
     * a segunda transacao so le reservados/enviados DEPOIS que a primeira
     * commita, entao duas reservas nunca ocupam a mesma ultima vaga.
     * Tenant sempre escopado (empresa + ciclo); nunca lock apenas por id.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select u
            from WhatsAppUsoCicloEntity u
            where u.empresa.id = :empresaId
              and u.cicloInicio = :cicloInicio
            """)
    Optional<WhatsAppUsoCicloEntity> findByEmpresaIdAndCicloInicioForUpdate(
            @Param("empresaId") Long empresaId,
            @Param("cicloInicio") LocalDate cicloInicio);
}

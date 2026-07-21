package com.minhaempresa.agendapro.agendamento.repository;

import com.minhaempresa.agendapro.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.agendapro.agendamento.dto.AgendamentoSimplesProjection;
import com.minhaempresa.agendapro.agendamento.enums.StatusAgendamento;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface AgendamentoRepository extends JpaRepository<AgendamentoEntity, Long> {
    interface AgendamentoHorarioProjection {
        LocalTime getHoraInicio();
        LocalTime getHoraFim();
        StatusAgendamento getStatus();
    }

    @EntityGraph(attributePaths = {"cliente", "servico", "profissional", "empresa"})
    List<AgendamentoEntity> findByEmpresaId(Long empresaId);
    @EntityGraph(attributePaths = {"cliente", "servico", "profissional", "empresa"})
    List<AgendamentoEntity> findByEmpresaIdAndData(Long empresaId, LocalDate data);
    @EntityGraph(attributePaths = {"cliente", "servico", "profissional", "empresa"})
    List<AgendamentoEntity> findByClienteId(Long clienteId);
    @EntityGraph(attributePaths = {"cliente", "servico", "profissional", "empresa"})
    List<AgendamentoEntity> findByServicoId(Long servicoId);
    List<AgendamentoHorarioProjection> findByProfissionalIdAndData(Long profissionalId, LocalDate data);
    @Query("""
            select a.horaInicio as horaInicio, a.horaFim as horaFim, a.status as status
            from AgendamentoEntity a
            where a.empresa.id = :empresaId
              and a.data = :data
            """)
    List<AgendamentoHorarioProjection> findByEmpresaIdAndDataHorarios(
            @Param("empresaId") Long empresaId,
            @Param("data") LocalDate data);
    @EntityGraph(attributePaths = {"cliente", "servico", "profissional", "empresa"})
    List<AgendamentoEntity> findTop10ByEmpresaIdOrderByDataDescHoraInicioDesc(Long empresaId);
    @EntityGraph(attributePaths = {"cliente", "servico", "profissional", "empresa"})
    List<AgendamentoEntity> findTop5ByEmpresaIdAndStatusInAndDataGreaterThanEqualOrderByDataAscHoraInicioAsc(
            Long empresaId,
            List<StatusAgendamento> status,
            LocalDate data
    );
    @EntityGraph(attributePaths = {"cliente", "servico", "profissional", "empresa"})
    List<AgendamentoEntity> findByEmpresaIdAndStatusInAndDataGreaterThanEqualOrderByDataAscHoraInicioAsc(
            Long empresaId,
            List<StatusAgendamento> status,
            LocalDate data
    );
    @EntityGraph(attributePaths = {"cliente", "servico", "profissional", "empresa"})
    List<AgendamentoEntity> findByEmpresaIdAndStatusInAndDataOrderByHoraInicioAsc(
            Long empresaId,
            List<StatusAgendamento> status,
            LocalDate data
    );

    @Query("""
            select new com.minhaempresa.agendapro.agendamento.dto.AgendamentoSimplesProjection(
                   a.id,
                   c.nome,
                   c.telefone,
                   s.nome,
                   a.data,
                   a.horaInicio,
                   a.status
            )
            from AgendamentoEntity a
            join a.cliente c
            join a.servico s
            where a.empresa.id = :empresaId
              and a.data = :data
            """)
    List<AgendamentoSimplesProjection> findAgendamentosSimples(
            @Param("empresaId") Long empresaId,
            @Param("data") LocalDate data
    );
    long countByEmpresaIdAndData(Long empresaId, LocalDate data);
    boolean existsByClienteId(Long clienteId);
    boolean existsByServicoId(Long servicoId);
    boolean existsByProfissionalIdAndDataAndHoraInicioAndStatus(Long profissionalId, LocalDate data, LocalTime horaInicio, StatusAgendamento status);
    boolean existsByProtocolo(String protocolo);
    java.util.Optional<AgendamentoEntity> findByEmpresa_IdAndProtocolo(Long empresaId, String protocolo);
    java.util.Optional<AgendamentoEntity> findByProtocolo(String protocolo);

    @Query("""
            select count(a) > 0 from AgendamentoEntity a
            where a.profissional.id = :profissionalId
              and a.data = :data
              and a.status <> :statusCancelado
              and (:ignorarId is null or a.id <> :ignorarId)
              and :horaInicio < a.horaFim
            and :horaFim > a.horaInicio
            """)
    boolean existeConflitoDeHorario(
            @Param("profissionalId") Long profissionalId,
            @Param("data") LocalDate data,
            @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFim") LocalTime horaFim,
            @Param("statusCancelado") StatusAgendamento statusCancelado,
            @Param("ignorarId") Long ignorarId);

    @Query("""
            select a.servico.id, a.servico.nome, count(a.id), coalesce(sum(a.servico.valor), 0)
            from AgendamentoEntity a
            where a.empresa.id = :empresaId
              and a.status <> :statusCancelado
            group by a.servico.id, a.servico.nome
            order by count(a.id) desc
            """)
    List<Object[]> resumoServicosMaisAgendados(
            @Param("empresaId") Long empresaId,
            @Param("statusCancelado") StatusAgendamento statusCancelado,
            Pageable pageable
    );
}

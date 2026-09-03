package com.minhaempresa.gendaz.agendamento.repository;

import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoSimplesProjection;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.financeiro.dto.FinanceiroDtos.ItemResumoResponse;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
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
    @Query("""
            select a from AgendamentoEntity a
            where a.empresa.id = :empresaId
              and a.cliente.status <> :statusExcluido
            """)
    @EntityGraph(attributePaths = {"cliente", "servico", "profissional", "empresa"})
    List<AgendamentoEntity> findByEmpresaIdOperacional(
            @Param("empresaId") Long empresaId,
            @Param("statusExcluido") StatusCadastro statusExcluido);
    @Query("""
            select a from AgendamentoEntity a
            where a.empresa.id = :empresaId
              and a.data = :data
              and a.cliente.status <> :statusExcluido
            """)
    @EntityGraph(attributePaths = {"cliente", "servico", "profissional", "empresa"})
    List<AgendamentoEntity> findByEmpresaIdAndDataOperacional(
            @Param("empresaId") Long empresaId,
            @Param("data") LocalDate data,
            @Param("statusExcluido") StatusCadastro statusExcluido);
    @EntityGraph(attributePaths = {"cliente", "servico", "profissional", "empresa"})
    List<AgendamentoEntity> findByClienteId(Long clienteId);
    @EntityGraph(attributePaths = {"cliente", "servico", "profissional", "empresa"})
    List<AgendamentoEntity> findByEmpresaIdAndClienteId(Long empresaId, Long clienteId);
    /**
     * Busca de propriedade para fluxos self-service (Meu Gendaz): valida
     * atomicamente agendamento + empresa + cliente. Usada para impedir
     * IDOR/BOLA entre clientes da mesma empresa. Retorna vazio quando o
     * recurso nao pertence ao cliente, sem vazar o proprietario real.
     */
    @EntityGraph(attributePaths = {"cliente", "servico", "profissional", "empresa"})
    java.util.Optional<AgendamentoEntity> findByIdAndEmpresaIdAndClienteId(Long id, Long empresaId, Long clienteId);
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
    List<AgendamentoEntity> findTop10ByEmpresaIdAndClienteStatusNotOrderByDataDescHoraInicioDesc(Long empresaId, StatusCadastro statusExcluido);
    @EntityGraph(attributePaths = {"cliente", "servico", "profissional", "empresa"})
    List<AgendamentoEntity> findTop5ByEmpresaIdAndStatusInAndDataGreaterThanEqualAndClienteStatusNotOrderByDataAscHoraInicioAsc(
            Long empresaId,
            List<StatusAgendamento> status,
            LocalDate data,
            StatusCadastro statusExcluido
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
            select new com.minhaempresa.gendaz.agendamento.dto.AgendamentoSimplesProjection(
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
    long countByEmpresaIdAndDataAndStatusNot(Long empresaId, LocalDate data, StatusAgendamento status);
    long countByEmpresaIdAndDataAndStatusNotAndClienteStatusNot(Long empresaId, LocalDate data, StatusAgendamento status, StatusCadastro statusCliente);
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
            select a.servico.id, a.servico.nome, count(a.id), coalesce(sum(coalesce(a.valorFinal, a.servico.valor)), 0)
            from AgendamentoEntity a
            where a.empresa.id = :empresaId
              and a.status <> :statusCancelado
              and a.cliente.status <> :statusExcluido
            group by a.servico.id, a.servico.nome
            order by count(a.id) desc
            """)
    List<Object[]> resumoServicosMaisAgendados(
            @Param("empresaId") Long empresaId,
            @Param("statusCancelado") StatusAgendamento statusCancelado,
            @Param("statusExcluido") StatusCadastro statusExcluido,
            Pageable pageable
    );

    @Query("""
            select a.profissional.id, a.profissional.nome, count(a.id)
            from AgendamentoEntity a
            where a.empresa.id = :empresaId
              and a.profissional is not null
              and a.status <> :statusCancelado
              and a.cliente.status <> :statusExcluido
            group by a.profissional.id, a.profissional.nome
            order by count(a.id) desc
            """)
    List<Object[]> resumoProfissionaisMaisAgendados(
            @Param("empresaId") Long empresaId,
            @Param("statusCancelado") StatusAgendamento statusCancelado,
            @Param("statusExcluido") StatusCadastro statusExcluido,
            Pageable pageable
    );

    @Query("SELECT COUNT(a) FROM AgendamentoEntity a WHERE a.empresa.id = :empresaId AND a.status = 'FINALIZADO'")
    long countConsultasFinalizadas(@Param("empresaId") Long empresaId);

    @Query("""
        SELECT new com.minhaempresa.gendaz.financeiro.dto.FinanceiroDtos$ItemResumoResponse(
            c.nome,
            COUNT(a.id),
            coalesce(sum(coalesce(a.valorFinal, a.servico.valor)), 0)
        )
        FROM AgendamentoEntity a
        JOIN a.cliente c
        WHERE a.empresa.id = :empresaId
          AND a.status <> :statusCancelado
        GROUP BY c.id, c.nome
        ORDER BY COUNT(a.id) DESC
    """)
    List<ItemResumoResponse> resumoClientesMaisAgendados(
            @Param("empresaId") Long empresaId,
            @Param("statusCancelado") StatusAgendamento statusCancelado,
            Pageable pageable);

    @Query("""
        SELECT new com.minhaempresa.gendaz.financeiro.dto.FinanceiroDtos$ItemResumoResponse(
            s.nome,
            COUNT(a.id),
            coalesce(sum(coalesce(a.valorFinal, a.servico.valor)), 0)
        )
        FROM AgendamentoEntity a
        JOIN a.servico s
        WHERE a.empresa.id = :empresaId
          AND a.status <> :statusCancelado
        GROUP BY s.id, s.nome
        ORDER BY COUNT(a.id) DESC
    """)
    List<ItemResumoResponse> resumoServicosMaisAgendadosFinanceiro(
            @Param("empresaId") Long empresaId,
            @Param("statusCancelado") StatusAgendamento statusCancelado,
            Pageable pageable);
}


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

    interface AgendamentoLembreteProjection {
        Long getId();
        Long getEmpresaId();
        Boolean getEmpresaWhatsappConnected();
        String getClienteNome();
        String getClienteTelefone();
        String getServicoNome();
        String getProfissionalNome();
        java.time.LocalDate getData();
        LocalTime getHoraInicio();
        String getProtocolo();
        Boolean getLembreteWppEnviado();
        StatusAgendamento getStatus();
        Boolean getConfirmacaoPagamentoDonoEnviada();
        java.time.LocalDateTime getConfirmacaoPagamentoDonoEnviadaEm();
        Boolean getSegundaConfirmacaoPagamentoDonoEnviada();
        java.time.LocalDateTime getSegundaConfirmacaoPagamentoDonoEnviadaEm();
        Boolean getConfirmacaoPagamentoDonoRespondida();
        java.time.LocalDateTime getConfirmacaoPagamentoDonoRespondidaEm();
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
            select a.id as id,
                   a.empresa.id as empresaId,
                   a.empresa.whatsappConnected as empresaWhatsappConnected,
                   a.cliente.nome as clienteNome,
                   a.cliente.telefone as clienteTelefone,
                   a.servico.nome as servicoNome,
                   a.profissional.nome as profissionalNome,
                   a.data as data,
                   a.horaInicio as horaInicio,
                   a.protocolo as protocolo,
                   a.lembreteWppEnviado as lembreteWppEnviado,
                   a.status as status,
                   a.confirmacaoPagamentoDonoEnviada as confirmacaoPagamentoDonoEnviada,
                   a.confirmacaoPagamentoDonoEnviadaEm as confirmacaoPagamentoDonoEnviadaEm,
                   a.segundaConfirmacaoPagamentoDonoEnviada as segundaConfirmacaoPagamentoDonoEnviada,
                   a.segundaConfirmacaoPagamentoDonoEnviadaEm as segundaConfirmacaoPagamentoDonoEnviadaEm,
                   a.confirmacaoPagamentoDonoRespondida as confirmacaoPagamentoDonoRespondida,
                   a.confirmacaoPagamentoDonoRespondidaEm as confirmacaoPagamentoDonoRespondidaEm
            from AgendamentoEntity a
            where a.empresa.id = :empresaId
              and a.data = :data
              and a.horaInicio between :horaInicio and :horaFim
              and a.status in :status
              and (a.lembreteWppEnviado = false or a.lembreteWppEnviado is null)
            """)
    List<AgendamentoLembreteProjection> findLembretesClienteProjection(
            @Param("empresaId") Long empresaId,
            @Param("status") List<StatusAgendamento> status,
            @Param("data") LocalDate data,
            @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFim") LocalTime horaFim
    );

    @Query("""
            select a.id as id,
                   a.empresa.id as empresaId,
                   a.empresa.whatsappConnected as empresaWhatsappConnected,
                   a.cliente.nome as clienteNome,
                   a.cliente.telefone as clienteTelefone,
                   a.servico.nome as servicoNome,
                   a.profissional.nome as profissionalNome,
                   a.data as data,
                   a.horaInicio as horaInicio,
                   a.protocolo as protocolo,
                   a.lembreteWppEnviado as lembreteWppEnviado,
                   a.status as status,
                   a.confirmacaoPagamentoDonoEnviada as confirmacaoPagamentoDonoEnviada,
                   a.confirmacaoPagamentoDonoEnviadaEm as confirmacaoPagamentoDonoEnviadaEm,
                   a.segundaConfirmacaoPagamentoDonoEnviada as segundaConfirmacaoPagamentoDonoEnviada,
                   a.segundaConfirmacaoPagamentoDonoEnviadaEm as segundaConfirmacaoPagamentoDonoEnviadaEm,
                   a.confirmacaoPagamentoDonoRespondida as confirmacaoPagamentoDonoRespondida,
                   a.confirmacaoPagamentoDonoRespondidaEm as confirmacaoPagamentoDonoRespondidaEm
            from AgendamentoEntity a
            where a.empresa.id = :empresaId
              and a.data = :data
              and a.status in :status
            order by a.horaInicio asc
            """)
    List<AgendamentoLembreteProjection> findConfirmacoesPagamentoDonoProjection(
            @Param("empresaId") Long empresaId,
            @Param("status") List<StatusAgendamento> status,
            @Param("data") LocalDate data
    );
    @EntityGraph(attributePaths = {"cliente", "servico", "profissional", "empresa"})
    @Query("""
            select a from AgendamentoEntity a
            where a.empresa.id = :empresaId
              and a.data = :data
              and a.horaInicio between :horaInicio and :horaFim
              and a.status in :status
            """)
    List<AgendamentoEntity> findConfirmacaoPagamentoPendente(
            @Param("empresaId") Long empresaId,
            @Param("status") List<StatusAgendamento> status,
            @Param("data") LocalDate data,
            @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFim") LocalTime horaFim
    );

    @EntityGraph(attributePaths = {"cliente", "servico", "profissional", "empresa"})
    List<AgendamentoEntity> findByLembreteWppEnviadoFalseAndStatusInAndDataAndHoraInicioBetween(
            List<StatusAgendamento> status,
            LocalDate data,
            LocalTime horaInicio,
            LocalTime horaFim
    );

    @EntityGraph(attributePaths = {"cliente", "servico", "profissional", "empresa"})
    @Query("""
            select a from AgendamentoEntity a
            where a.data = :data
              and a.horaInicio between :horaInicio and :horaFim
              and a.status in :status
              and (a.lembreteWppEnviado = false or a.lembreteWppEnviado is null)
            """)
    List<AgendamentoEntity> findByLembretePendente(
            @Param("status") List<StatusAgendamento> status,
            @Param("data") LocalDate data,
            @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFim") LocalTime horaFim
    );

    @EntityGraph(attributePaths = {"cliente", "servico", "profissional", "empresa"})
    @Query("""
            select a from AgendamentoEntity a
            where a.data = :data
              and a.horaInicio between :horaInicio and :horaFim
              and a.status in :status
              and (a.lembreteWppEnviado = false or a.lembreteWppEnviado is null)
            """)
    List<AgendamentoEntity> buscarAgendamentosParaLembreteWhatsapp(
            @Param("status") List<StatusAgendamento> status,
            @Param("data") LocalDate data,
            @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFim") LocalTime horaFim
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

package com.minhaempresa.gendaz.pagamento.repository;

import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PagamentoRepository extends JpaRepository<PagamentoEntity, Long> {
    @EntityGraph(attributePaths = {"cliente", "empresa", "agendamento"})
    List<PagamentoEntity> findByEmpresaId(Long empresaId);
    @EntityGraph(attributePaths = {"cliente", "empresa", "agendamento"})
    List<PagamentoEntity> findByEmpresaIdAndDataPagamentoBetween(Long empresaId, LocalDateTime inicio, LocalDateTime fim);
    @EntityGraph(attributePaths = {"cliente", "empresa", "agendamento"})
    List<PagamentoEntity> findByEmpresaIdAndStatus(Long empresaId, StatusPagamento status);
    @EntityGraph(attributePaths = {"cliente", "empresa", "agendamento"})
    List<PagamentoEntity> findTop5ByEmpresaIdAndStatusOrderByIdDesc(Long empresaId, StatusPagamento status);
    @EntityGraph(attributePaths = {"cliente", "empresa", "agendamento"})
    List<PagamentoEntity> findByEmpresaIdAndStatusIn(Long empresaId, List<StatusPagamento> statuses);
    @EntityGraph(attributePaths = {"cliente", "empresa", "agendamento"})
    java.util.Optional<PagamentoEntity> findByAgendamento_Id(Long agendamentoId);

    @Query("""
            select coalesce(sum(p.valor), 0)
            from PagamentoEntity p
            where p.empresa.id = :empresaId
              and p.cliente.id = :clienteId
              and p.status in :statuses
            """)
    BigDecimal somarValorByEmpresaIdAndClienteIdAndStatusIn(
            @Param("empresaId") Long empresaId,
            @Param("clienteId") Long clienteId,
            @Param("statuses") List<StatusPagamento> statuses);

    @Transactional
    @Modifying
    @Query("DELETE FROM PagamentoEntity p WHERE p.agendamento.id = :agendamentoId")
    void deleteByAgendamentoId(@Param("agendamentoId") Long agendamentoId);
    @Transactional
    @Modifying
    @Query("DELETE FROM PagamentoEntity p WHERE p.cliente.id = :clienteId")
    void deleteByClienteId(@Param("clienteId") Long clienteId);
    @Query("""
            select coalesce(sum(p.valor), 0)
            from PagamentoEntity p
            where p.empresa.id = :empresaId
              and p.status in :statuses
            """)
    BigDecimal somarValorByEmpresaIdAndStatusIn(Long empresaId, List<StatusPagamento> statuses);
    @Query("""
            select cast(p.dataPagamento as date), coalesce(sum(p.valor), 0)
            from PagamentoEntity p
            where p.empresa.id = :empresaId
              and p.status in :statuses
              and p.dataPagamento between :inicio and :fim
            group by cast(p.dataPagamento as date)
            order by cast(p.dataPagamento as date)
            """)
    List<Object[]> resumoReceitaPorDia(Long empresaId, List<StatusPagamento> statuses, LocalDateTime inicio, LocalDateTime fim);
    boolean existsByClienteId(Long clienteId);
    boolean existsByAgendamentoId(Long agendamentoId);
    long countByEmpresaIdAndStatus(Long empresaId, StatusPagamento status);
}


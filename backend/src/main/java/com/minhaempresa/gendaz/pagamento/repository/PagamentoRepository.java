package com.minhaempresa.gendaz.pagamento.repository;

import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PagamentoRepository extends JpaRepository<PagamentoEntity, Long> {
    @EntityGraph(attributePaths = {"cliente", "empresa", "agendamento"})
    Optional<PagamentoEntity> findByIdAndEmpresaId(Long id, Long empresaId);

    // Métodos originais que retornam entidades para compatibilidade
    // Sem @EntityGraph para evitar carregar a coluna inexistente clientes.status e quebrar o boot/queries
    List<PagamentoEntity> findByEmpresaId(Long empresaId);
    List<PagamentoEntity> findByEmpresaIdAndDataPagamentoBetween(Long empresaId, LocalDateTime inicio, LocalDateTime fim);
    List<PagamentoEntity> findByEmpresaIdAndStatus(Long empresaId, StatusPagamento status);
    List<PagamentoEntity> findTop5ByEmpresaIdAndStatusOrderByIdDesc(Long empresaId, StatusPagamento status);
    List<PagamentoEntity> findByEmpresaIdAndStatusIn(Long empresaId, List<StatusPagamento> statuses);
    Optional<PagamentoEntity> findByAgendamentoIdAndEmpresaId(Long agendamentoId, Long empresaId);

    @Query("""
        SELECT new com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos$PagamentoResponse(
            p.id,
            p.agendamento.id,
            p.agendamento.protocolo,
            p.agendamento.servico.nome,
            p.cliente.id,
            p.cliente.nome,
            p.empresa.id,
            p.valor,
            p.metodoPagamento,
            p.parcelas,
            p.status,
            p.dataPagamento,
            com.minhaempresa.gendaz.shared.enums.StatusCadastro.ATIVO
        )
        FROM PagamentoEntity p
        WHERE p.empresa.id = :empresaId
        ORDER BY p.dataPagamento DESC
    """)
    List<PagamentoDtos.PagamentoResponse> findByEmpresaIdForFinanceiro(@Param("empresaId") Long empresaId);
    @Query("""
        SELECT new com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos$PagamentoResponse(
            p.id,
            p.agendamento.id,
            p.agendamento.protocolo,
            p.agendamento.servico.nome,
            p.cliente.id,
            p.cliente.nome,
            p.empresa.id,
            p.valor,
            p.metodoPagamento,
            p.parcelas,
            p.status,
            p.dataPagamento,
            com.minhaempresa.gendaz.shared.enums.StatusCadastro.ATIVO
        )
        FROM PagamentoEntity p
        WHERE p.empresa.id = :empresaId
          AND p.dataPagamento BETWEEN :inicio AND :fim
        ORDER BY p.dataPagamento DESC
    """)
    List<PagamentoDtos.PagamentoResponse> findByEmpresaIdAndDataPagamentoBetweenForFinanceiro(@Param("empresaId") Long empresaId, @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("""
        SELECT new com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos$PagamentoResponse(
            p.id,
            p.agendamento.id,
            p.agendamento.protocolo,
            p.agendamento.servico.nome,
            p.cliente.id,
            p.cliente.nome,
            p.empresa.id,
            p.valor,
            p.metodoPagamento,
            p.parcelas,
            p.status,
            p.dataPagamento,
            com.minhaempresa.gendaz.shared.enums.StatusCadastro.ATIVO
        )
        FROM PagamentoEntity p
        WHERE p.empresa.id = :empresaId
          AND p.status = :status
        ORDER BY p.dataPagamento DESC
    """)
    List<PagamentoDtos.PagamentoResponse> findByEmpresaIdAndStatusForFinanceiro(@Param("empresaId") Long empresaId, @Param("status") StatusPagamento status);

    @Query("""
        SELECT new com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos$PagamentoResponse(
            p.id,
            p.agendamento.id,
            p.agendamento.protocolo,
            p.agendamento.servico.nome,
            p.cliente.id,
            p.cliente.nome,
            p.empresa.id,
            p.valor,
            p.metodoPagamento,
            p.parcelas,
            p.status,
            p.dataPagamento,
            com.minhaempresa.gendaz.shared.enums.StatusCadastro.ATIVO
        )
        FROM PagamentoEntity p
        WHERE p.empresa.id = :empresaId
          AND p.status = :status
        ORDER BY p.id DESC
    """)
    List<PagamentoDtos.PagamentoResponse> findTop5ByEmpresaIdAndStatusOrderByIdDescForFinanceiro(@Param("empresaId") Long empresaId, @Param("status") StatusPagamento status);

    @Query("""
        SELECT new com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos$PagamentoResponse(
            p.id,
            p.agendamento.id,
            p.agendamento.protocolo,
            p.agendamento.servico.nome,
            p.cliente.id,
            p.cliente.nome,
            p.empresa.id,
            p.valor,
            p.metodoPagamento,
            p.parcelas,
            p.status,
            p.dataPagamento,
            com.minhaempresa.gendaz.shared.enums.StatusCadastro.ATIVO
        )
        FROM PagamentoEntity p
        WHERE p.empresa.id = :empresaId
          AND p.status IN :statuses
        ORDER BY p.dataPagamento DESC
    """)
    List<PagamentoDtos.PagamentoResponse> findByEmpresaIdAndStatusInForFinanceiro(@Param("empresaId") Long empresaId, @Param("statuses") List<StatusPagamento> statuses);

    @Query("""
        SELECT new com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos$PagamentoResponse(
            p.id,
            p.agendamento.id,
            p.agendamento.protocolo,
            p.agendamento.servico.nome,
            p.cliente.id,
            p.cliente.nome,
            p.empresa.id,
            p.valor,
            p.metodoPagamento,
            p.parcelas,
            p.status,
            p.dataPagamento,
            com.minhaempresa.gendaz.shared.enums.StatusCadastro.ATIVO
        )
        FROM PagamentoEntity p
        WHERE p.agendamento.id = :agendamentoId
          AND p.empresa.id = :empresaId
    """)
    Optional<PagamentoDtos.PagamentoResponse> findByAgendamentoIdAndEmpresaIdForFinanceiro(@Param("agendamentoId") Long agendamentoId, @Param("empresaId") Long empresaId);

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
    @Query("DELETE FROM PagamentoEntity p WHERE p.agendamento.id = :agendamentoId AND p.empresa.id = :empresaId")
    int deleteByAgendamentoIdAndEmpresaId(
            @Param("agendamentoId") Long agendamentoId,
            @Param("empresaId") Long empresaId);
    @Transactional
    @Modifying
    @Query("DELETE FROM PagamentoEntity p WHERE p.cliente.id = :clienteId AND p.empresa.id = :empresaId")
    int deleteByClienteIdAndEmpresaId(
            @Param("clienteId") Long clienteId,
            @Param("empresaId") Long empresaId);
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


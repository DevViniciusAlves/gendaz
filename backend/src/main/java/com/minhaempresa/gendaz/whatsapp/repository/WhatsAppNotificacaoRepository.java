package com.minhaempresa.gendaz.whatsapp.repository;

import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppNotificacaoEntity;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppStatusNotificacao;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppTipoNotificacao;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WhatsAppNotificacaoRepository extends JpaRepository<WhatsAppNotificacaoEntity, Long> {
    /**
     * Leitura escopada por tenant: nunca buscar/alterar notificacao de outra
     * empresa apenas pelo id.
     */
    Optional<WhatsAppNotificacaoEntity> findByIdAndEmpresaId(Long id, Long empresaId);

    /**
     * Carga com lock pessimista de escrita para as decisoes atomicas
     * cancelamento x inicio de envio: quem adquire a linha primeiro decide;
     * o outro reavalia status/sendStartedAt DEPOIS do lock. Tenant sempre
     * escopado; nunca lock apenas por id quando a empresa e conhecida.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select n
            from WhatsAppNotificacaoEntity n
            where n.id = :id
              and n.empresa.id = :empresaId
            """)
    Optional<WhatsAppNotificacaoEntity> findByIdAndEmpresaIdForUpdate(
            @Param("id") Long id,
            @Param("empresaId") Long empresaId);

    /**
     * Variante para o worker, que opera sobre linhas da fila global lidas
     * pelo proprio claim (a empresa e extraida da linha, sem risco de
     * cross-tenant: nunca parte de id arbitrario externo).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select n
            from WhatsAppNotificacaoEntity n
            where n.id = :id
            """)
    Optional<WhatsAppNotificacaoEntity> findByIdForUpdate(@Param("id") Long id);

    Optional<WhatsAppNotificacaoEntity> findByEmpresaIdAndIdempotencyKey(Long empresaId, String idempotencyKey);

    /**
     * Localizacao pelo par tenant + providerMessageId (message.key.id do
     * Baileys) para confirmacao de entrega. Tenant sempre escopado: um ACK
     * da empresa A nunca altera notificacao da empresa B.
     */
    Optional<WhatsAppNotificacaoEntity> findByEmpresaIdAndProviderMessageId(Long empresaId, String providerMessageId);

    /**
     * Variante com lock pessimista para a confirmacao de entrega idempotente:
     * ACKs duplicados/simultaneos serializam na linha e o segundo vira no-op.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select n
            from WhatsAppNotificacaoEntity n
            where n.empresa.id = :empresaId
              and n.providerMessageId = :providerMessageId
            """)
    Optional<WhatsAppNotificacaoEntity> findByEmpresaIdAndProviderMessageIdForUpdate(
            @Param("empresaId") Long empresaId,
            @Param("providerMessageId") String providerMessageId);

    List<WhatsAppNotificacaoEntity> findByEmpresaIdAndAgendamentoId(Long empresaId, Long agendamentoId);

    List<WhatsAppNotificacaoEntity> findByEmpresaIdAndClienteId(Long empresaId, Long clienteId);

    List<WhatsAppNotificacaoEntity> findByEmpresaIdAndStatus(Long empresaId, WhatsAppStatusNotificacao status);

    @Query("select n from WhatsAppNotificacaoEntity n where n.empresa.id = :empresaId and n.status in :statuses")
    List<WhatsAppNotificacaoEntity> findByEmpresaIdAndStatusIn(@Param("empresaId") Long empresaId, @Param("statuses") List<WhatsAppStatusNotificacao> statuses);

    /**
     * Claim transacional da fila: somente um worker obtem cada notificacao
     * (FOR UPDATE SKIP LOCKED funciona no PostgreSQL e no H2 de teste).
     * Apenas PENDENTE vencida (scheduled/next) e com conteudo operacional:
     * registros sem recipient/message nao entram na fila de envio.
     * A transacao de claim termina antes da chamada HTTP externa: o lock
     * nunca e segurado enquanto espera Baileys/HTTP.
     */
    @Query(value = """
            SELECT * FROM whatsapp_notificacoes
            WHERE status = 'PENDENTE'
              AND (scheduled_at IS NULL OR scheduled_at <= :agora)
              AND (next_attempt_at IS NULL OR next_attempt_at <= :agora)
              AND recipient IS NOT NULL
              AND message_body IS NOT NULL
            ORDER BY COALESCE(scheduled_at, next_attempt_at, data_criacao), id
            LIMIT :limite
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<WhatsAppNotificacaoEntity> claimPendentes(
            @Param("agora") LocalDateTime agora,
            @Param("limite") int limite);

    /**
     * Recupera ENVIANDO presos (crash/restart): somente um worker assume
     * cada registro.
     */
    @Query(value = """
            SELECT * FROM whatsapp_notificacoes
            WHERE status = 'ENVIANDO'
              AND processing_started_at IS NOT NULL
              AND processing_started_at < :limite
            ORDER BY processing_started_at, id
            LIMIT :tamanho
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<WhatsAppNotificacaoEntity> claimPresos(
            @Param("limite") LocalDateTime limite,
            @Param("tamanho") int tamanho);

    /**
     * Contadores da UI separados por estado real, sempre no ciclo vigente da
     * quota e somente sobre notificacoes com reserva associada a esse ciclo
     * (quotaReserved + quotaCycleStart). Fonte persistente
     * whatsapp_notificacoes; o campo reservados do ciclo continua sendo a
     * fonte de verdade da cota.
     *
     * Checkpoint de consistencia: em fluxo normal, reservados equivale a
     * naFila (PENDENTE + ENVIANDO) + aguardandoConfirmacao
     * (AGUARDANDO_ENTREGA). Divergencia indica estado excepcional (ex.:
     * reserva orfa de ciclo anterior gravada no ciclo atual) e nao deve ser
     * mascarada aqui: os numeros refletem a fonte persistente como esta.
     */
    @Query("""
            select count(n)
            from WhatsAppNotificacaoEntity n
            where n.empresa.id = :empresaId
              and n.quotaReserved = true
              and n.quotaCycleStart = :cicloInicio
              and n.tipo in :tipos
              and n.status in :statuses
            """)
    long contarComReservaPorCicloTiposStatuses(
            @Param("empresaId") Long empresaId,
            @Param("cicloInicio") LocalDate cicloInicio,
            @Param("tipos") List<WhatsAppTipoNotificacao> tipos,
            @Param("statuses") List<WhatsAppStatusNotificacao> statuses);
}

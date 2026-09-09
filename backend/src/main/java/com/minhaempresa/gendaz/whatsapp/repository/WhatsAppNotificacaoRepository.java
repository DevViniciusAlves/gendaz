package com.minhaempresa.gendaz.whatsapp.repository;

import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppNotificacaoEntity;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppStatusNotificacao;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WhatsAppNotificacaoRepository extends JpaRepository<WhatsAppNotificacaoEntity, Long> {
    /**
     * Leitura escopada por tenant: nunca buscar/alterar notificacao de outra
     * empresa apenas pelo id.
     */
    Optional<WhatsAppNotificacaoEntity> findByIdAndEmpresaId(Long id, Long empresaId);

    Optional<WhatsAppNotificacaoEntity> findByEmpresaIdAndIdempotencyKey(Long empresaId, String idempotencyKey);

    List<WhatsAppNotificacaoEntity> findByEmpresaIdAndAgendamentoId(Long empresaId, Long agendamentoId);

    List<WhatsAppNotificacaoEntity> findByEmpresaIdAndStatus(Long empresaId, WhatsAppStatusNotificacao status);

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
}

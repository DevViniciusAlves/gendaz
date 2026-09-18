package com.minhaempresa.gendaz.whatsapp.entity;

import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * Receipt de entrega do Baileys persistido no backend.
 *
 * <p>Resolve a corrida "callback antes do providerMessageId": o Node executa
 * sendMessage, o Baileys gera o ACK muito rapido e o callback pode chegar ao
 * Spring antes do worker persistir o providerMessageId na notificacao. Como o
 * receipt e gravado primeiro (upsert idempotente por empresa + messageId) e
 * o worker reconcilia receipts pendentes ao mover para AGUARDANDO_ENTREGA, o
 * ACK nunca e perdido — nem em corrida, nem em restart (Render pode
 * reiniciar; nada critico vive so na memoria do Node).
 *
 * <p>Historico antigo: receipts existem somente daqui para frente; nenhuma
 * migracao "devolve" franquia historica sem evidencia de entrega.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "whatsapp_entrega_receipts", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_whatsapp_receipt_empresa_msg",
                columnNames = {"empresa_id", "provider_message_id"})
}, indexes = {
        @Index(name = "idx_whatsapp_receipt_empresa_msg",
                columnList = "empresa_id,provider_message_id")
})
public class WhatsAppEntregaReceiptEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private EmpresaEntity empresa;

    /** message.key.id do Baileys (nunca telefone, JID, texto ou keys). */
    @Column(name = "provider_message_id", nullable = false, length = 120)
    private String providerMessageId;

    /** Prova de entrega observada: sempre "DELIVERED" nesta task. */
    @Column(nullable = false, length = 20)
    private String status;

    /** True quando a notificacao correspondente ja consumiu este receipt. */
    @Column(name = "consumed", nullable = false)
    private boolean consumed;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    @PrePersist
    void prePersist() {
        dataCriacao = LocalDateTime.now();
        status = status == null ? "DELIVERED" : status;
    }

    @PreUpdate
    void preUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }
}

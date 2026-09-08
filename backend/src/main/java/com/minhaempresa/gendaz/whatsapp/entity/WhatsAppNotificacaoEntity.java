package com.minhaempresa.gendaz.whatsapp.entity;

import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppStatusNotificacao;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppTipoNotificacao;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "whatsapp_notificacoes", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_whatsapp_notif_empresa_chave",
                columnNames = {"empresa_id", "idempotency_key"})
}, indexes = {
        @Index(name = "idx_whatsapp_notif_empresa_status", columnList = "empresa_id,status"),
        @Index(name = "idx_whatsapp_notif_empresa_scheduled", columnList = "empresa_id,scheduled_at")
})
public class WhatsAppNotificacaoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private EmpresaEntity empresa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WhatsAppTipoNotificacao tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WhatsAppStatusNotificacao status;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(nullable = false)
    private LocalDateTime dataCriacao;

    private LocalDateTime dataAtualizacao;

    private LocalDateTime scheduledAt;

    private LocalDateTime sentAt;

    @Column(nullable = false)
    private int attempts;

    @Column(length = 1000)
    private String lastError;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private ClienteEntity cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agendamento_id")
    private AgendamentoEntity agendamento;

    @PrePersist
    void prePersist() {
        dataCriacao = LocalDateTime.now();
        status = status == null ? WhatsAppStatusNotificacao.PENDENTE : status;
    }

    @PreUpdate
    void preUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }
}

package com.minhaempresa.agendapro.whatsapp.entity;

import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.whatsapp.enums.WhatsappConversationStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "whatsapp_conversations", indexes = {
        @Index(name = "idx_whatsapp_conversations_empresa", columnList = "empresa_id"),
        @Index(name = "idx_whatsapp_conversations_contact_phone", columnList = "contact_phone")
})
public class WhatsappConversationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private EmpresaEntity empresa;

    @Column(name = "contact_name", length = 120)
    private String contactName;

    @Column(name = "contact_phone", nullable = false, length = 20)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WhatsappConversationStatus status;

    @Column(name = "bot_pausado", nullable = false)
    private Boolean botPausado;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        status = status == null ? WhatsappConversationStatus.OPEN : status;
        botPausado = botPausado != null && botPausado;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
        botPausado = botPausado != null && botPausado;
    }
}

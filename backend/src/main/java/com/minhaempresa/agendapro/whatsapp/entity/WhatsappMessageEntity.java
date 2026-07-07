package com.minhaempresa.agendapro.whatsapp.entity;

import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.whatsapp.enums.WhatsappMessageDirection;
import com.minhaempresa.agendapro.whatsapp.enums.WhatsappMessageStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "whatsapp_messages", indexes = {
        @Index(name = "idx_whatsapp_messages_empresa", columnList = "empresa_id"),
        @Index(name = "idx_whatsapp_messages_conversation", columnList = "conversation_id"),
        @Index(name = "idx_whatsapp_messages_provider_id", columnList = "provider_message_id")
})
public class WhatsappMessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private EmpresaEntity empresa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private WhatsappConversationEntity conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WhatsappMessageDirection direction;

    @Column(name = "from_number", length = 30)
    private String fromNumber;

    @Column(name = "to_number", length = 30)
    private String toNumber;

    @Column(name = "message_text", length = 1000)
    private String messageText;

    @Column(name = "provider_message_id", length = 120)
    private String providerMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WhatsappMessageStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        status = status == null ? WhatsappMessageStatus.RECEIVED : status;
    }
}

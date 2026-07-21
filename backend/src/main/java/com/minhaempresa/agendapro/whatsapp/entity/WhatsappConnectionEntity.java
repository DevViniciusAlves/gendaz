/*
  ╔══════════════════════════════════════════════╗
  ║    DESATIVADO - FUNCIONALIDADE WhatsApp    ║
  ║  Todo código comentado. Remova comentários   ║
  ║  para reativar.                              ║
  ╚══════════════════════════════════════════════╝
*/
package com.minhaempresa.agendapro.whatsapp.entity;

import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.whatsapp.enums.WhatsappConnectionStatus;
import com.minhaempresa.agendapro.whatsapp.enums.WhatsappProvider;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "whatsapp_connections", indexes = {
        @Index(name = "idx_whatsapp_connections_empresa", columnList = "empresa_id"),
        @Index(name = "idx_whatsapp_connections_phone_number", columnList = "phone_number_id")
})
public class WhatsappConnectionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false, unique = true)
    private EmpresaEntity empresa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WhatsappProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WhatsappConnectionStatus status;

    @Column(name = "waba_id", length = 100)
    private String wabaId;

    @Column(name = "phone_number_id", length = 100)
    private String phoneNumberId;

    @Column(name = "display_phone_number", length = 30)
    private String displayPhoneNumber;

    @Column(name = "access_token_encrypted", length = 2048)
    private String accessTokenEncrypted;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "connected_at")
    private LocalDateTime connectedAt;

    @Column(name = "disconnected_at")
    private LocalDateTime disconnectedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        provider = provider == null ? WhatsappProvider.BAILEYS : provider;
        status = status == null ? WhatsappConnectionStatus.DISCONNECTED : status;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

/*
  ╔══════════════════════════════════════════════╗
  ║    DESATIVADO - FUNCIONALIDADE WhatsApp    ║
  ║  Todo código comentado. Remova comentários   ║
  ║  para reativar.                              ║
  ╚══════════════════════════════════════════════╝
*/
package com.minhaempresa.agendapro.whatsapp.entity;

import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "whatsapp_sessions", indexes = {
        @Index(name = "idx_whatsapp_sessions_empresa", columnList = "empresa_id")
})
public class WhatsappSessionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false, unique = true)
    private EmpresaEntity empresa;

    @Column(name = "creds_json", nullable = false, columnDefinition = "TEXT")
    private String credsJson;

    @Column(name = "keys_json", nullable = false, columnDefinition = "TEXT")
    private String keysJson;

    @Column(name = "registered", nullable = false)
    private Boolean registered;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "me_id", length = 100)
    private String meId;

    @Column(name = "me_lid", length = 100)
    private String meLid;

    @Column(name = "last_status", length = 50)
    private String lastStatus;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        registered = registered != null && registered;
        credsJson = credsJson == null ? "{}" : credsJson;
        keysJson = keysJson == null ? "{}" : keysJson;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
        registered = registered != null && registered;
        credsJson = credsJson == null ? "{}" : credsJson;
        keysJson = keysJson == null ? "{}" : keysJson;
    }
}

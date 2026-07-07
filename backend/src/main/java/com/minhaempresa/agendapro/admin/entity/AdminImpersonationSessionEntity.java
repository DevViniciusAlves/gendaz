package com.minhaempresa.agendapro.admin.entity;

import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.usuario.entity.UsuarioEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "admin_impersonation_sessions", indexes = {
        @Index(name = "idx_impersonation_admin", columnList = "admin_id"),
        @Index(name = "idx_impersonation_empresa", columnList = "empresa_id")
})
public class AdminImpersonationSessionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private UsuarioEntity admin;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private EmpresaEntity empresa;

    @Column(nullable = false, length = 1000)
    private String motivo;

    @Column(nullable = false)
    private LocalDateTime dataInicio;

    private LocalDateTime dataFim;

    @Column(length = 120)
    private String ip;

    @Column(length = 600)
    private String userAgent;

    @Column(nullable = false)
    private boolean ativa;

    @PrePersist
    void prePersist() {
        dataInicio = LocalDateTime.now();
        ativa = true;
    }
}

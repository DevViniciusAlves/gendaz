package com.minhaempresa.gendaz.admin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "admin_impersonation_sessions")
public class AdminImpersonationSessionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_usuario_id", nullable = false)
    private Long adminUsuarioId;

    @Column(name = "usuario_impersonado_id", nullable = false)
    private Long usuarioImpersonadoId;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "session_token_hash", nullable = false, length = 128, unique = true)
    private String sessionTokenHash;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "ip_inicio", length = 100)
    private String ipInicio;

    @Column(name = "user_agent_inicio", length = 500)
    private String userAgentInicio;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "encerrado_em")
    private LocalDateTime encerradoEm;

    @Column(name = "motivo_encerramento", length = 100)
    private String motivoEncerramento;
}

package com.minhaempresa.agendapro.auth.entity;

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
@Table(name = "password_reset_tokens", indexes = {
        @Index(name = "idx_password_reset_token_hash", columnList = "token_hash"),
        @Index(name = "idx_password_reset_token_usuario", columnList = "usuario_id")
})
public class PasswordResetTokenEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioEntity usuario;

    @Column(name = "token_hash", nullable = false, unique = true, length = 120)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime dataCriacao;

    @Column(nullable = false)
    private LocalDateTime dataExpiracao;

    private LocalDateTime dataUso;

    @Column(nullable = false)
    private boolean usado;

    @PrePersist
    void prePersist() {
        dataCriacao = LocalDateTime.now();
    }
}

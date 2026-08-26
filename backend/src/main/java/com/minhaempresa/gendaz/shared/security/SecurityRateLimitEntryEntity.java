package com.minhaempresa.gendaz.shared.security;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "security_rate_limit_entries",
        uniqueConstraints = @UniqueConstraint(name = "uk_security_rate_limit_scope", columnNames = "scope_key"),
        indexes = {
                @Index(name = "idx_security_rate_limit_scope", columnList = "scope_key"),
                @Index(name = "idx_security_rate_limit_expira", columnList = "expira_em")
        }
)
public class SecurityRateLimitEntryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scope_key", nullable = false, length = 128)
    private String scopeKey;

    @Column(name = "janela_inicio", nullable = false)
    private LocalDateTime janelaInicio;

    @Column(name = "quantidade", nullable = false)
    private int quantidade;

    @Column(name = "bloqueado_ate")
    private LocalDateTime bloqueadoAte;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    @PrePersist
    void prePersist() {
        dataCriacao = LocalDateTime.now();
        dataAtualizacao = dataCriacao;
    }

    @PreUpdate
    void preUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }
}

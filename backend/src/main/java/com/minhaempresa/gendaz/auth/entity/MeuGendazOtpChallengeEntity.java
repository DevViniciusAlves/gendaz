package com.minhaempresa.gendaz.auth.entity;

import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
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
        name = "meu_gendaz_otp_challenges",
        uniqueConstraints = @UniqueConstraint(name = "uk_meu_gendaz_otp_empresa_email", columnNames = {"empresa_id", "email"}),
        indexes = {
                @Index(name = "idx_meu_gendaz_otp_empresa_email", columnList = "empresa_id,email"),
                @Index(name = "idx_meu_gendaz_otp_onboarding_hash", columnList = "onboarding_session_hash"),
                @Index(name = "idx_meu_gendaz_otp_expira", columnList = "otp_expira_em"),
                @Index(name = "idx_meu_gendaz_onboarding_expira", columnList = "onboarding_session_expira_em")
        }
)
public class MeuGendazOtpChallengeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private EmpresaEntity empresa;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(name = "otp_hash", length = 128)
    private String otpHash;

    @Column(name = "otp_expira_em")
    private LocalDateTime otpExpiraEm;

    @Column(name = "tentativas_falhas", nullable = false)
    private int tentativasFalhas;

    @Column(name = "ultima_solicitacao")
    private LocalDateTime ultimaSolicitacao;

    @Column(name = "reenviar_disponivel_em")
    private LocalDateTime reenviarDisponivelEm;

    @Column(name = "janela_solicitacoes_inicio")
    private LocalDateTime janelaSolicitacoesInicio;

    @Column(name = "solicitacoes_na_janela", nullable = false)
    private int solicitacoesNaJanela;

    @Column(name = "bloqueado_ate")
    private LocalDateTime bloqueadoAte;

    @Column(name = "validado_em")
    private LocalDateTime validadoEm;

    @Column(name = "onboarding_session_hash", length = 128)
    private String onboardingSessionHash;

    @Column(name = "onboarding_session_expira_em")
    private LocalDateTime onboardingSessionExpiraEm;

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

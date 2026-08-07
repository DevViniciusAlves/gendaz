package com.minhaempresa.agendapro.usuario.entity;

import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.usuario.enums.PerfilUsuario;
import com.minhaempresa.agendapro.usuario.enums.StatusUsuario;
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
        name = "usuarios",
        uniqueConstraints = @UniqueConstraint(name = "uk_usuario_empresa_email", columnNames = {"empresa_id", "email"})
)
public class UsuarioEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String senha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PerfilUsuario perfil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusUsuario status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    private LocalDateTime dataAtualizacao;

    @Column(nullable = false)
    private Boolean aceitouTermos;

    private LocalDateTime dataAceiteTermos;
    private LocalDateTime dataAceitePolitica;

    private String versaoTermos;
    private String versaoPolitica;

    @Column(length = 80)
    private String sessaoAtiva;

    @Column(name = "sessao_ativa_meu_gendaz", length = 80)
    private String sessaoAtivaMeuGendaz;

    @Column(name = "tentativas_login_falhadas", nullable = false)
    private Integer tentativasLoginFalhadas;

    @Column(name = "bloqueado_ate")
    private LocalDateTime bloqueadoAte;

    @Column(name = "ultimo_login_falhado")
    private LocalDateTime ultimoLoginFalhado;

    public Integer getTentativasLoginFalhadas() {
        return tentativasLoginFalhadas != null ? tentativasLoginFalhadas : 0;
    }

    public void setTentativasLoginFalhadas(Integer tentativasLoginFalhadas) {
        this.tentativasLoginFalhadas = tentativasLoginFalhadas != null ? tentativasLoginFalhadas : 0;
    }

    public boolean estaBloqueado() {
        return bloqueadoAte != null && LocalDateTime.now().isBefore(bloqueadoAte);
    }

    public boolean precisaCaptcha() {
        return getTentativasLoginFalhadas() >= 3;
    }

    @PrePersist
    void prePersist() {
        dataCriacao = LocalDateTime.now();
        status = status == null ? StatusUsuario.ATIVO : status;
        aceitouTermos = aceitouTermos != null && aceitouTermos;
        tentativasLoginFalhadas = tentativasLoginFalhadas != null ? tentativasLoginFalhadas : 0;
    }

    @PreUpdate
    void preUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }
}

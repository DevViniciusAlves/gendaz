package com.minhaempresa.gendaz.meugendazacesso.entity;

import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
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
        name = "meu_gendaz_acessos",
        uniqueConstraints = @UniqueConstraint(name = "uk_meu_gendaz_acesso_empresa_email", columnNames = {"empresa_id", "email"}),
        indexes = {
                @Index(name = "idx_meu_gendaz_acesso_empresa", columnList = "empresa_id"),
                @Index(name = "idx_meu_gendaz_acesso_sessao", columnList = "sessao_ativa")
        }
)
public class MeuGendazAcessoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private EmpresaEntity empresa;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(nullable = false, length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusUsuario status;

    @Column(name = "sessao_ativa", length = 80)
    private String sessaoAtiva;

    @Column(name = "usuario_legado_id")
    private Long usuarioLegadoId;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    @PrePersist
    void prePersist() {
        dataCriacao = LocalDateTime.now();
        if (status == null) {
            status = StatusUsuario.ATIVO;
        }
    }

    @PreUpdate
    void preUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }
}


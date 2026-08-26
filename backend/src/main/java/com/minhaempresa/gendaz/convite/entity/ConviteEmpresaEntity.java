package com.minhaempresa.gendaz.convite.entity;

import com.minhaempresa.gendaz.convite.enums.StatusConviteEmpresa;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "convites_empresa")
public class ConviteEmpresaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private EmpresaEntity empresa;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(name = "nome_convidado", length = 120)
    private String nomeConvidado;

    @Column(name = "telefone_convidado", length = 19)
    private String telefoneConvidado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por_usuario_id", nullable = false)
    private UsuarioEntity criadoPor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusConviteEmpresa status;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_expiracao", nullable = false)
    private LocalDateTime dataExpiracao;

    @Column(name = "data_aceite")
    private LocalDateTime dataAceite;

    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    @Column(name = "convite_referenciado_por")
    private Long conviteReferenciadoPor;

    @Column(name = "email_enviado_em")
    private LocalDateTime emailEnviadoEm;

    @Column(name = "cancelado_em")
    private LocalDateTime canceladoEm;

    @Column(name = "expirado_em")
    private LocalDateTime expiradoEm;

    @Column(name = "aceito_por_usuario_id")
    private Long aceitoPorUsuarioId;

    @Column(name = "reenvios", nullable = false)
    private Integer reenvios;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    @PrePersist
    void prePersist() {
        dataCriacao = LocalDateTime.now();
        if (reenvios == null) reenvios = 0;
        if (status == null) status = StatusConviteEmpresa.PENDING;
    }

    @PreUpdate
    void preUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }
}


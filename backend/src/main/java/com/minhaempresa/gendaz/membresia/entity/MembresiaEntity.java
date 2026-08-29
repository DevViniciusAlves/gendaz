package com.minhaempresa.gendaz.membresia.entity;

import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.membresia.enums.FuncaoMembresia;
import com.minhaempresa.gendaz.membresia.enums.StatusMembresia;
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
@Table(
        name = "membresias",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_membresia_usuario", columnNames = {"usuario_id"}),
                @UniqueConstraint(name = "uk_membresia_empresa_usuario", columnNames = {"empresa_id", "usuario_id"})
        }
)
public class MembresiaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioEntity usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private EmpresaEntity empresa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusMembresia status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FuncaoMembresia funcao;

    @Column(name = "is_owner", nullable = false)
    private Boolean owner;

    @Column(name = "data_entrada", nullable = false)
    private LocalDateTime dataEntrada;

    @Column(name = "data_remocao")
    private LocalDateTime dataRemocao;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alterado_por_usuario_id")
    private UsuarioEntity alteradoPor;

    @PrePersist
    void prePersist() {
        dataCriacao = LocalDateTime.now();
        if (dataEntrada == null) {
            dataEntrada = dataCriacao;
        }
        if (status == null) {
            status = StatusMembresia.ACTIVE;
        }
        if (funcao == null) {
            funcao = FuncaoMembresia.MEMBER;
        }
        if (owner == null) {
            owner = false;
        }
    }

    @PreUpdate
    void preUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }
}


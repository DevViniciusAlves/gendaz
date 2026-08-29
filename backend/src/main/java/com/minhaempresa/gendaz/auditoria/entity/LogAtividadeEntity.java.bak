package com.minhaempresa.gendaz.auditoria.entity;

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
@Table(name = "logs_atividade", indexes = {
        @Index(name = "idx_logs_atividade_empresa_data", columnList = "empresa_id, data_hora DESC"),
        @Index(name = "idx_logs_atividade_entidade", columnList = "entidade")
})
public class LogAtividadeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private EmpresaEntity empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private UsuarioEntity usuario;

    @Column(name = "nome_usuario", length = 120, nullable = false)
    private String nomeUsuario;

    @Column(length = 40, nullable = false)
    private String entidade;

    @Column(name = "entidade_id")
    private Long entidadeId;

    @Column(length = 500, nullable = false)
    private String acao;

    @Column(length = 1000)
    private String detalhes;

    @Column(length = 120)
    private String ip;

    @Column(name = "data_hora", nullable = false, updatable = false)
    private LocalDateTime dataHora;

    @PrePersist
    void prePersist() {
        if (dataHora == null) {
            dataHora = LocalDateTime.now();
        }
    }
}

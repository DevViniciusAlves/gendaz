package com.minhaempresa.gendaz.promocao.entity;

import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.promocao.enums.TipoPromocao;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "promocoes", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"empresa_id", "codigo"})
})
public class PromocaoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    @Column(nullable = false, length = 80)
    private String codigo;

    @Column(nullable = false, length = 180)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoPromocao tipo;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "data_inicio", nullable = false)
    private LocalDateTime dataInicio;

    @Column(name = "data_fim", nullable = false)
    private LocalDateTime dataFim;

    @Column(name = "quantidade_limite")
    private Integer quantidadeLimite;

    @Column(name = "quantidade_usada", nullable = false)
    private Integer quantidadeUsada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusCadastro status;

    @Column(name = "aplicar_todos_servicos", nullable = false)
    private Boolean aplicarTodosServicos;

    @ManyToMany(fetch = FetchType.LAZY)
    @Builder.Default
    @JoinTable(
            name = "promocao_servico",
            joinColumns = @JoinColumn(name = "promocao_id"),
            inverseJoinColumns = @JoinColumn(name = "servico_id")
    )
    private Set<ServicoEntity> servicos = new HashSet<>();

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_notificacao")
    private LocalDateTime dataNotificacao;

    @PrePersist
    void prePersist() {
        dataCriacao = LocalDateTime.now();
        status = status == null ? StatusCadastro.ATIVO : status;
        quantidadeUsada = quantidadeUsada == null ? 0 : quantidadeUsada;
        aplicarTodosServicos = aplicarTodosServicos == null || aplicarTodosServicos;
        if (servicos == null) {
            servicos = new HashSet<>();
        }
    }

    public boolean estaAtiva() {
        return status == StatusCadastro.ATIVO;
    }
}


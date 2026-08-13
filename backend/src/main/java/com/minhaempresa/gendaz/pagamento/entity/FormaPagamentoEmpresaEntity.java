package com.minhaempresa.gendaz.pagamento.entity;

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
@Table(name = "formas_pagamento_empresa")
public class FormaPagamentoEmpresaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false, unique = true)
    private EmpresaEntity empresa;

    @Column(name = "pix_ativo", nullable = false)
    private boolean pixAtivo;

    @Column(name = "debito_ativo", nullable = false)
    private boolean debitoAtivo;

    @Column(name = "credito_ativo", nullable = false)
    private boolean creditoAtivo;

    @Column(name = "parcelado_ativo", nullable = false)
    private boolean parceladoAtivo;

    @Column(name = "dinheiro_ativo", nullable = false)
    private boolean dinheiroAtivo;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    private LocalDateTime dataAtualizacao;

    @PrePersist
    void prePersist() {
        dataCriacao = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }
}

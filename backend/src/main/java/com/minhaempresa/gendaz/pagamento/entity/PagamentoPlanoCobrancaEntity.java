package com.minhaempresa.gendaz.pagamento.entity;

import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "pagamentos_planos_cobrancas",
    indexes = {
        @Index(name = "idx_pagamentos_planos_cobrancas_pagamento", columnList = "pagamento_plano_id"),
        @Index(name = "idx_pagamentos_planos_cobrancas_invoice", columnList = "stripe_invoice_id", unique = true)
    }
)
public class PagamentoPlanoCobrancaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pagamento_plano_id", nullable = false)
    private PagamentoPlanoEntity pagamentoPlano;

    @Column(name = "subscription_id", nullable = false, length = 120)
    private String subscriptionId;

    @Column(name = "stripe_invoice_id", nullable = false, unique = true, length = 120)
    private String stripeInvoiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPagamento status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "periodo_inicio", nullable = false)
    private LocalDate periodoInicio;

    @Column(name = "periodo_fim", nullable = false)
    private LocalDate periodoFim;

    @Column(name = "data_pagamento")
    private LocalDateTime dataPagamento;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @PrePersist
    void prePersist() {
        dataCriacao = LocalDateTime.now();
    }
}
package com.minhaempresa.gendaz.pagamento.entity;

import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.plano.entity.PlanoEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "pagamentos_planos",
        indexes = {
                @Index(name = "idx_pagamentos_planos_empresa", columnList = "empresa_id"),
                @Index(name = "idx_pagamentos_planos_provider", columnList = "provider_payment_id")
        }
)
public class PagamentoPlanoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private EmpresaEntity empresa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plano_id", nullable = false)
    private PlanoEntity plano;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assinatura_id")
    private AssinaturaEntity assinatura;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MetodoPagamento metodoPagamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPagamento status;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_payment_id", nullable = false, unique = true)
    private String providerPaymentId;

    @Column(name = "external_reference", unique = true, length = 120)
    private String externalReference;

    @Column(name = "payment_reference", unique = true, length = 120)
    private String paymentReference;

    @Column(name = "customer_name", length = 120)
    private String customerName;

    @Column(name = "customer_email", length = 120)
    private String customerEmail;

    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    @Column(name = "customer_doc_type", length = 20)
    private String customerDocType;

    @Column(name = "customer_doc_number", length = 20)
    private String customerDocNumber;

    @Column(name = "antifraud_reference", length = 120)
    private String antifraudReference;

    @Column(length = 600)
    private String checkoutUrl;

    @Column(name = "subscription_id", length = 120)
    private String subscriptionId;

    @Column(name = "stripe_customer_id", length = 120)
    private String stripeCustomerId;

    @Column(name = "stripe_session_id", length = 120, unique = true)
    private String stripeSessionId;

    private LocalDateTime dataCriacao;
    private LocalDateTime dataAtualizacao;
    private LocalDateTime dataExpiracao;
    private LocalDateTime dataPagamento;

    @PrePersist
    void prePersist() {
        dataCriacao = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }
}


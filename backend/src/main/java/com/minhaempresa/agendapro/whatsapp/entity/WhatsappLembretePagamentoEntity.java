/*
  ╔══════════════════════════════════════════════╗
  ║  ⚠️  DESATIVADO - FUNCIONALIDADE WhatsApp    ║
  ║  Todo código comentado. Remova comentários   ║
  ║  para reativar.                              ║
  ╚══════════════════════════════════════════════╝
*/
package com.minhaempresa.agendapro.whatsapp.entity;

import com.minhaempresa.agendapro.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "whatsapp_lembrete_pagamento", indexes = {
        @Index(name = "idx_whatsapp_lembrete_pagamento_empresa", columnList = "empresa_id"),
        @Index(name = "idx_whatsapp_lembrete_pagamento_agendamento", columnList = "agendamento_id")
})
public class WhatsappLembretePagamentoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agendamento_id", nullable = false, unique = true)
    private AgendamentoEntity agendamento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private EmpresaEntity empresa;

    @Column(nullable = false)
    private Integer tentativa;

    @Column(name = "enviado_em", nullable = false)
    private LocalDateTime enviadoEm;

    @Column(name = "respondido_em")
    private LocalDateTime respondidoEm;

    @Column(name = "opcao_respondida", length = 30)
    private String opcaoRespondida;

    @PrePersist
    void prePersist() {
        if (tentativa == null || tentativa < 1) {
            tentativa = 1;
        }
        if (enviadoEm == null) {
            enviadoEm = LocalDateTime.now();
        }
    }

    @PreUpdate
    void preUpdate() {
        if (tentativa == null || tentativa < 1) {
            tentativa = 1;
        }
    }
}

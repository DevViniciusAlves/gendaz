package com.minhaempresa.gendaz.whatsapp.entity;

import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

/**
 * Uso da franquia WhatsApp por empresa e ciclo da assinatura. Existe no
 * maximo um registro por (empresa, ciclo_inicio).
 *
 * Reserva nao e consumo: a disponibilidade de uma categoria e
 * limite - enviados - reservados. A reserva serializa workers concorrentes
 * (Fase 5); o consumo acontece somente no envio efetivo.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "whatsapp_uso_ciclos", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_whatsapp_uso_empresa_ciclo",
                columnNames = {"empresa_id", "ciclo_inicio"})
}, indexes = {
        @Index(name = "idx_whatsapp_uso_empresa_ciclo", columnList = "empresa_id,ciclo_inicio")
})
public class WhatsAppUsoCicloEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private EmpresaEntity empresa;

    @Column(name = "ciclo_inicio", nullable = false)
    private LocalDate cicloInicio;

    @Column(nullable = false)
    private int lembretesReservados;

    @Column(nullable = false)
    private int lembretesEnviados;

    @Column(nullable = false)
    private int crmReservados;

    @Column(nullable = false)
    private int crmEnviados;

    @Column(nullable = false)
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

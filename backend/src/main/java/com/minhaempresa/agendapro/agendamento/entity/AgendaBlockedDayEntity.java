package com.minhaempresa.agendapro.agendamento.entity;

import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.profissional.entity.ProfissionalEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "agenda_blocked_days",
        uniqueConstraints = @UniqueConstraint(name = "uk_agenda_blocked_days_empresa_prof_data", columnNames = {"empresa_id", "profissional_id", "blocked_date"})
)
public class AgendaBlockedDayEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profissional_id")
    private ProfissionalEntity profissional;

    @Column(name = "blocked_date", nullable = false)
    private LocalDate data;

    @Column(length = 255)
    private String motivo;

    @Column(nullable = false, updatable = false)
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

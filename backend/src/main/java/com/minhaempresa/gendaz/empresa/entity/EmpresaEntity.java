package com.minhaempresa.gendaz.empresa.entity;

import com.minhaempresa.gendaz.empresa.enums.RamoEmpresa;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.shared.enums.TimezoneEnum;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "empresas")
public class EmpresaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nomeFantasia;

    @Column(unique = true)
    private String documento;

    private String telefone;

    @Column(nullable = false)
    private String email;

    @Column(name = "agendamento_slug", unique = true, length = 120)
    private String agendamentoSlug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusEmpresa status;

    @Column(name = "timezone", nullable = false, length = 60)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "ramo", length = 50)
    private RamoEmpresa ramo;

    @Column(name = "ramo_atualizado_em")
    private LocalDateTime ramoAtualizadoEm;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    private LocalDateTime dataAtualizacao;

    @PrePersist
    void prePersist() {
        dataCriacao = LocalDateTime.now();
        status = status == null ? StatusEmpresa.ATIVA : status;
        timezone = timezone == null || timezone.isBlank() ? TimezoneEnum.AMERICA_CUIABA.getValue() : timezone;
    }

    @PreUpdate
    void preUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }

    public void setRamo(RamoEmpresa ramo) {
        if (Objects.equals(this.ramo, ramo)) {
            return;
        }
        this.ramo = ramo;
        this.ramoAtualizadoEm = LocalDateTime.now();
    }
}


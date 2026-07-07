package com.minhaempresa.agendapro.plano.entity;

import com.minhaempresa.agendapro.plano.enums.StatusPlano;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "planos")
public class PlanoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nome;

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorMensal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPlano status;
}

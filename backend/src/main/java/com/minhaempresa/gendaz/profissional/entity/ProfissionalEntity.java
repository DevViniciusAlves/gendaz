package com.minhaempresa.gendaz.profissional.entity;

import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.profissional.enums.DiaSemana;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import jakarta.persistence.*;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "profissionais")
public class ProfissionalEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    private String especialidade;
    private String telefone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusCadastro status;

    @Column(nullable = false)
    private boolean sistema;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "profissional_dias_trabalho", joinColumns = @JoinColumn(name = "profissional_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false)
    @Builder.Default
    private Set<DiaSemana> diasTrabalho = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;
}


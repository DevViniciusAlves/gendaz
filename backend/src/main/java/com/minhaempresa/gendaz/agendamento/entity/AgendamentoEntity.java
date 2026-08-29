package com.minhaempresa.gendaz.agendamento.entity;

import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "agendamentos")
public class AgendamentoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private ClienteEntity cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servico_id")
    private ServicoEntity servico;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profissional_id")
    private ProfissionalEntity profissional;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false)
    private LocalTime horaInicio;

    @Column(nullable = false)
    private LocalTime horaFim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusAgendamento status;

    @Column(name = "confirmacao_pagamento_dono_2_enviada", nullable = false)
    private boolean confirmacaoPagamentoDono2Enviada = false;

    @Column(length = 6, unique = true)
    private String protocolo;

    @Column(length = 1000)
    private String observacoes;

    @Column(name = "valor_original", precision = 10, scale = 2)
    private BigDecimal valorOriginal;

    @Column(name = "valor_desconto", precision = 10, scale = 2)
    private BigDecimal valorDesconto;

    @Column(name = "valor_final", precision = 10, scale = 2)
    private BigDecimal valorFinal;

    @Column(name = "cupom_codigo", length = 80)
    private String cupomCodigo;

    @Column(name = "tipo_promocao_aplicada", length = 20)
    private String tipoPromocaoAplicada;

    @Column(name = "valor_promocao_aplicada", precision = 10, scale = 2)
    private BigDecimal valorPromocaoAplicada;

    @Column(name = "promocao_origem_id")
    private Long promocaoOrigemId;

}


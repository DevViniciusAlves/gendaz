package com.minhaempresa.gendaz.admin.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "admin_audit")
public class AdminAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long empresaId;

    @Column(nullable = false)
    private Long usuarioId;

    @Column(nullable = false)
    private String usuarioNome;

    @Column(nullable = false)
    private String acao;

    @Column(nullable = false)
    private String entidade;

    private Long entidadeId;

    @Column(nullable = false, length = 1000)
    private String descricao;

    @Column(nullable = false)
    private LocalDateTime dataHora;

    private String ip;

    private String userAgent;
}
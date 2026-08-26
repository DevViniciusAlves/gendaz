package com.minhaempresa.gendaz.promocao.entity;

import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "promocao_notificacao")
public class PromocaoNotificacaoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promocao_id")
    private PromocaoEntity promocao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private ClienteEntity cliente;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "data_envio")
    private LocalDateTime dataEnvio;

    @Column(name = "mensagem_erro", length = 1000)
    private String mensagemErro;
}


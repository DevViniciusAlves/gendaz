package com.minhaempresa.gendaz.meugendazpromocao.entity;

import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import jakarta.persistence.*;
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
@Table(name = "meu_gendaz_promocao_notificacao")
public class MeuGendazPromocaoNotificacaoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promocao_id")
    private MeuGendazPromocaoEntity promocao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private ClienteEntity cliente;

    @Column(nullable = false)
    private Boolean lido;

    @Column(name = "data_envio", nullable = false)
    private LocalDateTime dataEnvio;

    @Column(name = "data_leitura")
    private LocalDateTime dataLeitura;

    @PrePersist
    void prePersist() {
        if (lido == null) {
            lido = false;
        }
        if (dataEnvio == null) {
            dataEnvio = LocalDateTime.now();
        }
    }
}


package com.minhaempresa.gendaz.crm.entity;

import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "crm_contatos")
public class CrmContatoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "cliente_id")
    private ClienteEntity cliente;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false, length = 30)
    private String template;

    @Column(length = 200)
    private String assunto;

    @Column(columnDefinition = "TEXT")
    private String mensagem;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private LocalDateTime dataCriacao;

    private LocalDateTime aberturaData;

    @PrePersist
    void prePersist() {
        dataCriacao = LocalDateTime.now();
        if (status == null || status.isBlank()) {
            status = "enviado";
        }
    }
}


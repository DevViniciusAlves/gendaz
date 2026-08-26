package com.minhaempresa.gendaz.conversa.entity;

import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.conversa.enums.StatusConversa;
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
@Table(name = "conversas")
public class ConversaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private ClienteEntity cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusConversa status;

    private String ultimaMensagem;
    private LocalDateTime dataUltimaMensagem;
}


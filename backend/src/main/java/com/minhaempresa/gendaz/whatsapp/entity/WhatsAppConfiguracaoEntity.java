package com.minhaempresa.gendaz.whatsapp.entity;

import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * Configuracao WhatsApp por empresa. Uma linha por empresa; ausencia de
 * linha equivale a tudo desabilitado. Sem UI nesta fase: habilitado
 * futuramente de forma explicita.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "whatsapp_configuracoes", uniqueConstraints = {
        @UniqueConstraint(name = "uk_whatsapp_config_empresa", columnNames = {"empresa_id"})
})
public class WhatsAppConfiguracaoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private EmpresaEntity empresa;

    @Column(name = "lembretes_ativos", nullable = false)
    private boolean lembretesAtivos;

    @Column(nullable = false)
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

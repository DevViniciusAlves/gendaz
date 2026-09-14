package com.minhaempresa.gendaz.crm.entity;

import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppNotificacaoEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "crm_contatos", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_crm_contatos_whatsapp_notif",
                columnNames = {"whatsapp_notificacao_id"})
})
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

    /**
     * Vinculo idempotente com a notificacao WhatsApp (somente canal
     * whatsapp; nulo para e-mail e historico antigo). A UNIQUE garante no
     * banco: uma notificacao, no maximo um registro de historico.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "whatsapp_notificacao_id")
    private WhatsAppNotificacaoEntity whatsappNotificacao;

    @PrePersist
    void prePersist() {
        dataCriacao = LocalDateTime.now();
        if (status == null || status.isBlank()) {
            status = "enviado";
        }
    }
}


/*
  ╔══════════════════════════════════════════════╗
  ║    DESATIVADO - FUNCIONALIDADE WhatsApp    ║
  ║  Todo código comentado. Remova comentários   ║
  ║  para reativar.                              ║
  ╚══════════════════════════════════════════════╝
*/
package com.minhaempresa.agendapro.whatsapp.entity;

import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
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
@Table(name = "whatsapp_fluxo_conversa", indexes = {
        @Index(name = "idx_whatsapp_fluxo_empresa", columnList = "empresa_id"),
        @Index(name = "idx_whatsapp_fluxo_telefone", columnList = "telefone_cliente"),
        @Index(name = "idx_whatsapp_fluxo_remote", columnList = "remote_jid")
})
public class WhatsappFluxoConversaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private EmpresaEntity empresa;

    @Column(name = "telefone_cliente", nullable = false, length = 20)
    private String telefoneCliente;

    @Column(name = "remote_jid", length = 120)
    private String remoteJid;

    @Column(name = "tipo_fluxo", nullable = false, length = 30)
    private String tipoFluxo;

    @Column(nullable = false, length = 50)
    private String etapa;

    @Column(nullable = false)
    private Boolean ativo;

    @Column(name = "modo_selecionado", length = 30)
    private String modoSelecionado;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Column(name = "expira_em")
    private LocalDateTime expiraEm;

    @PrePersist
    void prePersist() {
        criadoEm = LocalDateTime.now();
        atualizadoEm = LocalDateTime.now();
        ativo = ativo != null && ativo;
    }

    @PreUpdate
    void preUpdate() {
        atualizadoEm = LocalDateTime.now();
        ativo = ativo != null && ativo;
    }
}

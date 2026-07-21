package com.minhaempresa.agendapro.empresa.entity;

import com.minhaempresa.agendapro.empresa.enums.StatusEmpresa;
import com.minhaempresa.agendapro.shared.enums.TimezoneEnum;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "empresas")
public class EmpresaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nomeFantasia;

    @Column(unique = true)
    private String documento;

    private String telefone;

    @Column(nullable = false)
    private String email;

    @Column(name = "agendamento_slug", unique = true, length = 120)
    private String agendamentoSlug;

    //  DESATIVADO - WhatsApp
    // @Column(name = "whatsapp_descricao_empresa", length = 500)
    // private String whatsappDescricaoEmpresa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusEmpresa status;

    //  DESATIVADO - WhatsApp
    // @Column(name = "whatsapp_connected", nullable = false)
    // private Boolean whatsappConnected;

    // @Column(name = "whatsapp_phone", length = 30)
    // private String whatsappPhone;

    // @Column(name = "whatsapp_notifications_enabled", nullable = false)
    // private Boolean whatsappNotificationsEnabled;

    // @Column(name = "whatsapp_secretaria_ia_enabled", nullable = false)
    // private Boolean whatsappSecretariaIaEnabled;

    // @Column(name = "whatsapp_mensagem_boas_vindas", length = 2000)
    // private String whatsappMensagemBoasVindas;

    @Column(name = "timezone", nullable = false, length = 60)
    private String timezone;

    //  DESATIVADO - WhatsApp
    // @Column(name = "whatsapp_resposta_horarios", length = 2000)
    // private String whatsappRespostaHorarios;

    // @Column(name = "whatsapp_resposta_servicos", length = 2000)
    // private String whatsappRespostaServicos;

    // @Column(name = "whatsapp_resposta_nao_entende", length = 2000)
    // private String whatsappRespostaNaoEntende;

    // @Column(name = "whatsapp_mensagem_humano", length = 2000)
    // private String whatsappMensagemHumano;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    private LocalDateTime dataAtualizacao;

    @PrePersist
    void prePersist() {
        dataCriacao = LocalDateTime.now();
        status = status == null ? StatusEmpresa.ATIVA : status;
        //  DESATIVADO - WhatsApp
        // whatsappConnected = whatsappConnected == null ? Boolean.FALSE : whatsappConnected;
        // whatsappNotificationsEnabled = whatsappNotificationsEnabled == null ? Boolean.TRUE : whatsappNotificationsEnabled;
        // whatsappSecretariaIaEnabled = whatsappSecretariaIaEnabled == null ? Boolean.TRUE : whatsappSecretariaIaEnabled;
        timezone = timezone == null || timezone.isBlank() ? TimezoneEnum.AMERICA_CUIABA.getValue() : timezone;
    }

    @PreUpdate
    void preUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }
}

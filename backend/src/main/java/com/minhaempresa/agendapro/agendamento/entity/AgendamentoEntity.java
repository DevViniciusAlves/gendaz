package com.minhaempresa.agendapro.agendamento.entity;

import com.minhaempresa.agendapro.agendamento.enums.StatusAgendamento;
import com.minhaempresa.agendapro.cliente.entity.ClienteEntity;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.profissional.entity.ProfissionalEntity;
import com.minhaempresa.agendapro.servico.entity.ServicoEntity;
import jakarta.persistence.*;
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

    @Column(length = 6, unique = true)
    private String protocolo;

    @Column(length = 1000)
    private String observacoes;

    @Column(name = "lembrete_wpp_enviado", nullable = false)
    private Boolean lembreteWppEnviado;

    @Builder.Default
    @Column(name = "confirmacao_pagamento_dono_enviada", nullable = false)
    private Boolean confirmacaoPagamentoDonoEnviada = Boolean.FALSE;

    @Column(name = "confirmacao_pagamento_dono_enviada_em")
    private java.time.LocalDateTime confirmacaoPagamentoDonoEnviadaEm;

    @Builder.Default
    @Column(name = "segunda_confirmacao_pagamento_dono_enviada", nullable = false)
    private Boolean segundaConfirmacaoPagamentoDonoEnviada = Boolean.FALSE;

    @Column(name = "segunda_confirmacao_pagamento_dono_enviada_em")
    private java.time.LocalDateTime segundaConfirmacaoPagamentoDonoEnviadaEm;

    @Builder.Default
    @Column(name = "confirmacao_pagamento_dono_2_enviada", nullable = false)
    private Boolean confirmacaoPagamentoDono2Enviada = Boolean.FALSE;

    @Builder.Default
    @Column(name = "confirmacao_pagamento_dono_respondida", nullable = false)
    private Boolean confirmacaoPagamentoDonoRespondida = Boolean.FALSE;

    @Column(name = "confirmacao_pagamento_dono_respondida_em")
    private java.time.LocalDateTime confirmacaoPagamentoDonoRespondidaEm;

    @PrePersist
    void prePersist() {
        lembreteWppEnviado = lembreteWppEnviado == null ? Boolean.FALSE : lembreteWppEnviado;
        confirmacaoPagamentoDonoEnviada = confirmacaoPagamentoDonoEnviada == null ? Boolean.FALSE : confirmacaoPagamentoDonoEnviada;
        segundaConfirmacaoPagamentoDonoEnviada = segundaConfirmacaoPagamentoDonoEnviada == null ? Boolean.FALSE : segundaConfirmacaoPagamentoDonoEnviada;
        confirmacaoPagamentoDono2Enviada = confirmacaoPagamentoDono2Enviada == null ? Boolean.FALSE : confirmacaoPagamentoDono2Enviada;
        confirmacaoPagamentoDonoRespondida = confirmacaoPagamentoDonoRespondida == null ? Boolean.FALSE : confirmacaoPagamentoDonoRespondida;
    }
}

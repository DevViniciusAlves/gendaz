package com.minhaempresa.agendapro.mensagem.entity;

import com.minhaempresa.agendapro.conversa.entity.ConversaEntity;
import com.minhaempresa.agendapro.mensagem.enums.DirecaoMensagem;
import com.minhaempresa.agendapro.mensagem.enums.TipoMensagem;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mensagens")
public class MensagemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversa_id")
    private ConversaEntity conversa;

    @Column(nullable = false, length = 3000)
    private String conteudo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DirecaoMensagem direcao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMensagem tipo;

    @Column(nullable = false)
    private LocalDateTime dataEnvio;
}

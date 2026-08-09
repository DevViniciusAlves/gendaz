package com.minhaempresa.agendapro.chamado.entity;

import com.minhaempresa.agendapro.chamado.enums.StatusChamado;
import com.minhaempresa.agendapro.chamado.enums.PrioridadeChamado;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.meugendazacesso.entity.MeuGendazAcessoEntity;
import com.minhaempresa.agendapro.usuario.entity.UsuarioEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "chamados", indexes = {
        @Index(name = "idx_chamados_empresa", columnList = "empresa_id"),
        @Index(name = "idx_chamados_status", columnList = "status")
})
public class ChamadoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String assunto;

    @Column(nullable = false, length = 500)
    private String mensagem;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PrioridadeChamado prioridade;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private EmpresaEntity empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private UsuarioEntity usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meu_gendaz_acesso_id")
    private MeuGendazAcessoEntity meuGendazAcesso;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusChamado status;

    @Column(nullable = false)
    private LocalDateTime dataCriacao;

    private LocalDateTime dataAtualizacao;

    @Column(length = 1200)
    private String resposta;

    @Column(nullable = false, length = 20)
    private String origem;

    @PrePersist
    void prePersist() {
        dataCriacao = LocalDateTime.now();
        status = status == null ? StatusChamado.ABERTO : status;
        prioridade = prioridade == null ? PrioridadeChamado.MEDIA : prioridade;
        origem = normalizarOrigem(origem);
    }

    @PreUpdate
    void preUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }

    private String normalizarOrigem(String valor) {
        if (valor == null || valor.isBlank()) {
            return "PAINEL";
        }
        String normalizado = valor.trim().toUpperCase();
        return switch (normalizado) {
            case "MEU_GENDAZ", "MEUGENDAZ", "MEU GANDAZ" -> "MEU_GENDAZ";
            default -> "PAINEL";
        };
    }
}

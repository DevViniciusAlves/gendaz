package com.minhaempresa.agendapro.cliente.entity;

import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.shared.enums.StatusCadastro;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.AccessLevel;
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
@Table(name = "clientes")
public class ClienteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Setter(AccessLevel.NONE)
    @Column(name = "telefone", length = 20)
    @NotBlank(message = "Telefone é obrigatório")
    private String telefone;

    private String email;

    @Column(length = 1000)
    private String observacoes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusCadastro status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    private LocalDateTime dataAtualizacao;

    public void setTelefone(String telefone) {
        this.telefone = telefone;
        validarTelefone();
    }

    public void validarTelefone() {
        if (telefone == null || telefone.isBlank()) {
            throw new BusinessException("Telefone é obrigatório");
        }

        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.length() < 11 || digitos.length() > 14) {
            throw new BusinessException("Telefone invalido. Use codigo da cidade + numero. Voce informou apenas " + digitos.length() + " digitos.");
        }
    }

    @PrePersist
    void prePersist() {
        dataCriacao = LocalDateTime.now();
        status = status == null ? StatusCadastro.ATIVO : status;
        validarTelefone();
    }

    @PreUpdate
    void preUpdate() {
        dataAtualizacao = LocalDateTime.now();
        validarTelefone();
    }
}

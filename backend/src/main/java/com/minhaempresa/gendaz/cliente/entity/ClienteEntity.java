package com.minhaempresa.gendaz.cliente.entity;

import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
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
    }

    // Invariante defensiva APENAS do formato canônico de persistencia (somente dígitos,
    // no máximo 15). A validacao semântica real do número (país, plano de numeração)
    // ocorre antes, no PhoneNumberService. A Entity não conhece DDD, DDI nem país.
    public void validarTelefone() {
        if (telefone == null || telefone.isBlank()) {
            throw new BusinessException("Telefone é obrigatório");
        }
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.length() > 15) {
            throw new BusinessException("Telefone inválido. Confira o país e o número informado.");
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


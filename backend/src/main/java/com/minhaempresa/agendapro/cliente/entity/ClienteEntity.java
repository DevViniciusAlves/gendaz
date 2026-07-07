package com.minhaempresa.agendapro.cliente.entity;

import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.shared.BusinessException;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.*;

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

    @Column(name = "telefone", length = 20)
    @NotBlank(message = "Telefone é obrigatório")
    private String telefone;

    private String email;

    @Column(length = 1000)
    private String observacoes;

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

        // Deve ter exatamente 13 dígitos (55 + DDD + número)
        if (digitos.length() != 13) {
            throw new BusinessException("Telefone inválido. Formato correto: +55 (DDD) 99999-9999. Você informou apenas " + digitos.length() + " dígitos.");
        }

        // Deve começar com 55 (Brasil)
        if (!digitos.startsWith("55")) {
            throw new BusinessException("Telefone inválido. Deve ser Brasil (+55)");
        }

        // DDD deve ser entre 11 e 99
        String ddd = digitos.substring(2, 4);
        int dddInt = Integer.parseInt(ddd);
        if (dddInt < 11 || dddInt > 99) {
            throw new BusinessException("DDD inválido: " + ddd + ". DDD deve estar entre 11 e 99");
        }
    }

    @PrePersist
    void prePersist() {
        dataCriacao = LocalDateTime.now();
        validarTelefone();
    }

    @PreUpdate
    void preUpdate() {
        dataAtualizacao = LocalDateTime.now();
        validarTelefone();
    }
}

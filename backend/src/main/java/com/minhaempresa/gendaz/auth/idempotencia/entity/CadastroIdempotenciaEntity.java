package com.minhaempresa.gendaz.auth.idempotencia.entity;

import com.minhaempresa.gendaz.auth.idempotencia.enums.CadastroIdempotenciaStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(
        name = "cadastro_idempotencia",
        uniqueConstraints = @UniqueConstraint(name = "uk_cadastro_idempotencia_key_hash", columnNames = {"key_hash"})
)
public class CadastroIdempotenciaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CadastroIdempotenciaStatus status;

    @Column(name = "empresa_id")
    private Long empresaId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "assinatura_id")
    private Long assinaturaId;

    @Column(name = "pagamento_plano_id")
    private Long pagamentoPlanoId;

    @Column(name = "status_conta", length = 30)
    private String statusConta;

    @Column(name = "ultimo_request_id", length = 64)
    private String ultimoRequestId;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;
}

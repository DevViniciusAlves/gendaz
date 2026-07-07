package com.minhaempresa.agendapro.security.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ip_tracking")
public class IpTrackingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String ipAddress;

    @Column(name = "tentativas_falhadas")
    private Integer tentativasFalhadas = 0;

    @Column(name = "ultimo_acesso")
    private LocalDateTime ultimoAcesso;

    @Column(name = "bloqueado")
    private Boolean bloqueado = false;

    @Column(name = "bloqueado_ate")
    private LocalDateTime bloqueadoAte;

    @Column(name = "motivo_bloqueio")
    private String motivoBloqueio;

    @Column(name = "data_criacao", updatable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    @PrePersist
    void prePersist() {
        dataCriacao = LocalDateTime.now();
        dataAtualizacao = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public Integer getTentativasFalhadas() { return tentativasFalhadas != null ? tentativasFalhadas : 0; }
    public void setTentativasFalhadas(Integer tentativas) { this.tentativasFalhadas = tentativas; }
    public LocalDateTime getUltimoAcesso() { return ultimoAcesso; }
    public void setUltimoAcesso(LocalDateTime ultimoAcesso) { this.ultimoAcesso = ultimoAcesso; }
    public Boolean getBloqueado() { return bloqueado != null ? bloqueado : false; }
    public void setBloqueado(Boolean bloqueado) { this.bloqueado = bloqueado; }
    public LocalDateTime getBloqueadoAte() { return bloqueadoAte; }
    public void setBloqueadoAte(LocalDateTime bloqueadoAte) { this.bloqueadoAte = bloqueadoAte; }
    public String getMotivoBloqueio() { return motivoBloqueio; }
    public void setMotivoBloqueio(String motivo) { this.motivoBloqueio = motivo; }

    public boolean estaBloqueado() {
        return bloqueado && (bloqueadoAte == null || LocalDateTime.now().isBefore(bloqueadoAte));
    }
}

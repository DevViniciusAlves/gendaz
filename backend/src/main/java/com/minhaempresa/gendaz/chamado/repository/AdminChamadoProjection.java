package com.minhaempresa.gendaz.chamado.repository;

import java.time.LocalDateTime;

public interface AdminChamadoProjection {
    Long getId();
    String getAssunto();
    String getMensagem();
    String getEmpresa();
    String getUsuario();
    String getStatus();
    String getResposta();
    LocalDateTime getDataCriacao();
    LocalDateTime getDataAtualizacao();
}


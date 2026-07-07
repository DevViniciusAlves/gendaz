package com.minhaempresa.agendapro.notificacao.mapper;

import com.minhaempresa.agendapro.notificacao.dto.NotificacaoDtos.NotificacaoResponse;
import com.minhaempresa.agendapro.notificacao.entity.NotificacaoEntity;

public class NotificacaoMapper {
    public NotificacaoResponse toResponse(NotificacaoEntity notificacao) {
        return new NotificacaoResponse(
                notificacao.getId(),
                notificacao.getCliente().getId(),
                notificacao.getEmpresa().getId(),
                notificacao.getMensagem(),
                notificacao.getTipo(),
                notificacao.getStatus(),
                notificacao.getDataCriacao()
        );
    }
}

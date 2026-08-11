package com.minhaempresa.gendaz.notificacao.dto;

import com.minhaempresa.gendaz.notificacao.enums.StatusNotificacao;
import com.minhaempresa.gendaz.notificacao.enums.TipoNotificacao;
import java.time.LocalDateTime;

public final class NotificacaoDtos {
    private NotificacaoDtos() {}

    public record NotificacaoResponse(
            Long id,
            Long clienteId,
            Long empresaId,
            String mensagem,
            TipoNotificacao tipo,
            StatusNotificacao status,
            LocalDateTime dataCriacao
    ) {}
}


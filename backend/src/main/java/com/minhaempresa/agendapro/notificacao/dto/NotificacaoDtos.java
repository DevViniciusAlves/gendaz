package com.minhaempresa.agendapro.notificacao.dto;

import com.minhaempresa.agendapro.notificacao.enums.StatusNotificacao;
import com.minhaempresa.agendapro.notificacao.enums.TipoNotificacao;
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

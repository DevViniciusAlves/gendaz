package com.minhaempresa.gendaz.whatsapp.service;

import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppNotificacaoEntity;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppTipoNotificacao;
import java.time.LocalDateTime;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Enfileiramento de notificacoes WhatsApp para uso futuro de Agenda e CRM.
 *
 * <p>Nesta fase ninguem chama este servico ainda; ele apenas prepara
 * notificacoes persistentes validas para o worker. Validacao estrutural
 * (sem normalizacao brasileira, que pertence a fase posterior):
 * recipient com somente digitos (8-15), message nao blank (max 4096) e
 * scheduledAt obrigatorio.
 */
@Service
@RequiredArgsConstructor
public class WhatsAppFilaService {

    private static final Pattern RECIPIENT_PATTERN = Pattern.compile("^[0-9]{8,15}$");
    private static final int MAX_MESSAGE_LENGTH = 4096;

    private final WhatsAppNotificacaoService notificacaoService;

    public WhatsAppNotificacaoEntity enfileirar(
            Long empresaId,
            WhatsAppTipoNotificacao tipo,
            String idempotencyKey,
            LocalDateTime scheduledAt,
            Long clienteId,
            Long agendamentoId,
            String recipient,
            String messageBody) {
        validar(recipient, messageBody, scheduledAt);
        return notificacaoService.criarIdempotenteConteudo(
                empresaId, tipo, idempotencyKey, scheduledAt, clienteId, agendamentoId,
                recipient, messageBody);
    }

    static void validar(String recipient, String messageBody, LocalDateTime scheduledAt) {
        if (recipient == null || !RECIPIENT_PATTERN.matcher(recipient).matches()) {
            throw new IllegalArgumentException("Recipient deve conter somente digitos (8-15).");
        }
        if (messageBody == null || messageBody.isBlank() || messageBody.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Message deve ser nao vazia com no maximo 4096 caracteres.");
        }
        if (scheduledAt == null) {
            throw new IllegalArgumentException("ScheduledAt e obrigatorio.");
        }
    }
}

package com.minhaempresa.gendaz.whatsapp.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Relogio operacional do modulo WhatsApp.
 *
 * <p>Toda a logica operacional da fila trabalha em UTC (representado como
 * LocalDateTime em ZoneOffset.UTC, o tipo das colunas atuais): comparacoes
 * de agendamento, expiracao, retry e recovery nunca usam o timezone do
 * servidor. Centralizado aqui para facilitar testes e evitar
 * LocalDateTime.now() solto. Nao altera a JVM nem outros modulos.
 */
@Component
public class WhatsAppClock {

    private final String appTimezone;

    public WhatsAppClock(@Value("${app.timezone:America/Sao_Paulo}") String appTimezone) {
        this.appTimezone = appTimezone;
    }

    public LocalDateTime agoraUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public ZoneId zonaEmpresa(String timezoneEmpresa) {
        String valor = timezoneEmpresa == null || timezoneEmpresa.isBlank()
                ? appTimezone
                : timezoneEmpresa;
        if (valor == null || valor.isBlank()) {
            valor = "America/Cuiaba";
        }
        return ZoneId.of(valor);
    }
}

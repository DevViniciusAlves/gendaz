/*
  ╔══════════════════════════════════════════════╗
  ║    DESATIVADO - FUNCIONALIDADE WhatsApp    ║
  ║  Todo código comentado. Remova comentários   ║
  ║  para reativar.                              ║
  ╚══════════════════════════════════════════════╝
*/
package com.minhaempresa.agendapro.whatsapp.service;

import com.minhaempresa.agendapro.agendamento.enums.StatusAgendamento;
import com.minhaempresa.agendapro.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.repository.EmpresaRepository;
import com.minhaempresa.agendapro.shared.enums.TimezoneEnum;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WhatsappReminderScheduler {
    private final AgendamentoRepository agendamentoRepository;
    private final EmpresaRepository empresaRepository;
    private final WhatsappNodeClient nodeClient;
    @Value("${app.timezone:America/Cuiaba}")
    private String appTimezone;

    // @Scheduled(fixedDelay = 60000)  //  DESATIVADO
    public void enviarLembretes() {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        return;
    }

    private void tentarEnviarLembrete(AgendamentoRepository.AgendamentoLembreteProjection agendamento) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        if (agendamento == null || agendamento.getEmpresaId() == null) {
            return;
        }
        if (!Boolean.TRUE.equals(agendamento.getEmpresaWhatsappConnected())) {
            log.info("[Reminder] ignorado whatsapp desconectado empresaId={} agendamentoId={}",
                    agendamento.getEmpresaId(), agendamento.getId());
            return;
        }
        if (agendamento.getClienteTelefone() == null || agendamento.getClienteTelefone().isBlank()) {
            log.info("[Reminder] ignorado sem telefone agendamentoId={}", agendamento.getId());
            return;
        }
        if (agendamento.getStatus() == StatusAgendamento.CANCELADO) {
            log.info("[Reminder] ignorado status invalido agendamentoId={}", agendamento.getId());
            return;
        }
        if (Boolean.TRUE.equals(agendamento.getLembreteWppEnviado())) {
            return;
        }

        String telefone = normalizarTelefone(agendamento.getClienteTelefone());
        if (telefone == null) {
            log.warn("[Reminder] ignorado telefone nao normalizavel agendamentoId={} telefoneOriginal={}",
                    agendamento.getId(), agendamento.getClienteTelefone());
            return;
        }
        String mensagem = montarMensagem(agendamento);
        Map<String, Object> payload = new HashMap<>();
        payload.put("agendamentoId", agendamento.getId());
        payload.put("empresaId", agendamento.getEmpresaId());
        payload.put("tipo", "LEMBRETE_CLIENTE");
        payload.put("telefone", telefone);
        payload.put("clienteNome", agendamento.getClienteNome());
        payload.put("servicoNome", agendamento.getServicoNome());
        payload.put("profissionalNome", agendamento.getProfissionalNome());
        payload.put("data", agendamento.getData() == null ? null : agendamento.getData().toString());
        payload.put("horario", agendamento.getHoraInicio() == null ? null : String.format("%02d:%02d", agendamento.getHoraInicio().getHour(), agendamento.getHoraInicio().getMinute()));
        payload.put("mensagem", mensagem);

        nodeClient.enviarLembrete(payload);
        log.info("[Reminder] lembrete enviado empresaId={} agendamentoId={} protocolo={} horario={}",
                agendamento.getEmpresaId(), agendamento.getId(), agendamento.getProtocolo(), agendamento.getHoraInicio());
        */
        return;
    }

    private String montarMensagem(AgendamentoRepository.AgendamentoLembreteProjection agendamento) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        String cliente = textoOuPadrao(agendamento.getClienteNome(), "Cliente");
        String servico = textoOuPadrao(agendamento.getServicoNome(), "seu atendimento");
        String profissional = textoOuPadrao(agendamento.getProfissionalNome(), "-");
        String horario = agendamento.getHoraInicio() == null ? "--:--" : String.format("%02d:%02d", agendamento.getHoraInicio().getHour(), agendamento.getHoraInicio().getMinute());

        StringBuilder builder = new StringBuilder();
        builder.append("Olá, ").append(cliente).append("! Tudo bem?\n\n")
                .append("Só passando para lembrar do seu agendamento de hoje:\n\n")
                .append("Serviço: ").append(servico).append('\n');
        if (!"-".equals(profissional)) {
            builder.append("Profissional: ").append(profissional).append('\n');
        }
        builder.append("Horário: ").append(horario).append('\n')
                .append("\nEsperamos você no horário marcado. \n\n")
                .append("Se precisar cancelar ou reagendar, responda esta mensagem.");
        return builder.toString();
        */
        return "";
    }

    private String textoOuPadrao(String valor, String padrao) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        String texto = valor == null ? "" : valor.trim();
        return texto.isBlank() ? padrao : texto;
        */
        return padrao;
    }

    /**
     * Normaliza telefone para o formato 55DDNNNNNNNNN (13 dígitos).
     * Aceita formatos como: 5565992700672, 65992700672, (65)99270-0672, +55 65 99270-0672.
     * Retorna null se o número não puder ser normalizado para 13 dígitos com DDI 55.
     */
    static String normalizarTelefone(String telefone) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        if (telefone == null || telefone.isBlank()) return null;
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.isEmpty()) return null;
        if (!digitos.startsWith("55")) {
            digitos = "55" + digitos;
        }
        if (digitos.length() == 12 && digitos.startsWith("55")) {
            digitos = digitos.substring(0, 4) + "9" + digitos.substring(4);
        }
        if (digitos.length() != 13) return null;
        int ddd = Integer.parseInt(digitos.substring(2, 4));
        if (ddd < 11 || ddd > 99) return null;
        return digitos;
        */
        return null;
    }

    private ZoneId resolverZoneId(String timezone) {
        // DESATIVADO - Funcionalidade WhatsApp comentada
        /*
        String valor = timezone == null || timezone.isBlank()
                ? appTimezone
                : timezone;
        if (valor == null || valor.isBlank()) {
            valor = TimezoneEnum.AMERICA_CUIABA.getValue();
        }
        return ZoneId.of(valor);
        */
        return ZoneId.of("America/Cuiaba");
    }
}

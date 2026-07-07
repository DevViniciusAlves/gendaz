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

    @Scheduled(fixedDelay = 300000)
    public void keepAlive() {
        log.info("[keep-alive] ping ok");
    }

    @Scheduled(fixedDelay = 60000)
    public void enviarLembretes() {
        log.info("[Scheduler] enviarLembretes() executando");

        try {
            List<EmpresaEntity> empresas = empresaRepository.findByWhatsappConnectedTrue();
            log.info("[Scheduler] encontradas {} empresas com whatsapp conectado", empresas.size());
            for (EmpresaEntity empresa : empresas) {
                ZoneId zoneId = resolverZoneId(empresa.getTimezone());
                ZonedDateTime agoraZoned = ZonedDateTime.now(zoneId);
                LocalDateTime agora = agoraZoned.toLocalDateTime();
                LocalDateTime dataLimiteInferior = agora.plusMinutes(25);
                LocalDateTime dataLimiteSuperior = agora.plusMinutes(35);
                LocalDate data = dataLimiteInferior.toLocalDate();
                LocalTime inicio = dataLimiteInferior.toLocalTime();
                LocalTime fim = dataLimiteSuperior.toLocalTime();

                log.info("[Reminder] timezone={} agora={} empresaId={} buscando lembretes data={} janela={}..{}",
                        zoneId, agoraZoned, empresa.getId(), data, inicio, fim);

                List<AgendamentoRepository.AgendamentoLembreteProjection> agendamentos = agendamentoRepository.findLembretesClienteProjection(
                        empresa.getId(),
                        List.of(StatusAgendamento.PENDENTE, StatusAgendamento.CONFIRMADO),
                        data,
                        inicio,
                        fim
                );

                for (AgendamentoRepository.AgendamentoLembreteProjection agendamento : agendamentos) {
                    try {
                        tentarEnviarLembrete(agendamento);

                        log.info("[Scheduler-Delay] aguardando 10 segundos antes do proximo lembrete...");
                        try {
                            Thread.sleep(10000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    } catch (RuntimeException ex) {
                        log.warn("[Reminder] falha ao enviar lembrete agendamentoId={} detalhe={}",
                                agendamento.getId(), ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("[Scheduler] ERRO GERAL NO SCHEDULER", e);
            e.printStackTrace();
        }
    }

    private void tentarEnviarLembrete(AgendamentoRepository.AgendamentoLembreteProjection agendamento) {
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
    }

    private String montarMensagem(AgendamentoRepository.AgendamentoLembreteProjection agendamento) {
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
                .append("\nEsperamos você no horário marcado. ✅\n\n")
                .append("Se precisar cancelar ou reagendar, responda esta mensagem.");
        return builder.toString();
    }

    private String textoOuPadrao(String valor, String padrao) {
        String texto = valor == null ? "" : valor.trim();
        return texto.isBlank() ? padrao : texto;
    }

    /**
     * Normaliza telefone para o formato 55DDNNNNNNNNN (13 dígitos).
     * Aceita formatos como: 5565992700672, 65992700672, (65)99270-0672, +55 65 99270-0672.
     * Retorna null se o número não puder ser normalizado para 13 dígitos com DDI 55.
     */
    static String normalizarTelefone(String telefone) {
        if (telefone == null || telefone.isBlank()) return null;
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.isEmpty()) return null;
        // Já está no formato correto: 55 + DDD (2) + número (9) = 13 dígitos
        if (digitos.length() == 13 && digitos.startsWith("55")) {
            return digitos;
        }
        // DDD + 9 dígitos (11 dígitos sem DDI)
        if (digitos.length() == 11 && !digitos.startsWith("55")) {
            return "55" + digitos;
        }
        // DDD + 8 dígitos (10 dígitos sem DDI — número antigo sem o 9)
        if (digitos.length() == 10 && !digitos.startsWith("55")) {
            return "55" + digitos;
        }
        // Tem 55 mas o nacional tem 10 dígitos (sem o 9 extra)
        if (digitos.startsWith("55") && digitos.length() == 12) {
            String nacional = digitos.substring(2); // 10 dígitos
            return "55" + nacional;
        }
        // Tem 55 e nacional tem 11 dígitos com 9 extra (ex: 5565992700672 = 13, já tratado acima)
        // Caso raro: começa com 0 antes do DDD
        if (digitos.startsWith("0") && digitos.length() == 12) {
            return "55" + digitos.substring(1);
        }
        return null;
    }

    private ZoneId resolverZoneId(String timezone) {
        String valor = timezone == null || timezone.isBlank()
                ? appTimezone
                : timezone;
        if (valor == null || valor.isBlank()) {
            valor = TimezoneEnum.AMERICA_CUIABA.getValue();
        }
        return ZoneId.of(valor);
    }
}

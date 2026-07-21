/*
  ╔══════════════════════════════════════════════╗
  ║    DESATIVADO - FUNCIONALIDADE WhatsApp    ║
  ║  Todo código comentado. Remova comentários   ║
  ║  para reativar.                              ║
  ╚══════════════════════════════════════════════╝
*/
package com.minhaempresa.agendapro.whatsapp.service;

import com.minhaempresa.agendapro.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.agendapro.agendamento.enums.StatusAgendamento;
import com.minhaempresa.agendapro.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.repository.EmpresaRepository;
import com.minhaempresa.agendapro.pagamento.entity.PagamentoEntity;
import com.minhaempresa.agendapro.pagamento.enums.StatusPagamento;
import com.minhaempresa.agendapro.pagamento.repository.PagamentoRepository;
import com.minhaempresa.agendapro.shared.enums.TimezoneEnum;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfirmacaoPagamentoDonoWhatsappService {
    private final AgendamentoRepository agendamentoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final EmpresaRepository empresaRepository;
    private final WhatsappNodeClient nodeClient;
    @Value("${app.timezone:America/Cuiaba}")
    private String appTimezone;

    /*
    @Scheduled(fixedDelay = 60000)
    public void processarConfirmacoesPagamentoDono() {
        List<EmpresaEntity> empresas = empresaRepository.findByWhatsappConnectedTrue();
        for (EmpresaEntity empresa : empresas) {
            try {
                processarEmpresa(empresa);
            } catch (Exception ex) {
                log.warn("[confirmacao-pagamento-dono] falha ao processar empresaId={} detalhe={}", empresa.getId(), ex.getMessage());
            }
        }
    }
    */

    // public void processarConfirmacoesPagamentoDono() { }

    public void enviarLembrete(Long empresaId, Long agendamentoId, boolean segundoLembrete) {
        /*
        EmpresaEntity empresa = empresaRepository.findById(empresaId).orElse(null);
        AgendamentoEntity agendamento = agendamentoRepository.findById(agendamentoId).orElse(null);
        if (empresa == null || agendamento == null || agendamento.getEmpresa() == null || !empresaId.equals(agendamento.getEmpresa().getId())) {
            throw new IllegalArgumentException("Agendamento ou empresa invalido.");
        }
        if (!Boolean.TRUE.equals(empresa.getWhatsappConnected())) {
            throw new IllegalStateException("Empresa sem WhatsApp conectado.");
        }
        if (!enviarParaNodeDaEntidade(empresa, agendamento, segundoLembrete)) {
            throw new IllegalStateException("Falha ao enviar lembrete.");
        }
        marcarEnviadoDaEntidade(agendamento, segundoLembrete);
        */
    }

    private boolean enviarParaNodeDaEntidade(EmpresaEntity empresa, AgendamentoEntity agendamento, boolean segundoLembrete) {
        /*
        return enviarParaNode(empresa, toProjection(agendamento), segundoLembrete);
        */
        return false;
    }

    private void processarEmpresa(EmpresaEntity empresa) {
        /*
        ZoneId zoneId = resolverZoneId(empresa.getTimezone());
        LocalDateTime agora = ZonedDateTime.now(zoneId).toLocalDateTime();

        // 1o envio: agendamentos cujo horaInicio está entre (agora - 10min) e (agora - 4min)
        LocalDate data1 = agora.toLocalDate();
        LocalTime inicio1 = agora.minusMinutes(10).toLocalTime();
        LocalTime fim1   = agora.minusMinutes(4).toLocalTime();

        // 2o envio (reforço): agendamentos cujo horaInicio está entre (agora - 20min) e (agora - 14min)
        LocalDate data2 = agora.toLocalDate();
        LocalTime inicio2 = agora.minusMinutes(20).toLocalTime();
        LocalTime fim2   = agora.minusMinutes(14).toLocalTime();

        log.info("[confirmacao-pagamento-dono] timezone={} agora={} empresaId={} "
                + "1o envio horaInicio=[{}..{}] | 2o envio horaInicio=[{}..{}]",
                zoneId, agora, empresa.getId(), inicio1, fim1, inicio2, fim2);

        List<AgendamentoEntity> agendamentos1 = agendamentoRepository
                .findConfirmacaoPagamentoPendente(
                        empresa.getId(),
                        List.of(StatusAgendamento.PENDENTE, StatusAgendamento.CONFIRMADO),
                        data1,
                        inicio1,
                        fim1);

        for (AgendamentoEntity agendamento : agendamentos1) {
            try {
                if (Boolean.TRUE.equals(agendamento.getConfirmacaoPagamentoDonoEnviada())) {
                    continue;
                }
                if (Boolean.TRUE.equals(agendamento.getConfirmacaoPagamentoDonoRespondida())) {
                    continue;
                }
                if (enviarParaNodeDaEntidade(empresa, agendamento, false)) {
                    marcarPrimeiroEnviadoDaEntidade(agendamento);
                    log.info("[confirmacao-pagamento-dono] 1o envio realizado agendamentoId={}", agendamento.getId());
                }
            } catch (RuntimeException ex) {
                log.warn("[confirmacao-pagamento-dono] erro no 1o envio agendamentoId={}: {}",
                        agendamento.getId(), ex.getMessage());
            }
        }

        List<AgendamentoEntity> agendamentos2 = agendamentoRepository
                .findConfirmacaoPagamentoPendente(
                        empresa.getId(),
                        List.of(StatusAgendamento.PENDENTE, StatusAgendamento.CONFIRMADO),
                        data2,
                        inicio2,
                        fim2);

        for (AgendamentoEntity agendamento : agendamentos2) {
            try {
                if (!Boolean.TRUE.equals(agendamento.getConfirmacaoPagamentoDonoEnviada())) {
                    continue;
                }
                if (Boolean.TRUE.equals(agendamento.getConfirmacaoPagamentoDono2Enviada())) {
                    continue;
                }
                if (Boolean.TRUE.equals(agendamento.getConfirmacaoPagamentoDonoRespondida())) {
                    continue;
                }
                PagamentoEntity pagamento = pagamentoRepository.findByAgendamento_Id(agendamento.getId()).orElse(null);
                if (pagamento == null || isPago(pagamento.getStatus())) {
                    continue;
                }
                if (enviarParaNodeDaEntidade(empresa, agendamento, true)) {
                    agendamento.setConfirmacaoPagamentoDono2Enviada(Boolean.TRUE);
                    agendamentoRepository.save(agendamento);
                    log.info("[confirmacao-pagamento-dono] 2o envio (reforco) realizado agendamentoId={}",
                            agendamento.getId());
                }
            } catch (RuntimeException ex) {
                log.warn("[confirmacao-pagamento-dono] erro no 2o envio agendamentoId={}: {}",
                        agendamento.getId(), ex.getMessage());
            }
        }
        */
    }

    private boolean deveEnviarPrimeiro(AgendamentoRepository.AgendamentoLembreteProjection agendamento, LocalDateTime dataLimiteInferior, LocalDateTime dataLimiteSuperior) {
        /*
        if (agendamento == null || agendamento.getEmpresaId() == null || agendamento.getData() == null || agendamento.getHoraInicio() == null) {
            return false;
        }
        if (agendamento.getStatus() == StatusAgendamento.CANCELADO) {
            return false;
        }
        if (Boolean.TRUE.equals(agendamento.getConfirmacaoPagamentoDonoEnviada())) {
            return false;
        }
        if (Boolean.TRUE.equals(agendamento.getConfirmacaoPagamentoDonoRespondida())) {
            return false;
        }
        LocalDateTime horarioAgendamento = LocalDateTime.of(agendamento.getData(), agendamento.getHoraInicio());
        return !horarioAgendamento.isBefore(dataLimiteInferior) && !horarioAgendamento.isAfter(dataLimiteSuperior);
        */
        return false;
    }

    private boolean deveEnviarSegundo(AgendamentoRepository.AgendamentoLembreteProjection agendamento, LocalDateTime dataLimiteInferior, LocalDateTime dataLimiteSuperior) {
        /*
        if (agendamento == null || agendamento.getEmpresaId() == null || agendamento.getData() == null || agendamento.getHoraInicio() == null) {
            return false;
        }
        if (agendamento.getStatus() == StatusAgendamento.CANCELADO) {
            return false;
        }
        if (!Boolean.TRUE.equals(agendamento.getConfirmacaoPagamentoDonoEnviada())) {
            return false;
        }
        if (Boolean.TRUE.equals(agendamento.getSegundaConfirmacaoPagamentoDonoEnviada())) {
            return false;
        }
        if (Boolean.TRUE.equals(agendamento.getConfirmacaoPagamentoDonoRespondida())) {
            return false;
        }
        LocalDateTime enviadaEm = agendamento.getConfirmacaoPagamentoDonoEnviadaEm();
        return enviadaEm != null
                && !enviadaEm.isBefore(dataLimiteInferior)
                && !enviadaEm.isAfter(dataLimiteSuperior);
        */
        return false;
    }

    private boolean enviarParaNode(EmpresaEntity empresa, AgendamentoRepository.AgendamentoLembreteProjection agendamento, boolean segundoLembrete) {
        /*
        PagamentoEntity pagamento = pagamentoRepository.findByAgendamento_Id(agendamento.getId()).orElse(null);
        if (pagamento == null) {
            log.warn("[confirmacao-pagamento-dono] ignorado sem pagamento empresaId={} agendamentoId={}", empresa.getId(), agendamento.getId());
            return false;
        }
        if (isPago(pagamento.getStatus())) {
            return false;
        }
        String telefoneCliente = textoOuPadrao(agendamento.getClienteTelefone(), "");
        Map<String, Object> payload = new HashMap<>();
        payload.put("empresaId", empresa.getId());
        payload.put("agendamentoId", agendamento.getId());
        payload.put("protocolo", agendamento.getProtocolo());
        payload.put("clienteNome", textoOuPadrao(agendamento.getClienteNome(), ""));
        payload.put("clienteTelefone", telefoneCliente);
        payload.put("servicoNome", textoOuPadrao(agendamento.getServicoNome(), ""));
        payload.put("profissionalNome", textoOuPadrao(agendamento.getProfissionalNome(), ""));
        payload.put("data", agendamento.getData() == null ? null : agendamento.getData().toString());
        payload.put("horario", agendamento.getHoraInicio() == null ? null : String.format("%02d:%02d", agendamento.getHoraInicio().getHour(), agendamento.getHoraInicio().getMinute()));
        payload.put("segundoLembrete", segundoLembrete);
        payload.put("mensagem", montarMensagem(agendamento, segundoLembrete));

        try {
            nodeClient.enviarConfirmacaoPagamentoDono(payload);
            log.info("[confirmacao-pagamento-dono] lembrete enviado empresaId={} agendamentoId={} segundoLembrete={}",
                    empresa.getId(), agendamento.getId(), segundoLembrete);
            return true;
        } catch (RuntimeException ex) {
            log.warn("[confirmacao-pagamento-dono] envio falhou empresaId={} agendamentoId={} detalhe={}",
                    empresa.getId(), agendamento.getId(), ex.getMessage());
            return false;
        }
        */
        return false;
    }

    private void marcarPrimeiroEnviado(AgendamentoRepository.AgendamentoLembreteProjection agendamento) {
        /*
        AgendamentoEntity entity = agendamentoRepository.findById(agendamento.getId()).orElseThrow();
        entity.setConfirmacaoPagamentoDonoEnviada(Boolean.TRUE);
        entity.setConfirmacaoPagamentoDonoEnviadaEm(LocalDateTime.now(resolverZoneId(entity.getEmpresa() == null ? null : entity.getEmpresa().getTimezone())));
        agendamentoRepository.save(entity);
        */
    }

    private void marcarSegundoEnviado(AgendamentoRepository.AgendamentoLembreteProjection agendamento) {
        /*
        AgendamentoEntity entity = agendamentoRepository.findById(agendamento.getId()).orElseThrow();
        entity.setSegundaConfirmacaoPagamentoDonoEnviada(Boolean.TRUE);
        entity.setSegundaConfirmacaoPagamentoDonoEnviadaEm(LocalDateTime.now(resolverZoneId(entity.getEmpresa() == null ? null : entity.getEmpresa().getTimezone())));
        agendamentoRepository.save(entity);
        */
    }

    private void marcarEnviado(AgendamentoRepository.AgendamentoLembreteProjection agendamento, boolean segundoLembrete) {
        /*
        if (segundoLembrete) {
            marcarSegundoEnviado(agendamento);
        } else {
            marcarPrimeiroEnviado(agendamento);
        }
        */
    }

    private void marcarPrimeiroEnviadoDaEntidade(AgendamentoEntity agendamento) {
        /*
        agendamento.setConfirmacaoPagamentoDonoEnviada(Boolean.TRUE);
        agendamento.setConfirmacaoPagamentoDonoEnviadaEm(LocalDateTime.now(resolverZoneId(agendamento.getEmpresa() == null ? null : agendamento.getEmpresa().getTimezone())));
        agendamentoRepository.save(agendamento);
        */
    }

    private void marcarSegundoEnviadoDaEntidade(AgendamentoEntity agendamento) {
        /*
        agendamento.setSegundaConfirmacaoPagamentoDonoEnviada(Boolean.TRUE);
        agendamento.setSegundaConfirmacaoPagamentoDonoEnviadaEm(LocalDateTime.now(resolverZoneId(agendamento.getEmpresa() == null ? null : agendamento.getEmpresa().getTimezone())));
        agendamentoRepository.save(agendamento);
        */
    }

    private void marcarEnviadoDaEntidade(AgendamentoEntity agendamento, boolean segundoLembrete) {
        /*
        if (segundoLembrete) {
            marcarSegundoEnviadoDaEntidade(agendamento);
        } else {
            marcarPrimeiroEnviadoDaEntidade(agendamento);
        }
        */
    }

    private boolean isPago(StatusPagamento status) {
        /*
        return status == StatusPagamento.PAGO || status == StatusPagamento.PAYMENT_APPROVED;
        */
        return false;
    }

    private String montarMensagem(AgendamentoRepository.AgendamentoLembreteProjection agendamento, boolean segundoLembrete) {
        /*
        String clienteNome = textoOuPadrao(agendamento.getClienteNome(), "Cliente");
        String clienteTelefone = textoOuPadrao(agendamento.getClienteTelefone(), "-");
        String protocolo = textoOuPadrao(agendamento.getProtocolo(), "------");
        String servicoNome = textoOuPadrao(agendamento.getServicoNome(), "seu atendimento");
        String profissionalNome = textoOuPadrao(agendamento.getProfissionalNome(), "-");
        String data = agendamento.getData() == null ? "--/--/----" : agendamento.getData().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String horario = agendamento.getHoraInicio() == null ? "--:--" : String.format("%02d:%02d", agendamento.getHoraInicio().getHour(), agendamento.getHoraInicio().getMinute());

        StringBuilder builder = new StringBuilder();
        if (segundoLembrete) {
            builder.append("Só confirmando novamente:\n\n");
        }
        builder.append("Olá! O atendimento abaixo já foi finalizado.\n\n")
                .append("Cliente: ").append(clienteNome).append('\n')
                .append("Telefone do cliente: ").append(clienteTelefone).append('\n')
                .append("Protocolo: ").append(protocolo).append('\n')
                .append("Serviço: ").append(servicoNome).append('\n')
                .append("Profissional: ").append(profissionalNome).append('\n')
                .append("Data: ").append(data).append('\n')
                .append("Horário: ").append(horario).append('\n')
                .append('\n')
                .append("Esse atendimento foi pago?\n\n")
                .append("Responda:\n")
                .append("1. Sim, foi pago\n")
                .append("2. Não, ficou pendente\n")
                .append("3. Foi cancelado");
        return builder.toString();
        */
        return "";
    }

    private AgendamentoRepository.AgendamentoLembreteProjection toProjection(AgendamentoEntity agendamento) {
        /*
        return new AgendamentoRepository.AgendamentoLembreteProjection() {
            @Override
            public Long getId() {
                return agendamento.getId();
            }

            @Override
            public Long getEmpresaId() {
                return agendamento.getEmpresa() == null ? null : agendamento.getEmpresa().getId();
            }

            @Override
            public Boolean getEmpresaWhatsappConnected() {
                return agendamento.getEmpresa() == null ? null : agendamento.getEmpresa().getWhatsappConnected();
            }

            @Override
            public String getClienteNome() {
                return agendamento.getCliente() == null ? null : agendamento.getCliente().getNome();
            }

            @Override
            public String getClienteTelefone() {
                return agendamento.getCliente() == null ? null : agendamento.getCliente().getTelefone();
            }

            @Override
            public String getServicoNome() {
                return agendamento.getServico() == null ? null : agendamento.getServico().getNome();
            }

            @Override
            public String getProfissionalNome() {
                return agendamento.getProfissional() == null ? null : agendamento.getProfissional().getNome();
            }

            @Override
            public LocalDate getData() {
                return agendamento.getData();
            }

            @Override
            public LocalTime getHoraInicio() {
                return agendamento.getHoraInicio();
            }

            @Override
            public String getProtocolo() {
                return agendamento.getProtocolo();
            }

            @Override
            public Boolean getLembreteWppEnviado() {
                return agendamento.getLembreteWppEnviado();
            }

            @Override
            public StatusAgendamento getStatus() {
                return agendamento.getStatus();
            }

            @Override
            public Boolean getConfirmacaoPagamentoDonoEnviada() {
                return agendamento.getConfirmacaoPagamentoDonoEnviada();
            }

            @Override
            public LocalDateTime getConfirmacaoPagamentoDonoEnviadaEm() {
                return agendamento.getConfirmacaoPagamentoDonoEnviadaEm();
            }

            @Override
            public Boolean getSegundaConfirmacaoPagamentoDonoEnviada() {
                return agendamento.getSegundaConfirmacaoPagamentoDonoEnviada();
            }

            @Override
            public LocalDateTime getSegundaConfirmacaoPagamentoDonoEnviadaEm() {
                return agendamento.getSegundaConfirmacaoPagamentoDonoEnviadaEm();
            }

            @Override
            public Boolean getConfirmacaoPagamentoDonoRespondida() {
                return agendamento.getConfirmacaoPagamentoDonoRespondida();
            }

            @Override
            public LocalDateTime getConfirmacaoPagamentoDonoRespondidaEm() {
                return agendamento.getConfirmacaoPagamentoDonoRespondidaEm();
            }
        };
        */
        return null;
    }

    private String textoOuPadrao(String valor, String padrao) {
        /*
        String texto = valor == null ? "" : valor.trim();
        return texto.isBlank() ? padrao : texto;
        */
        return padrao;
    }

    private ZoneId resolverZoneId(String timezone) {
        /*
        String valor = timezone == null || timezone.isBlank()
                ? appTimezone
                : timezone;
        if (valor == null || valor.isBlank()) {
            valor = TimezoneEnum.AMERICA_CUIABA.getValue();
        }
        return ZoneId.of(valor);
        */
        return ZoneId.of(TimezoneEnum.AMERICA_CUIABA.getValue());
    }
}

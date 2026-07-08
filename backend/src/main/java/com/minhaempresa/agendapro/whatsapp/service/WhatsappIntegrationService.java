package com.minhaempresa.agendapro.whatsapp.service;

import com.minhaempresa.agendapro.admin.service.AdminAuditService;
import com.minhaempresa.agendapro.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.agendapro.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.agendapro.agendamento.service.AgendamentoService;
import com.minhaempresa.agendapro.cliente.entity.ClienteEntity;
import com.minhaempresa.agendapro.cliente.repository.ClienteRepository;
import com.minhaempresa.agendapro.configuracao.dto.HorarioAtendimentoDtos.HorarioAtendimentoResponse;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.repository.EmpresaRepository;
import com.minhaempresa.agendapro.horarioatendimento.service.HorarioAtendimentoService;
import com.minhaempresa.agendapro.profissional.entity.ProfissionalEntity;
import com.minhaempresa.agendapro.profissional.repository.ProfissionalRepository;
import com.minhaempresa.agendapro.pagamento.entity.PagamentoEntity;
import com.minhaempresa.agendapro.pagamento.enums.StatusPagamento;
import com.minhaempresa.agendapro.pagamento.repository.PagamentoRepository;
import com.minhaempresa.agendapro.servico.entity.ServicoEntity;
import com.minhaempresa.agendapro.servico.repository.ServicoRepository;
import com.minhaempresa.agendapro.shared.enums.StatusCadastro;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.shared.ConflictException;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
import com.minhaempresa.agendapro.usuario.entity.UsuarioEntity;
import com.minhaempresa.agendapro.usuario.repository.UsuarioRepository;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.ClienteContextResponse;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.AgendamentoContextResponse;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.EmpresaContextResponse;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.SendTestMessageRequest;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.SendTestMessageResponse;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappAgendarRequest;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappAgendarResponse;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappAgendarResumoResponse;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappDisponibilidadeResponse;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappSessionResponse;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappSessionSaveRequest;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappSessionSummaryResponse;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappAgendamentoIaRequest;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappConfigResponse;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappFluxoConversaRequest;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappFluxoConversaResponse;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappConnectRequest;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappConnectResponse;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappContextResponse;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.ProfissionalContextResponse;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappPreferenciasRequest;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappStatusResponse;
import com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.WhatsappServicoResposta;
import com.minhaempresa.agendapro.whatsapp.entity.WhatsappConnectionEntity;
import com.minhaempresa.agendapro.whatsapp.entity.WhatsappConversationEntity;
import com.minhaempresa.agendapro.whatsapp.entity.WhatsappFluxoConversaEntity;
import com.minhaempresa.agendapro.whatsapp.entity.WhatsappMessageEntity;
import com.minhaempresa.agendapro.whatsapp.entity.WhatsappSessionEntity;
import com.minhaempresa.agendapro.whatsapp.enums.WhatsappConnectionStatus;
import com.minhaempresa.agendapro.whatsapp.enums.WhatsappConversationStatus;
import com.minhaempresa.agendapro.whatsapp.enums.WhatsappMessageDirection;
import com.minhaempresa.agendapro.whatsapp.enums.WhatsappMessageStatus;
import com.minhaempresa.agendapro.whatsapp.enums.WhatsappProvider;
import com.minhaempresa.agendapro.whatsapp.repository.WhatsappConnectionRepository;
import com.minhaempresa.agendapro.whatsapp.repository.WhatsappConversationRepository;
import com.minhaempresa.agendapro.whatsapp.repository.WhatsappFluxoConversaRepository;
import com.minhaempresa.agendapro.whatsapp.repository.WhatsappMessageRepository;
import com.minhaempresa.agendapro.whatsapp.repository.WhatsappSessionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@EnableCaching
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsappIntegrationService {
    private final WhatsappIntegrationProperties properties;
    private final WhatsappNodeClient nodeClient;
    private final WhatsappConnectionRepository connectionRepository;
    private final WhatsappConversationRepository conversationRepository;
    private final WhatsappFluxoConversaRepository fluxoConversaRepository;
    private final WhatsappMessageRepository messageRepository;
    private final WhatsappSessionRepository sessionRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final ServicoRepository servicoRepository;
    private final ProfissionalRepository profissionalRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final HorarioAtendimentoService horarioAtendimentoService;
    private final AgendamentoService agendamentoService;
    private final AdminAuditService auditService;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend-url:https://gendaz.site}")
    private String frontendUrl;

    @Value("${app.timezone:America/Cuiaba}")
    private String appTimezone;

    public WhatsappStatusResponse status(Long usuarioId) {
        UsuarioEntity usuario = buscarUsuarioComEmpresa(usuarioId);
        return statusDaEmpresa(usuario.getEmpresa().getId());
    }

    @Cacheable(value = "contextoEmpresa", key = "#empresaId", unless = "#result == null")
    public WhatsappConfigResponse contextoDaEmpresa(Long empresaId) {
        EmpresaRepository.WhatsappConfigView empresa = buscarEmpresaConfig(empresaId);
        return montarConfiguracaoWhatsapp(
                empresa,
                Boolean.TRUE.equals(empresa.getWhatsappConnected()),
                empresa.getWhatsappPhone()
        );
    }

    @Transactional(readOnly = true)
    public List<WhatsappServicoResposta> listarServicosWhatsApp(Long empresaId) {
        EmpresaEntity empresa = buscarEmpresa(empresaId);
        return servicoRepository.findByEmpresaId(empresa.getId()).stream()
                .filter(servico -> servico.getStatus() == StatusCadastro.ATIVO)
                .map(servico -> new WhatsappServicoResposta(
                        servico.getId(),
                        servico.getNome(),
                        servico.getValor(),
                        servico.getDuracaoMinutos()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public WhatsappDisponibilidadeResponse consultarDisponibilidadeWhatsApp(Long empresaId, Long servicoId, Long profissionalId, LocalDate data) {
        EmpresaEntity empresa = buscarEmpresa(empresaId);
        ZoneId zoneId = ZoneId.of(appTimezone == null || appTimezone.isBlank() ? "America/Cuiaba" : appTimezone);
        LocalDate hoje = LocalDate.now(zoneId);
        LocalTime agora = LocalTime.now(zoneId);
        ServicoEntity servico = servicoRepository.findById(servicoId)
                .orElseThrow(() -> new ResourceNotFoundException("Servico nao encontrado."));
        if (!servico.getEmpresa().getId().equals(empresa.getId()) || servico.getStatus() != StatusCadastro.ATIVO) {
            throw new BusinessException("Servico indisponivel.");
        }
        ProfissionalEntity profissional = null;
        if (profissionalId != null) {
            profissional = profissionalRepository.findById(profissionalId)
                    .orElseThrow(() -> new ResourceNotFoundException("Profissional nao encontrado."));
            if (!profissional.getEmpresa().getId().equals(empresa.getId()) || profissional.getStatus() != StatusCadastro.ATIVO) {
                throw new BusinessException("Profissional indisponivel.");
            }
        }
        if (data == null || data.isBefore(hoje)) {
            return new WhatsappDisponibilidadeResponse(data, false, List.of());
        }
        List<String> horarios = new ArrayList<>(
                agendamentoService.horariosDisponiveis(empresa.getId(), profissional == null ? null : profissional.getId(), servicoId, data)
        );
        if (data.isEqual(hoje)) {
            int limiteMinimo = agora.getHour() * 60 + agora.getMinute() + 5;
            horarios = horarios.stream()
                    .filter(horario -> {
                        if (horario == null || horario.isBlank()) {
                            return false;
                        }
                        String[] partes = horario.split(":");
                        if (partes.length < 2) {
                            return false;
                        }
                        try {
                            int hora = Integer.parseInt(partes[0]);
                            int minuto = Integer.parseInt(partes[1]);
                            int minutosHorario = (hora * 60) + minuto;
                            return minutosHorario > limiteMinimo;
                        } catch (NumberFormatException ex) {
                            return false;
                        }
                    })
                    .toList();
        }
        return new WhatsappDisponibilidadeResponse(data, !horarios.isEmpty(), horarios);
    }

    @Transactional
    public WhatsappAgendarResponse agendarWhatsApp(WhatsappAgendarRequest request) {
        log.info(
                "[agendamento-whatsapp] criando empresaId={} clienteNome='{}' servicoId={} data={} horario={} remoteJid='{}' origem='{}'",
                request.empresaId(),
                request.nomeCliente(),
                request.servicoId(),
                request.data(),
                request.horario(),
                request.remoteJid(),
                request.origem()
        );
        EmpresaEntity empresa = buscarEmpresa(request.empresaId());
        ServicoEntity servico = servicoRepository.findById(request.servicoId())
                .orElseThrow(() -> new ResourceNotFoundException("Servico nao encontrado."));
        if (!servico.getEmpresa().getId().equals(empresa.getId()) || servico.getStatus() != StatusCadastro.ATIVO) {
            throw new BusinessException("Servico indisponivel.");
        }
        ProfissionalEntity profissional = null;
        if (request.profissionalId() != null) {
            profissional = profissionalRepository.findById(request.profissionalId())
                    .orElseThrow(() -> new ResourceNotFoundException("Profissional nao encontrado."));
            if (!profissional.getEmpresa().getId().equals(empresa.getId()) || profissional.getStatus() != StatusCadastro.ATIVO) {
                throw new BusinessException("Profissional indisponivel.");
            }
        }
        if (request.data() == null || request.horario() == null || request.horario().isBlank()) {
            throw new BusinessException("Informe data e horario validos.");
        }
        LocalTime horaInicio = LocalTime.parse(request.horario());
        List<String> horariosDisponiveis = agendamentoService.horariosDisponiveis(empresa.getId(), profissional == null ? null : profissional.getId(), servico.getId(), request.data());
        if (!horariosDisponiveis.contains(horaInicio.toString())) {
            throw new ConflictException("Esse horario acabou de ficar indisponivel.");
        }

        ClienteEntity cliente = clienteRepository.findFirstByEmpresaIdAndTelefone(empresa.getId(), normalizarTelefone(request.telefoneCliente()))
                .orElseGet(() -> clienteRepository.save(ClienteEntity.builder()
                        .empresa(empresa)
                        .nome(textoOuPadrao(request.nomeCliente(), "Cliente WhatsApp"))
                        .telefone(normalizarTelefone(request.telefoneCliente()))
                        .build()));
        cliente.setNome(textoOuPadrao(request.nomeCliente(), cliente.getNome()));
        cliente.setTelefone(normalizarTelefone(request.telefoneCliente()));
        clienteRepository.save(cliente);

        try {
            var agendamento = agendamentoService.criar(new com.minhaempresa.agendapro.agendamento.dto.AgendamentoDtos.CriarAgendamentoRequest(
                    cliente.getId(),
                    servico.getId(),
                    profissional == null ? null : profissional.getId(),
                    empresa.getId(),
                    request.data(),
                    horaInicio,
                    "Agendamento criado pelo WhatsApp."
            ));
            if (agendamento.protocolo() == null || agendamento.protocolo().isBlank()) {
                log.error("[agendamento-whatsapp] protocolo ausente apos criacao empresaId={} agendamentoId={}", empresa.getId(), agendamento.id());
            }
            return new WhatsappAgendarResponse(
                    true,
                    agendamento.id(),
                    agendamento.protocolo(),
                    "Agendamento confirmado com sucesso.",
                    new WhatsappAgendarResumoResponse(
                            servico.getNome(),
                            request.data(),
                            horaInicio.toString().substring(0, 5)
                    )
            );
        } catch (ConflictException ex) {
            throw new ConflictException("Esse horario acabou de ficar indisponivel.");
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buscarAgendamentoCancelamentoPorProtocolo(Long empresaId, String protocolo, String telefone) {
        log.info("[whatsapp-cancelamento] buscar por protocolo empresaId={} protocolo={}", empresaId, protocolo);
        String protocoloNormalizado = textoOuNulo(protocolo);
        if (protocoloNormalizado == null || protocoloNormalizado.isBlank()) {
            Map<String, Object> erro = new HashMap<>();
            erro.put("success", false);
            erro.put("erro", "AGENDAMENTO_NAO_ENCONTRADO");
            erro.put("mensagem", "Não encontrei nenhum agendamento com esse protocolo.");
            return erro;
        }
        AgendamentoEntity agendamento = agendamentoRepository.findByEmpresa_IdAndProtocolo(empresaId, protocoloNormalizado).orElse(null);
        if (agendamento == null) {
            Map<String, Object> erro = new HashMap<>();
            erro.put("success", false);
            erro.put("erro", "AGENDAMENTO_NAO_ENCONTRADO");
            erro.put("mensagem", "Não encontrei nenhum agendamento com esse protocolo.");
            return erro;
        }
        String telefoneNormalizado = normalizarTelefone(telefone);
        boolean telefoneBate = telefoneNormalizado.isBlank()
                || (agendamento.getCliente() != null && telefoneNormalizado.equals(normalizarTelefone(agendamento.getCliente().getTelefone())));
        Map<String, Object> dadosAgendamento = new HashMap<>();
        dadosAgendamento.put("id", agendamento.getId());
        dadosAgendamento.put("protocolo", agendamento.getProtocolo());
        dadosAgendamento.put("clienteNome", agendamento.getCliente() == null ? "" : agendamento.getCliente().getNome());
        dadosAgendamento.put("servicoId", agendamento.getServico() == null ? null : agendamento.getServico().getId());
        dadosAgendamento.put("servicoNome", agendamento.getServico() == null ? "" : agendamento.getServico().getNome());
        dadosAgendamento.put("profissionalId", agendamento.getProfissional() == null ? null : agendamento.getProfissional().getId());
        dadosAgendamento.put("profissionalNome", agendamento.getProfissional() == null ? "" : agendamento.getProfissional().getNome());
        dadosAgendamento.put("data", agendamento.getData());
        dadosAgendamento.put("horario", agendamento.getHoraInicio());
        dadosAgendamento.put("status", agendamento.getStatus() == null ? null : agendamento.getStatus().name());
        dadosAgendamento.put("telefoneBate", telefoneBate);

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("success", true);
        resultado.put("agendamento", dadosAgendamento);
        return resultado;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarAgendamentosCancelamentoPorData(Long empresaId, LocalDate data, String telefone) {
        String telefoneNormalizado = normalizarTelefone(telefone);
        return agendamentoRepository.findByEmpresaIdAndData(empresaId, data).stream()
                .filter(agendamento -> agendamento.getStatus() != null && agendamento.getStatus() != com.minhaempresa.agendapro.agendamento.enums.StatusAgendamento.CANCELADO)
                .filter(agendamento -> telefoneNormalizado.isBlank()
                        || (agendamento.getCliente() != null && telefoneNormalizado.equals(normalizarTelefone(agendamento.getCliente().getTelefone()))))
                .map(agendamento -> Map.<String, Object>of(
                        "id", agendamento.getId(),
                        "protocolo", agendamento.getProtocolo(),
                        "clienteNome", agendamento.getCliente() == null ? null : agendamento.getCliente().getNome(),
                        "servicoNome", agendamento.getServico() == null ? null : agendamento.getServico().getNome(),
                        "data", agendamento.getData(),
                        "horaInicio", agendamento.getHoraInicio(),
                        "status", agendamento.getStatus()
                ))
                .toList();
    }

    @Transactional
    public Map<String, Object> cancelarAgendamentoWhatsapp(Long empresaId, Long agendamentoId) {
        AgendamentoEntity agendamento = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento nao encontrado."));
        if (!agendamento.getEmpresa().getId().equals(empresaId)) {
            throw new BusinessException("Agendamento nao pertence a empresa informada.");
        }
        if (agendamento.getStatus() == com.minhaempresa.agendapro.agendamento.enums.StatusAgendamento.CANCELADO) {
            return Map.of("success", true, "mensagem", "Agendamento já estava cancelado.");
        }
        if (agendamento.getStatus() == com.minhaempresa.agendapro.agendamento.enums.StatusAgendamento.FINALIZADO) {
            throw new BusinessException("Não é possível cancelar um agendamento finalizado.");
        }
        LocalDateTime limite = agendamento.getData().atTime(agendamento.getHoraInicio()).minusMinutes(30);
        if (LocalDateTime.now().isAfter(limite)) {
            return Map.of(
                    "success", false,
                    "erro", "CANCELAMENTO_FORA_DO_PRAZO",
                    "mensagem", "Não é possível cancelar pelo WhatsApp com menos de 30 minutos de antecedência."
            );
        }
        log.info("[whatsapp-cancelamento] cancelar agendamento empresaId={} agendamentoId={}", empresaId, agendamentoId);
        agendamentoService.cancelar(agendamentoId, empresaId);
        return Map.of(
                "success", true,
                "agendamento", Map.of(
                        "id", agendamento.getId(),
                        "protocolo", agendamento.getProtocolo(),
                        "clienteNome", agendamento.getCliente() == null ? null : agendamento.getCliente().getNome(),
                        "servicoNome", agendamento.getServico() == null ? null : agendamento.getServico().getNome(),
                        "data", agendamento.getData(),
                        "horario", agendamento.getHoraInicio(),
                        "status", "CANCELADO"
                )
        );
    }

    @Transactional
    public Map<String, Object> confirmarPagamentoDonoWhatsapp(Long empresaId, Long agendamentoId, String statusPagamentoTexto) {
        Map<String, Object> erro = new HashMap<>();
        if (empresaId == null || agendamentoId == null) {
            erro.put("success", false);
            erro.put("erro", "VALIDACAO");
            erro.put("mensagem", "empresaId e agendamentoId sao obrigatorios.");
            return erro;
        }
        AgendamentoEntity agendamento = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento nao encontrado."));
        if (agendamento.getEmpresa() == null || !empresaId.equals(agendamento.getEmpresa().getId())) {
            throw new BusinessException("Agendamento nao pertence a empresa informada.");
        }
        StatusPagamento statusPagamento = mapearStatusPagamentoDono(statusPagamentoTexto);
        if (statusPagamento == null) {
            erro.put("success", false);
            erro.put("erro", "STATUS_INVALIDO");
            erro.put("mensagem", "Status de pagamento invalido.");
            return erro;
        }

        PagamentoEntity pagamento = pagamentoRepository.findByAgendamento_Id(agendamentoId).orElse(null);
        if (pagamento == null) {
            erro.put("success", false);
            erro.put("erro", "PAGAMENTO_NAO_ENCONTRADO");
            erro.put("mensagem", "Não encontrei pagamento vinculado a esse agendamento.");
            log.warn("[confirmacao-pagamento-dono] pagamento nao encontrado empresaId={} agendamentoId={}", empresaId, agendamentoId);
            return erro;
        }

        pagamento.setStatus(statusPagamento);
        pagamento.setDataPagamento(statusPagamento == StatusPagamento.PAGO ? LocalDateTime.now() : null);
        pagamentoRepository.save(pagamento);

        agendamento.setConfirmacaoPagamentoDonoRespondida(Boolean.TRUE);
        agendamento.setConfirmacaoPagamentoDonoRespondidaEm(LocalDateTime.now());
        agendamentoRepository.save(agendamento);

        Map<String, Object> resposta = new HashMap<>();
        resposta.put("success", true);
        resposta.put("agendamentoId", agendamento.getId());
        resposta.put("statusPagamento", pagamento.getStatus().name());
        log.info("[confirmacao-pagamento-dono] pagamento atualizado empresaId={} agendamentoId={} status={}",
                empresaId, agendamentoId, pagamento.getStatus().name());
        return resposta;
    }

    @Transactional
    public Map<String, Object> marcarLembreteEnviado(Long agendamentoId, String tipo) {
        Map<String, Object> resposta = new HashMap<>();
        if (agendamentoId == null || tipo == null || tipo.isBlank()) {
            resposta.put("success", false);
            resposta.put("erro", "VALIDACAO");
            resposta.put("mensagem", "agendamentoId e tipo sao obrigatorios.");
            return resposta;
        }

        AgendamentoEntity agendamento = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento nao encontrado."));

        ZoneId zoneId = ZoneId.of(appTimezone == null || appTimezone.isBlank() ? "America/Cuiaba" : appTimezone);
        LocalDateTime agora = ZonedDateTime.now(zoneId).toLocalDateTime();
        String tipoNormalizado = tipo.trim().toUpperCase(Locale.ROOT);
        if ("LEMBRETE_CLIENTE".equals(tipoNormalizado)) {
            agendamento.setLembreteWppEnviado(Boolean.TRUE);
        } else if ("CONFIRMACAO_DONO".equals(tipoNormalizado)) {
            agendamento.setConfirmacaoPagamentoDonoEnviada(Boolean.TRUE);
            agendamento.setConfirmacaoPagamentoDonoEnviadaEm(agora);
        } else {
            resposta.put("success", false);
            resposta.put("erro", "TIPO_INVALIDO");
            resposta.put("mensagem", "Tipo de lembrete invalido.");
            return resposta;
        }

        agendamentoRepository.save(agendamento);
        resposta.put("success", true);
        resposta.put("agendamentoId", agendamento.getId());
        resposta.put("tipo", tipoNormalizado);
        return resposta;
    }

    @Transactional
    public Map<String, Object> reagendarAgendamentoWhatsapp(Long empresaId, Long agendamentoId, LocalDate novaData, LocalTime novoHorario, Long profissionalId) {
        AgendamentoEntity agendamento = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento nao encontrado."));
        if (!agendamento.getEmpresa().getId().equals(empresaId)) {
            throw new BusinessException("Agendamento nao pertence a empresa informada.");
        }
        if (novaData == null || novoHorario == null) {
            throw new BusinessException("Informe nova data e novo horario validos.");
        }
        LocalTime horaFim = novoHorario.plusMinutes(agendamento.getServico().getDuracaoMinutos());
        List<String> horariosDisponiveis = agendamentoService.horariosDisponiveis(
                empresaId,
                agendamento.getProfissional() == null ? null : agendamento.getProfissional().getId(),
                agendamento.getServico().getId(),
                novaData
        );
        if (!horariosDisponiveis.contains(novoHorario.toString())) {
            throw new ConflictException("Esse horario acabou de ficar indisponivel.");
        }
        if (agendamentoRepository.existeConflitoDeHorario(
                agendamento.getProfissional().getId(),
                novaData,
                novoHorario,
                horaFim,
                com.minhaempresa.agendapro.agendamento.enums.StatusAgendamento.CANCELADO,
                agendamento.getId())) {
            throw new ConflictException("Ja existe agendamento para este profissional neste horario.");
        }
        EmpresaEntity empresa = buscarEmpresa(empresaId);
        if (profissionalId != null) {
            ProfissionalEntity profissional = profissionalRepository.findById(profissionalId)
                    .orElseThrow(() -> new ResourceNotFoundException("Profissional nao encontrado."));
            if (!profissional.getEmpresa().getId().equals(empresa.getId()) || profissional.getStatus() != StatusCadastro.ATIVO) {
                throw new BusinessException("Profissional indisponivel.");
            }
            agendamento.setProfissional(profissional);
        }
        agendamento.setData(novaData);
        agendamento.setHoraInicio(novoHorario);
        agendamento.setHoraFim(horaFim);
        agendamento.setStatus(com.minhaempresa.agendapro.agendamento.enums.StatusAgendamento.PENDENTE);
        AgendamentoEntity salvo = agendamentoRepository.save(agendamento);
        log.info("[whatsapp-reagendamento] reagendado empresaId={} agendamentoId={} novaData={} novoHorario={}",
                empresaId,
                agendamentoId,
                novaData,
                novoHorario);
        return Map.of(
                "success", true,
                "agendamento", Map.of(
                        "id", salvo.getId(),
                        "protocolo", salvo.getProtocolo(),
                        "clienteNome", salvo.getCliente() == null ? null : salvo.getCliente().getNome(),
                        "servicoNome", salvo.getServico() == null ? null : salvo.getServico().getNome(),
                        "data", salvo.getData(),
                        "horario", salvo.getHoraInicio(),
                        "status", salvo.getStatus()
                )
        );
    }

    @Transactional(readOnly = true)
    public List<WhatsappSessionSummaryResponse> listarSessoesPersistidas() {
        return sessionRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(this::toSessionSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WhatsappSessionResponse obterSessaoPersistida(Long empresaId) {
        WhatsappSessionEntity session = sessionRepository.findByEmpresa_Id(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Sessao do WhatsApp nao encontrada."));
        return toSessionResponse(session);
    }

    @Transactional
    public WhatsappSessionResponse salvarSessaoPersistida(Long empresaId, WhatsappSessionSaveRequest request) {
        EmpresaEntity empresa = buscarEmpresa(empresaId);
        WhatsappSessionEntity session = sessionRepository.findByEmpresa_Id(empresaId)
                .orElseGet(() -> WhatsappSessionEntity.builder()
                        .empresa(empresa)
                        .build());
        session.setEmpresa(empresa);
        session.setCredsJson(textoOuPadrao(request.credsJson(), "{}"));
        session.setKeysJson(textoOuPadrao(request.keysJson(), "{}"));
        session.setRegistered(request.registered());
        session.setPhoneNumber(textoOuNulo(request.phoneNumber()));
        session.setMeId(textoOuNulo(request.meId()));
        session.setMeLid(textoOuNulo(request.meLid()));
        session.setLastStatus(textoOuNulo(request.lastStatus()));
        session.setLastError(textoOuNulo(request.lastError()));
        WhatsappSessionEntity salva = sessionRepository.save(session);
        log.info("[whatsapp-session] persistida empresaId={} registered={} phoneNumber={} meId={} meLid={}",
                empresaId,
                Boolean.TRUE.equals(salva.getRegistered()),
                textoOuPadrao(salva.getPhoneNumber(), ""),
                textoOuPadrao(salva.getMeId(), ""),
                textoOuPadrao(salva.getMeLid(), ""));
        return toSessionResponse(salva);
    }

    @Transactional
    public void removerSessaoPersistida(Long empresaId) {
        sessionRepository.findByEmpresa_Id(empresaId).ifPresent(sessionRepository::delete);
        log.info("[whatsapp-session] removida empresaId={}", empresaId);
    }

    @Transactional(readOnly = true)
    public WhatsappFluxoConversaResponse obterFluxoConversa(Long empresaId, String telefone) {
        String telefoneNormalizado = normalizarTelefone(telefone);
        if (telefoneNormalizado.isBlank()) {
            return null;
        }
        WhatsappFluxoConversaEntity fluxo = fluxoConversaRepository
                .findByEmpresa_IdAndTelefoneCliente(empresaId, telefoneNormalizado)
                .orElse(null);
        if (fluxo == null) {
            return null;
        }
        if (fluxo.getExpiraEm() != null && fluxo.getExpiraEm().isBefore(LocalDateTime.now())) {
            fluxoConversaRepository.deleteAllByEmpresa_IdAndTelefoneCliente(empresaId, telefoneNormalizado);
            log.info("[whatsapp-fluxo] removido por expiracao empresaId={} telefone='{}'", empresaId, telefoneNormalizado);
            return null;
        }
        return toFluxoResponse(fluxo);
    }

    @Transactional
    public WhatsappFluxoConversaResponse salvarFluxoConversa(WhatsappFluxoConversaRequest request) {
        EmpresaEntity empresa = buscarEmpresa(request.empresaId());
        String telefone = normalizarTelefone(request.telefoneCliente());
        WhatsappFluxoConversaEntity fluxo = fluxoConversaRepository
                .findByEmpresa_IdAndTelefoneClienteAndRemoteJid(empresa.getId(), telefone, normalizarTexto(request.remoteJid()))
                .orElseGet(() -> WhatsappFluxoConversaEntity.builder()
                        .empresa(empresa)
                        .telefoneCliente(telefone)
                        .remoteJid(normalizarTexto(request.remoteJid()))
                        .build());
        fluxo.setEmpresa(empresa);
        fluxo.setTelefoneCliente(telefone);
        fluxo.setRemoteJid(normalizarTexto(request.remoteJid()));
        fluxo.setTipoFluxo(textoOuPadrao(request.tipoFluxo(), "AGENDAMENTO"));
        fluxo.setEtapa(textoOuPadrao(request.etapa(), "ESCOLHENDO_MODO"));
        fluxo.setAtivo(request.ativo());
        fluxo.setModoSelecionado(textoOuNulo(request.modoSelecionado()));
        fluxo.setPayloadJson(serializarPayload(request.payload()));
        fluxo.setExpiraEm(request.expiraEm());
        WhatsappFluxoConversaEntity salvo = fluxoConversaRepository.save(fluxo);
        log.info(
                "[whatsapp-fluxo] salvo empresaId={} telefone='{}' remoteJid='{}' tipoFluxo='{}' etapa='{}' ativo={} modo='{}'",
                empresa.getId(),
                telefone,
                normalizarTexto(request.remoteJid()),
                salvo.getTipoFluxo(),
                salvo.getEtapa(),
                Boolean.TRUE.equals(salvo.getAtivo()),
                textoOuPadrao(salvo.getModoSelecionado(), "")
        );
        return toFluxoResponse(salvo);
    }

    @Transactional
    public void resetarFluxoConversa(Long empresaId, String telefone) {
        String telefoneNormalizado = normalizarTelefone(telefone);
        fluxoConversaRepository.deleteAllByEmpresa_IdAndTelefoneCliente(empresaId, telefoneNormalizado);
        log.info("[whatsapp-fluxo] resetado empresaId={} telefone='{}'", empresaId, telefoneNormalizado);
    }

    @Transactional(readOnly = true)
    public boolean conversaPausada(Long empresaId, String telefone) {
        String telefoneNormalizado = normalizarTelefone(telefone);
        if (telefoneNormalizado.isBlank()) {
            return false;
        }
        return conversationRepository.findByEmpresaIdAndContactPhone(empresaId, telefoneNormalizado)
                .map(conversa -> Boolean.TRUE.equals(conversa.getBotPausado()))
                .orElse(false);
    }

    public WhatsappConfigResponse configuracaoDaEmpresa(Long empresaId) {
        EmpresaRepository.WhatsappConfigView empresa = buscarEmpresaConfig(empresaId);
        WhatsappConnectionEntity connection = connectionRepository.findByEmpresaId(empresaId).orElse(null);
        WhatsappStatusResponse status = null;
        try {
            status = nodeClient.status(empresaId);
        } catch (RuntimeException ex) {
            log.warn("Falha ao consultar status remoto do WhatsApp para config: empresa={}, detalhe={}", empresaId, ex.getMessage());
        }
        boolean conectado = status != null ? status.whatsappConnected() : connection != null && connection.getStatus() == WhatsappConnectionStatus.CONNECTED;
        String numeroConectado = status != null && status.whatsappPhone() != null && !status.whatsappPhone().isBlank()
                ? status.whatsappPhone()
                : empresa.getWhatsappPhone();
        return montarConfiguracaoWhatsapp(empresa, conectado, numeroConectado);
    }

    private WhatsappConfigResponse montarConfiguracaoWhatsapp(EmpresaRepository.WhatsappConfigView empresa, boolean conectado, String numeroConectado) {
        List<com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.ServicoContextResponse> servicos = servicoRepository.findContextByEmpresaId(empresa.getId()).stream()
                .filter(servico -> servico.getNome() != null && !servico.getNome().isBlank())
                .map(servico -> new com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.ServicoContextResponse(
                        servico.getId(),
                        servico.getNome(),
                        servico.getValor(),
                        servico.getDuracaoMinutos(),
                        servico.getStatus() == null ? null : servico.getStatus().name()
                ))
                .toList();
        List<ProfissionalContextResponse> profissionais = profissionalRepository.findContextByEmpresaId(empresa.getId()).stream()
                .filter(profissional -> profissional.getStatus() == StatusCadastro.ATIVO)
                .filter(profissional -> profissional.getNome() != null && !profissional.getNome().isBlank())
                .map(profissional -> new ProfissionalContextResponse(
                        profissional.getId(),
                        profissional.getNome(),
                        profissional.getEspecialidade()
                ))
                .toList();
        String slug = resolverSlugAgendamento(empresa.getId(), empresa.getNomeFantasia(), empresa.getAgendamentoSlug());
        String nomeEmpresa = textoOuPadrao(empresa.getNomeFantasia(), "");
        String linkAgendamento = construirLinkAgendamento(slug);
        log.info("[whatsapp-contexto] empresaId={} nomeEmpresa='{}' slug='{}' linkAgendamento='{}' servicos={} profissionais={}",
                empresa.getId(),
                nomeEmpresa,
                slug,
                linkAgendamento,
                servicos.size(),
                profissionais.size());
        return new WhatsappConfigResponse(
                empresa.getId(),
                nomeEmpresa,
                textoOuPadrao(empresa.getWhatsappDescricaoEmpresa(), ""),
                slug,
                Boolean.TRUE.equals(empresa.getWhatsappSecretariaIaEnabled()),
                Boolean.TRUE.equals(empresa.getWhatsappNotificationsEnabled()),
                conectado,
                numeroConectado,
                textoOuPadrao(empresa.getWhatsappMensagemBoasVindas(), nomeEmpresa.isBlank() ? "Ola! Seja bem-vindo. Como posso te ajudar hoje?" : "Ola! Seja bem-vindo a " + nomeEmpresa + ". Como posso te ajudar hoje?"),
                textoOuPadrao(empresa.getWhatsappRespostaHorarios(), "Claro! Para verificar os dias e horarios disponiveis, acesse o link de agendamento."),
                textoOuPadrao(empresa.getWhatsappRespostaServicos(), "Temos alguns servicos disponiveis. Me diga qual voce deseja agendar."),
                textoOuPadrao(empresa.getWhatsappRespostaNaoEntende(), "Desculpa, nao entendi muito bem. Pode me explicar de outra forma?"),
                textoOuPadrao(empresa.getWhatsappMensagemHumano(), "Vou encaminhar sua mensagem para um atendente continuar o atendimento."),
                linkAgendamento,
                servicos,
                profissionais,
                List.of()
        );
    }

    public WhatsappConnectResponse conectar(Long usuarioId, WhatsappConnectRequest request) {
        UsuarioEntity usuario = buscarUsuarioComEmpresa(usuarioId);
        EmpresaEntity empresa = usuario.getEmpresa();
        if (!empresa.getId().equals(request.empresaId())) {
            throw new BusinessException("Empresa informada nao corresponde ao usuario autenticado.");
        }

        WhatsappConnectionEntity connectionExistente = connectionRepository.findByEmpresaId(empresa.getId()).orElse(null);
        WhatsappStatusResponse statusAtual = null;
        try {
            statusAtual = statusDaEmpresa(empresa.getId());
        } catch (RuntimeException ex) {
            log.warn("Nao foi possivel consultar status antes do pareamento: empresa={}, detalhe={}", empresa.getId(), ex.getMessage());
        }
        boolean precisaLimparSessao = statusAtual != null
                ? !statusAtual.whatsappConnected()
                : connectionExistente != null && connectionExistente.getStatus() != WhatsappConnectionStatus.CONNECTED;
        if (precisaLimparSessao) {
            try {
                nodeClient.limparSessao(empresa.getId());
                removerSessaoPersistida(empresa.getId());
                log.info("Sessao WhatsApp limpa antes de novo pareamento: empresa={}", empresa.getId());
            } catch (RuntimeException ex) {
                log.warn("Falha ao limpar sessao WhatsApp antes de novo pareamento: empresa={}, detalhe={}", empresa.getId(), ex.getMessage());
            }
        }

        log.info("Iniciando pareamento WhatsApp: empresa={}, telefone={}", empresa.getId(), normalizarTelefone(request.phoneNumber()));
        WhatsappConnectResponse response;
        try {
            response = nodeClient.conectar(request);
        } catch (RuntimeException ex) {
            log.error("Falha no pareamento WhatsApp: empresa={}, telefone={}, detalhe={}", empresa.getId(), normalizarTelefone(request.phoneNumber()), ex.getMessage());
            throw ex;
        }
        persistirInicioPareamento(empresa.getId(), request.phoneNumber(), usuarioId);
        return response;
    }

    public WhatsappStatusResponse concluirConexao(Long usuarioId) {
        UsuarioEntity usuario = buscarUsuarioComEmpresa(usuarioId);
        return statusDaEmpresa(usuario.getEmpresa().getId());
    }

    public WhatsappStatusResponse desconectar(Long usuarioId) {
        UsuarioEntity usuario = buscarUsuarioComEmpresa(usuarioId);
        EmpresaEntity empresa = usuario.getEmpresa();
        try {
            nodeClient.desconectar(empresa.getId());
        } catch (RuntimeException ex) {
            log.warn("Falha ao desconectar WhatsApp na integracao Node: empresa={}", empresa.getId());
        }
        return marcarEmpresaComoDesconectadaPorPainel(empresa.getId(), usuarioId);
    }

    public SendTestMessageResponse enviarMensagemTeste(Long usuarioId, SendTestMessageRequest request) {
        UsuarioEntity usuario = buscarUsuarioComEmpresa(usuarioId);
        EmpresaEntity empresa = usuario.getEmpresa();
        try {
            nodeClient.enviarMensagem(empresa.getId(), request.to(), request.message());
            registrarAuditoriaMensagemTeste(empresa.getId(), usuarioId, true, null);
            return new SendTestMessageResponse("SENT", "Mensagem teste enviada.", null);
        } catch (RuntimeException ex) {
            registrarAuditoriaMensagemTeste(empresa.getId(), usuarioId, false, ex.getMessage());
            throw new BusinessException("Nao foi possivel enviar a mensagem teste.");
        }
    }

    @Transactional(readOnly = true)
    public WhatsappContextResponse obterContexto(Long empresaId, String clientePhone) {
        EmpresaEntity empresa = buscarEmpresa(empresaId);
        String telefone = normalizarTelefone(clientePhone);
        ClienteEntity cliente = telefone.isBlank()
                ? null
                : clienteRepository.findFirstByEmpresaIdAndTelefone(empresaId, telefone).orElse(null);
        List<String> servicos = servicoRepository.findByEmpresaId(empresaId).stream()
                .map(ServicoEntity::getNome)
                .filter(valor -> valor != null && !valor.isBlank())
                .toList();
        List<String> horarios = horarioAtendimentoService.listarPorEmpresa(empresaId).stream()
                .filter(HorarioAtendimentoResponse::ativo)
                .map(item -> item.diaLabel() + " " + formatarHora(item.horaInicio()) + "-" + formatarHora(item.horaFim()))
                .toList();
        ClienteContextResponse clienteContexto = cliente == null ? null : new ClienteContextResponse(
                cliente.getNome(),
                agendamentosRecentes(cliente.getId()).stream()
                        .map(this::toAgendamentoContextResponse)
                        .toList()
        );
        return new WhatsappContextResponse(
                new EmpresaContextResponse(
                        empresa.getNomeFantasia(),
                        servicos,
                        horarios,
                        Boolean.TRUE.equals(empresa.getWhatsappConnected()),
                        empresa.getWhatsappPhone(),
                        Boolean.TRUE.equals(empresa.getWhatsappNotificationsEnabled()),
                        Boolean.TRUE.equals(empresa.getWhatsappSecretariaIaEnabled())
                ),
                clienteContexto
        );
    }

    @Transactional
    public void processarAgendamentoIa(WhatsappAgendamentoIaRequest request) {
        EmpresaEntity empresa = buscarEmpresa(request.empresaId());
        String telefone = normalizarTelefone(request.clientePhone());
        if (telefone.isBlank()) {
            throw new BusinessException("Cliente invalido para callback de agendamento.");
        }

        ClienteEntity cliente = clienteRepository.findFirstByEmpresaIdAndTelefone(empresa.getId(), telefone).orElse(null);
        if (cliente == null) {
            auditService.registrar("WHATSAPP_IA_SEM_CLIENTE", "WARN", null, null, empresa, "Callback da IA recebido sem cliente vinculado", telefone, null, null);
            return;
        }

        WhatsappConversationEntity conversa = conversationRepository.findByEmpresaIdAndContactPhone(empresa.getId(), telefone)
                .orElseGet(() -> WhatsappConversationEntity.builder()
                        .empresa(empresa)
                        .contactName(cliente.getNome())
                        .contactPhone(telefone)
                        .status(WhatsappConversationStatus.OPEN)
                        .build());
        conversa.setContactName(cliente.getNome());
        conversa.setLastMessageAt(LocalDateTime.now());
        WhatsappConversationEntity conversaSalva = conversationRepository.save(conversa);

        mensagemSalvar(empresa, conversaSalva, request.texto(), telefone);
        auditService.registrar("WHATSAPP_IA_CALLBACK", "INFO", null, null, empresa, "Callback da IA processado com sucesso", telefone, null, null);
    }

    public WhatsappStatusResponse atualizarPreferencias(WhatsappPreferenciasRequest request) {
        atualizarPreferenciasPersistidas(request);
        return statusDaEmpresa(request.empresaId());
    }

    public WhatsappStatusResponse atualizarStatusEmpresa(Long empresaId, String status, String phoneNumber, String pairingCode) {
        atualizarStatusEmpresaPersistido(empresaId, status, phoneNumber, pairingCode);
        return statusDaEmpresa(empresaId);
    }

    public WhatsappStatusResponse statusDaEmpresa(Long empresaId) {
        EmpresaEntity empresa = buscarEmpresa(empresaId);
        WhatsappConnectionEntity connection = connectionRepository.findByEmpresaId(empresaId).orElse(null);
        try {
            WhatsappStatusResponse remoto = nodeClient.status(empresaId);
            return combinarStatus(empresa, connection, remoto);
        } catch (RuntimeException ex) {
            log.warn("Falha ao consultar status remoto do WhatsApp: empresa={}, detalhe={}", empresaId, ex.getMessage());
            return combinarStatus(empresa, connection, null);
        }
    }

    @Transactional
    protected void atualizarStatusEmpresaPersistido(Long empresaId, String status, String phoneNumber, String pairingCode) {
        EmpresaEntity empresa = buscarEmpresa(empresaId);
        WhatsappConnectionEntity connection = connectionRepository.findByEmpresaId(empresaId)
                .orElseGet(() -> criarConexao(empresa));

        WhatsappConnectionStatus novoStatus = mapStatus(status);
        connection.setProvider(WhatsappProvider.BAILEYS);
        connection.setStatus(novoStatus);
        connection.setLastError(novoStatus == WhatsappConnectionStatus.ERROR ? "Conexao WhatsApp indisponivel." : null);
        if (phoneNumber != null && !phoneNumber.isBlank()) {
            connection.setDisplayPhoneNumber(phoneNumber);
            empresa.setWhatsappPhone(normalizarTelefone(phoneNumber));
        }
        if (pairingCode != null && !pairingCode.isBlank()) {
            connection.setPhoneNumberId(pairingCode);
        }
        if (novoStatus == WhatsappConnectionStatus.CONNECTED) {
            connection.setConnectedAt(LocalDateTime.now());
            connection.setDisconnectedAt(null);
            empresa.setWhatsappConnected(Boolean.TRUE);
        } else if (novoStatus == WhatsappConnectionStatus.DISCONNECTED || novoStatus == WhatsappConnectionStatus.ERROR) {
            connection.setDisconnectedAt(LocalDateTime.now());
            empresa.setWhatsappConnected(Boolean.FALSE);
            removerSessaoPersistida(empresaId);
        }
        connectionRepository.save(connection);
        empresaRepository.save(empresa);
        auditService.registrar("WHATSAPP_STATUS_UPDATE", "INFO", null, null, empresa, "Status do WhatsApp atualizado pelo servico Node: " + novoStatus.name(), null, null, null);
    }

    private WhatsappStatusResponse combinarStatus(EmpresaEntity empresa, WhatsappConnectionEntity connection, WhatsappStatusResponse remoto) {
        WhatsappConnectionStatus status = remoto != null && remoto.status() != null
                ? remoto.status()
                : connection != null && connection.getStatus() != null
                        ? connection.getStatus()
                        : WhatsappConnectionStatus.DISCONNECTED;
        boolean conectado = status == WhatsappConnectionStatus.CONNECTED
                || (remoto != null && remoto.whatsappConnected());
        String numeroConectado = remoto != null && remoto.whatsappPhone() != null && !remoto.whatsappPhone().isBlank()
                ? remoto.whatsappPhone()
                : remoto != null && remoto.displayPhoneNumber() != null && !remoto.displayPhoneNumber().isBlank()
                        ? remoto.displayPhoneNumber()
                        : connection != null && connection.getDisplayPhoneNumber() != null && !connection.getDisplayPhoneNumber().isBlank()
                                ? connection.getDisplayPhoneNumber()
                                : empresa.getWhatsappPhone();
        String statusLabel = remoto != null && remoto.statusLabel() != null && !remoto.statusLabel().isBlank()
                ? remoto.statusLabel()
                : switch (status) {
                    case CONNECTED -> "WhatsApp conectado";
                    case RECONNECTING -> "Reconectando WhatsApp";
                    case CONNECTING -> "Aguardando codigo";
                    case CONFIG_PENDING -> "Configuracao pendente";
                    case ERROR -> "Erro na conexao";
                    default -> "Desconectado";
                };
        String message = remoto != null && remoto.message() != null && !remoto.message().isBlank()
                ? remoto.message()
                : switch (status) {
                    case CONNECTED -> "ConexÃƒÆ’Ã‚Â£o oficial ativa pela integraÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o Baileys.";
                    case CONNECTING -> "Aguardando o codigo de pareamento.";
                    case CONFIG_PENDING -> "Configure a integraÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o do WhatsApp para continuar.";
                    case ERROR -> "Revise a conexÃƒÆ’Ã‚Â£o do WhatsApp.";
                    default -> "Nenhum WhatsApp conectado.";
                };

        return new WhatsappStatusResponse(
                connection == null ? null : connection.getId(),
                connection == null || connection.getProvider() == null ? "BAILEYS" : connection.getProvider().name(),
                status,
                statusLabel,
                numeroConectado,
                connection == null ? null : connection.getPhoneNumberId(),
                connection == null ? null : connection.getLastError(),
                connection == null ? null : connection.getConnectedAt(),
                connection == null ? null : connection.getDisconnectedAt(),
                !properties.whatsappServiceUrl().isBlank(),
                message,
                remoto == null ? null : remoto.pairingCode(),
                remoto == null ? null : remoto.expiresAt(),
                conectado,
                numeroConectado,
                Boolean.TRUE.equals(empresa.getWhatsappNotificationsEnabled()),
                Boolean.TRUE.equals(empresa.getWhatsappSecretariaIaEnabled())
        );
    }

    private WhatsappConnectionStatus mapStatus(String status) {
        String valor = normalizarTexto(status).toLowerCase(Locale.ROOT);
        return switch (valor) {
            case "conectado", "connected", "connected_success" -> WhatsappConnectionStatus.CONNECTED;
            case "reconnecting", "restart_required", "restartrequired" -> WhatsappConnectionStatus.RECONNECTING;
            case "gerando_codigo", "generating_code", "aguardando", "connecting", "pairing", "pairing_code", "waiting_pairing", "aguardando_codigo" -> WhatsappConnectionStatus.CONNECTING;
            case "config_pending", "configuracao_pendente" -> WhatsappConnectionStatus.CONFIG_PENDING;
            case "error", "erro", "pairing_failed", "pairing_expired", "session_error" -> WhatsappConnectionStatus.ERROR;
            case "disconnected", "desconectado" -> WhatsappConnectionStatus.DISCONNECTED;
            default -> WhatsappConnectionStatus.DISCONNECTED;
        };
    }

    private List<AgendamentoEntity> agendamentosRecentes(Long clienteId) {
        return agendamentoRepository.findByClienteId(clienteId).stream()
                .sorted(Comparator.comparing(AgendamentoEntity::getData).reversed()
                        .thenComparing(AgendamentoEntity::getHoraInicio, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .toList();
    }

    private AgendamentoContextResponse toAgendamentoContextResponse(AgendamentoEntity agendamento) {
        return new AgendamentoContextResponse(
                agendamento.getId(),
                agendamento.getData(),
                agendamento.getHoraInicio(),
                agendamento.getHoraFim(),
                agendamento.getServico() == null ? null : agendamento.getServico().getNome(),
                agendamento.getProfissional() == null ? null : agendamento.getProfissional().getNome(),
                agendamento.getStatus() == null ? null : agendamento.getStatus().name()
        );
    }

    private void mensagemSalvar(EmpresaEntity empresa, WhatsappConversationEntity conversa, String texto, String phone) {
        String conteudo = normalizarTexto(texto);
        if (conteudo.isBlank()) {
            return;
        }
        messageRepository.save(WhatsappMessageEntity.builder()
                .empresa(empresa)
                .conversation(conversa)
                .direction(WhatsappMessageDirection.OUTBOUND)
                .fromNumber(empresa.getWhatsappPhone())
                .toNumber(phone)
                .messageText(conteudo)
                .providerMessageId("ai-" + System.nanoTime())
                .status(WhatsappMessageStatus.SENT)
                .build());
        conversa.setLastMessageAt(LocalDateTime.now());
        conversationRepository.save(conversa);
    }

    private WhatsappConnectionEntity criarConexao(EmpresaEntity empresa) {
        return WhatsappConnectionEntity.builder()
                .empresa(empresa)
                .provider(WhatsappProvider.BAILEYS)
                .status(WhatsappConnectionStatus.DISCONNECTED)
                .build();
    }

    private WhatsappSessionSummaryResponse toSessionSummaryResponse(WhatsappSessionEntity session) {
        return new WhatsappSessionSummaryResponse(
                session.getEmpresa() == null ? null : session.getEmpresa().getId(),
                Boolean.TRUE.equals(session.getRegistered()),
                textoOuNulo(session.getPhoneNumber()),
                textoOuNulo(session.getMeId()),
                textoOuNulo(session.getMeLid()),
                session.getUpdatedAt()
        );
    }

    private WhatsappSessionResponse toSessionResponse(WhatsappSessionEntity session) {
        return new WhatsappSessionResponse(
                session.getEmpresa() == null ? null : session.getEmpresa().getId(),
                textoOuPadrao(session.getCredsJson(), "{}"),
                textoOuPadrao(session.getKeysJson(), "{}"),
                Boolean.TRUE.equals(session.getRegistered()),
                textoOuNulo(session.getPhoneNumber()),
                textoOuNulo(session.getMeId()),
                textoOuNulo(session.getMeLid()),
                textoOuNulo(session.getLastStatus()),
                textoOuNulo(session.getLastError()),
                session.getUpdatedAt()
        );
    }

    private WhatsappFluxoConversaResponse toFluxoResponse(WhatsappFluxoConversaEntity fluxo) {
        Map<String, Object> payload = desserializarPayload(fluxo.getPayloadJson());
        return new WhatsappFluxoConversaResponse(
                fluxo.getId(),
                fluxo.getEmpresa() == null ? null : fluxo.getEmpresa().getId(),
                textoOuNulo(fluxo.getTelefoneCliente()),
                textoOuNulo(fluxo.getRemoteJid()),
                textoOuNulo(fluxo.getTipoFluxo()),
                textoOuNulo(fluxo.getEtapa()),
                Boolean.TRUE.equals(fluxo.getAtivo()),
                textoOuNulo(fluxo.getModoSelecionado()),
                payload,
                fluxo.getCriadoEm(),
                fluxo.getAtualizadoEm(),
                fluxo.getExpiraEm()
        );
    }

    private WhatsappStatusResponse marcarEmpresaComoDesconectada(EmpresaEntity empresa, UsuarioEntity usuario, String motivo) {
        WhatsappConnectionEntity connection = connectionRepository.findByEmpresaId(empresa.getId())
                .orElseGet(() -> criarConexao(empresa));
        connection.setProvider(WhatsappProvider.BAILEYS);
        connection.setStatus(WhatsappConnectionStatus.DISCONNECTED);
        connection.setDisconnectedAt(LocalDateTime.now());
        connectionRepository.save(connection);
        empresa.setWhatsappConnected(Boolean.FALSE);
        empresaRepository.save(empresa);
        removerSessaoPersistida(empresa.getId());
        auditService.registrar("WHATSAPP_DESCONECTADO", "INFO", null, usuario, empresa, motivo, null, null, null);
        return combinarStatus(empresa, connection, null);
    }

    private UsuarioEntity buscarUsuarioComEmpresa(Long usuarioId) {
        UsuarioEntity usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario nao encontrado."));
        if (usuario.getEmpresa() == null) {
            throw new BusinessException("Usuario sem empresa nao pode gerenciar WhatsApp.");
        }
        return usuario;
    }

    private EmpresaEntity buscarEmpresa(Long empresaId) {
        return empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa nao encontrada."));
    }

    private String serializarPayload(Map<String, Object> payload) {
        try {
            if (payload == null || payload.isEmpty()) {
                return "{}";
            }
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            log.warn("Falha ao serializar payload do fluxo WhatsApp: detalhe={}", ex.getMessage());
            return "{}";
        }
    }

    private Map<String, Object> desserializarPayload(String valorJson) {
        try {
            if (valorJson == null || valorJson.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(valorJson, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception ex) {
            log.warn("Falha ao desserializar payload do fluxo WhatsApp: detalhe={}", ex.getMessage());
            return Map.of();
        }
    }

    private String normalizarTelefone(String telefone) {
        if (telefone == null) return "";
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.isEmpty()) return "";
        if (!digitos.startsWith("55")) {
            digitos = "55" + digitos;
        }
        if (digitos.length() == 12 && digitos.startsWith("55")) {
            digitos = digitos.substring(0, 4) + "9" + digitos.substring(4);
        }
        if (digitos.length() != 13) return "";
        int ddd = Integer.parseInt(digitos.substring(2, 4));
        if (ddd < 11 || ddd > 99) return "";
        return digitos;
    }

    private String textoOuPadrao(String valor, String padrao) {
        String texto = normalizarTexto(valor);
        return texto.isBlank() ? padrao : texto;
    }

    private String textoOuNulo(String valor) {
        String texto = normalizarTexto(valor);
        return texto.isBlank() ? null : texto;
    }

    private String construirLinkAgendamento(String slug) {
        String valorSlug = normalizarTexto(slug);
        if (valorSlug.isBlank()) {
            return null;
        }
        return baseFrontendUrl() + "/agendar/" + valorSlug;
    }

    @Transactional
    protected void persistirInicioPareamento(Long empresaId, String phoneNumber, Long usuarioId) {
        EmpresaEntity empresa = buscarEmpresa(empresaId);
        UsuarioEntity usuario = buscarUsuarioComEmpresa(usuarioId);
        WhatsappConnectionEntity connection = connectionRepository.findByEmpresaId(empresa.getId())
                .orElseGet(() -> criarConexao(empresa));
        connection.setProvider(WhatsappProvider.BAILEYS);
        connection.setStatus(WhatsappConnectionStatus.CONNECTING);
        connection.setPhoneNumberId(null);
        connection.setDisplayPhoneNumber(phoneNumber);
        connection.setLastError(null);
        connectionRepository.save(connection);

        empresa.setWhatsappConnected(Boolean.FALSE);
        empresa.setWhatsappPhone(normalizarTelefone(phoneNumber));
        empresaRepository.save(empresa);
        auditService.registrar("WHATSAPP_CONNECT_START", "INFO", null, usuario, empresa, "Pareamento do WhatsApp iniciado", null, null, null);
    }

    @Transactional
    protected WhatsappStatusResponse marcarEmpresaComoDesconectadaPorPainel(Long empresaId, Long usuarioId) {
        EmpresaEntity empresa = buscarEmpresa(empresaId);
        UsuarioEntity usuario = buscarUsuarioComEmpresa(usuarioId);
        removerSessaoPersistida(empresaId);
        return marcarEmpresaComoDesconectada(empresa, usuario, "WhatsApp desconectado pelo painel.");
    }

    @Transactional
    protected void registrarAuditoriaMensagemTeste(Long empresaId, Long usuarioId, boolean sucesso, String detalhe) {
        EmpresaEntity empresa = buscarEmpresa(empresaId);
        UsuarioEntity usuario = buscarUsuarioComEmpresa(usuarioId);
        if (sucesso) {
            auditService.registrar("WHATSAPP_TESTE_ENVIADO", "INFO", null, usuario, empresa, "Mensagem teste enviada pela integracao Node", null, null, null);
            return;
        }
        auditService.registrar("WHATSAPP_TESTE_FALHOU", "WARN", null, usuario, empresa, "Falha ao enviar mensagem teste do WhatsApp", detalhe, null, null);
    }

    @Transactional
    @CacheEvict(value = "contextoEmpresa", key = "#request.empresaId()")
    protected void atualizarPreferenciasPersistidas(WhatsappPreferenciasRequest request) {
        EmpresaEntity empresa = buscarEmpresa(request.empresaId());
        empresa.setWhatsappNotificationsEnabled(request.notificacoesAutomaticas());
        empresa.setWhatsappSecretariaIaEnabled(request.secretariaIaAtiva());
        empresa.setWhatsappDescricaoEmpresa(textoOuNulo(request.descricaoEmpresa()));
        empresa.setWhatsappMensagemBoasVindas(textoOuNulo(request.mensagemBoasVindas()));
        empresa.setWhatsappRespostaHorarios(textoOuNulo(request.respostaHorarios()));
        empresa.setWhatsappRespostaServicos(textoOuNulo(request.respostaServicos()));
        empresa.setWhatsappRespostaNaoEntende(textoOuNulo(request.respostaNaoEntende()));
        empresa.setWhatsappMensagemHumano(textoOuNulo(request.mensagemHumano()));
        empresaRepository.save(empresa);
    }

    private EmpresaRepository.WhatsappConfigView buscarEmpresaConfig(Long empresaId) {
        return empresaRepository.findWhatsappConfigViewById(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa nao encontrada."));
    }

    private String resolverSlugAgendamento(Long empresaId, String nomeFantasia, String slugAtual) {
        String slug = normalizarTexto(slugAtual);
        if (!slug.isBlank()) {
            return slug;
        }
        String base = normalizarSlugParaAgendamento(nomeFantasia);
        return base.isBlank() ? "empresa-" + empresaId : base;
    }

    private EmpresaEntity garantirSlugAgendamento(EmpresaEntity empresa) {
        if (empresa == null) {
            return null;
        }
        String slugAtual = normalizarTexto(empresa.getAgendamentoSlug());
        if (!slugAtual.isBlank()) {
            return empresa;
        }
        String base = normalizarSlugParaAgendamento(empresa.getNomeFantasia());
        if (base.isBlank()) {
            base = "empresa-" + empresa.getId();
        }
        String slug = base;
        int tentativa = 1;
        while (empresaRepository.existsByAgendamentoSlug(slug)) {
            EmpresaEntity existente = empresaRepository.findByAgendamentoSlug(slug).orElse(null);
            if (existente == null || empresa.getId().equals(existente.getId())) {
                break;
            }
            slug = base + "-" + empresa.getId() + (tentativa > 1 ? "-" + tentativa : "");
            tentativa++;
        }
        empresa.setAgendamentoSlug(slug);
        return empresaRepository.save(empresa);
    }

    private String normalizarSlugParaAgendamento(String valor) {
        if (valor == null) {
            return "";
        }
        return java.text.Normalizer.normalize(valor.trim().toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private String baseFrontendUrl() {
        String base = normalizarTexto(System.getenv("PUBLIC_BASE_URL"));
        if (base.isBlank()) {
            base = normalizarTexto(frontendUrl);
        }
        if (base.isBlank()) {
            base = "https://gendaz.site";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private List<com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.HorarioDisponivelResponse> construirHorariosDisponiveis(Long empresaId, List<com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.ServicoContextResponse> servicos) {
        if (servicos.isEmpty() || servicos.get(0).id() == null) {
            return List.of();
        }
        List<com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.HorarioDisponivelResponse> resposta = new java.util.ArrayList<>();
        Long servicoId = servicos.get(0).id();
        for (int offset = 0; offset < 7; offset++) {
            LocalDate data = LocalDate.now().plusDays(offset);
            try {
                List<String> horarios = agendamentoService.horariosDisponiveis(empresaId, null, servicoId, data);
                if (!horarios.isEmpty()) {
                    resposta.add(new com.minhaempresa.agendapro.whatsapp.dto.WhatsappDtos.HorarioDisponivelResponse(data, horarios));
                }
            } catch (RuntimeException ex) {
                log.warn("Falha ao calcular horarios disponiveis do WhatsApp: empresa={}, data={}, detalhe={}", empresaId, data, ex.getMessage());
            }
        }
        return resposta;
    }

    private String formatarHora(java.time.LocalTime hora) {
        return hora == null ? "--:--" : hora.toString().substring(0, 5);
    }

    private String normalizarTexto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private StatusPagamento mapearStatusPagamentoDono(String statusPagamentoTexto) {
        String valor = normalizarTexto(statusPagamentoTexto).toUpperCase(Locale.ROOT);
        return switch (valor) {
            case "PAGO", "SIM", "S", "1", "FOI PAGO", "PAGOU", "CONFIRMADO" -> StatusPagamento.PAGO;
            case "PENDENTE", "NAO", "NÃO", "N", "2", "NAO FOI", "NÃO FOI", "NAO PAGOU", "NÃO PAGOU", "FICOU PENDENTE", "EM ABERTO" -> StatusPagamento.PENDENTE;
            case "CANCELADO", "CANCELAR", "CANCELOU", "FOI CANCELADO", "CLIENTE CANCELOU", "3", "CANCELADA", "CANCELAMENTO" -> StatusPagamento.CANCELADO;
            default -> null;
        };
    }

}


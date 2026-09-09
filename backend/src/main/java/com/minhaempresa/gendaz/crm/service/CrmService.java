package com.minhaempresa.gendaz.crm.service;

import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.crm.dto.CrmDtos.*;
import com.minhaempresa.gendaz.crm.entity.CrmContatoEntity;
import com.minhaempresa.gendaz.crm.repository.CrmContatoRepository;
import com.minhaempresa.gendaz.email.ResendEmailService;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.PhoneNumberService;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppNotificacaoEntity;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppTipoNotificacao;
import com.minhaempresa.gendaz.whatsapp.policy.WhatsAppPlanoPolicy;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppNotificacaoRepository;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppClock;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppFilaService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrmService {
    private final ClienteRepository clienteRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final CrmContatoRepository crmContatoRepository;
    private final ResendEmailService resendEmailService;
    private final AssinaturaService assinaturaService;
    private final WhatsAppFilaService whatsAppFilaService;
    private final WhatsAppNotificacaoRepository whatsAppNotificacaoRepository;
    private final PhoneNumberService phoneNumberService;
    private final WhatsAppClock whatsAppClock;

    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    @Value("${app.frontend-url:${FRONTEND_URL:https://gendaz.site}}")
    private String frontendUrl;

    @Transactional(readOnly = true)
    public List<CrmClienteResponse> listarClientes(Long empresaId, String segment, String search,
                                                    String orderBy, Integer period) {
        List<ClienteEntity> todosClientes = clienteRepository.findByEmpresaIdAndStatusNot(empresaId, StatusCadastro.EXCLUIDO);
        LocalDate hoje = LocalDate.now();
        LocalDate dataLimite = (period != null && period > 0) ? hoje.minusDays(period) : null;

        List<CrmClienteResponse> resultado = todosClientes.stream()
                .map(cliente -> {
                    List<AgendamentoEntity> agendamentos = agendamentoRepository.findByClienteId(cliente.getId());

                    List<AgendamentoEntity> agendamentosNoPeriodo = agendamentos.stream()
                            .filter(a -> a.getStatus() != StatusAgendamento.CANCELADO)
                            .filter(a -> dataLimite == null || !a.getData().isBefore(dataLimite))
                            .sorted(Comparator.comparing(AgendamentoEntity::getData).reversed())
                            .collect(Collectors.toList());

                    int totalAgendamentos = agendamentosNoPeriodo.size();
                    double totalGasto = pagamentoRepository
                            .somarValorByEmpresaIdAndClienteIdAndStatusIn(
                                    empresaId,
                                    cliente.getId(),
                                    List.of(StatusPagamento.PAGO, StatusPagamento.PAYMENT_APPROVED)
                            )
                            .doubleValue();
                    double gastoMedio = totalAgendamentos > 0 ? totalGasto / totalAgendamentos : 0.0;

                    int diasSemAgendar = calcularDiasSemAgendar(cliente, agendamentos);
                    LocalDate ultimoAgendamentoData = calcularUltimoAgendamentoData(agendamentos);
                    int padraoFrequencia = calcularPadraoFrequencia(agendamentos);
                    int scoreRisco = calcularScoreRisco(totalGasto, totalAgendamentos, diasSemAgendar, padraoFrequencia, cliente);

                    String seg = calcularSegmento(scoreRisco, cliente);

                    CrmUltimaMensagem ultimaMsg = null;
                    Optional<CrmContatoEntity> ultimoContato = crmContatoRepository.findFirstByClienteIdOrderByDataCriacaoDesc(cliente.getId());
                    if (ultimoContato.isPresent()) {
                        CrmContatoEntity c = ultimoContato.get();
                        ultimaMsg = new CrmUltimaMensagem(c.getTipo(), c.getTemplate(), c.getDataCriacao(), c.getStatus());
                    }

                    return new CrmClienteResponse(
                            cliente.getId(),
                            cliente.getNome() != null ? cliente.getNome() : "",
                            cliente.getTelefone() != null ? cliente.getTelefone() : "",
                            cliente.getEmail() != null ? cliente.getEmail() : "",
                            seg,
                            diasSemAgendar,
                            ultimoAgendamentoData,
                            totalGasto,
                            gastoMedio,
                            totalAgendamentos,
                            padraoFrequencia,
                            scoreRisco,
                            ultimaMsg
                    );
                })
                .filter(crm -> {
                    if (segment != null && !segment.isBlank() && !segment.equals("todos")) {
                        if (!crm.segment().equalsIgnoreCase(segment)) return false;
                    }
                    if (search != null && !search.isBlank()) {
                        String termo = search.toLowerCase();
                        String termoDigitos = termo.replaceAll("\\D", "");
                        boolean match = crm.nome().toLowerCase().contains(termo)
                                || crm.telefone().toLowerCase().contains(termo)
                                || crm.email().toLowerCase().contains(termo);
                        if (!match && !termoDigitos.isEmpty()) {
                            match = crm.telefone().replaceAll("\\D", "").contains(termoDigitos);
                        }
                        if (!match) return false;
                    }
                    return true;
                })
                .sorted((a, b) -> {
                    if (orderBy == null || orderBy.isBlank() || orderBy.equals("recente")) {
                        return Long.compare(b.id(), a.id());
                    } else if (orderBy.equals("maior_gasto")) {
                        return Double.compare(b.totalGasto(), a.totalGasto());
                    } else if (orderBy.equals("menor_gasto")) {
                        return Double.compare(a.totalGasto(), b.totalGasto());
                    } else if (orderBy.equals("dias_sem_agendar_asc")) {
                        return Integer.compare(a.diasSemAgendar(), b.diasSemAgendar());
                    } else if (orderBy.equals("dias_sem_agendar_desc")) {
                        return Integer.compare(b.diasSemAgendar(), a.diasSemAgendar());
                    }
                    return 0;
                })
                .collect(Collectors.toList());

        return resultado;
    }

    @Transactional
    public Map<String, Object> enviarMensagem(Long empresaId, Long clienteId, EnviarMensagemRequest request) {
        ClienteEntity cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new BusinessException("Cliente nao encontrado."));

        if (cliente.getEmpresa() == null || !Objects.equals(cliente.getEmpresa().getId(), empresaId)) {
            throw new BusinessException("Empresa nao foi encontrada");
        }

        // Canal WhatsApp: fluxo assincrono via fila (sem e-mail, sem exigir
        // e-mail do cliente). O fluxo de e-mail abaixo permanece intacto.
        if (isCanalWhatsApp(request.canal())) {
            return enviarViaWhatsApp(empresaId, cliente, request);
        }

        if (cliente.getEmail() == null || cliente.getEmail().isBlank()) {
            throw new BusinessException("Cliente nao possui e-mail cadastrado.");
        }

        String assunto = montarAssunto(request.template(), cliente.getNome());
        String slug = cliente.getEmpresa() != null ? cliente.getEmpresa().getAgendamentoSlug() : null;
        String titulo = montarTitulo(request.template());
        String subtitulo = montarSubtitulo(request.template());
        String ctaTexto = "resgate".equals(request.template()) ? "Voltar para o Meu Gendaz" : "Acessar o Meu Gendaz";
        String corpo = montarCorpo(request.template(), cliente.getNome(), request.customMessage(), slug);

        boolean enviado = resendEmailService.enviarComTemplate(
                cliente.getEmail(),
                assunto,
                titulo,
                subtitulo,
                corpo,
                montarUrlMeuGendaz(slug),
                ctaTexto
        );

        CrmContatoEntity contato = CrmContatoEntity.builder()
                .empresa(cliente.getEmpresa())
                .cliente(cliente)
                .tipo(request.canal() != null ? request.canal() : "email")
                .template(request.template())
                .assunto(assunto)
                .mensagem(request.customMessage() != null ? request.customMessage() : corpo)
                .status(enviado ? "enviado" : "nao_entregue")
                .build();
        crmContatoRepository.save(contato);

        return Map.of(
                "success", enviado,
                "messageId", String.valueOf(contato.getId()),
                "status", contato.getStatus(),
                "timestamp", contato.getDataCriacao().toString()
        );
    }

    /**
     * Resgate/Reconexao via WhatsApp (acao manual, 1 cliente = 1 acao).
     *
     * <p>Decisao de historico: como o envio e assincrono (fila + worker), o
     * historico CRM registra que a acao foi solicitada/enfileirada
     * (status "solicitado", canal "whatsapp"); o status tecnico
     * (PENDENTE/ENVIANDO/ENVIADO/FALHOU/CANCELADO) vive em
     * whatsapp_notificacoes, sem duplicar a maquina de status aqui.
     *
     * <p>Sem reserva de cota aqui: a cota CRM e reservada exclusivamente
     * pelo worker no claim. E-mail nao e tocado nem contabilizado.
     */
    private Map<String, Object> enviarViaWhatsApp(
            Long empresaId, ClienteEntity cliente, EnviarMensagemRequest request) {
        String template = request.template() == null ? "" : request.template().trim().toLowerCase();
        WhatsAppTipoNotificacao tipo = switch (template) {
            case "resgate" -> WhatsAppTipoNotificacao.CRM_RESGATE;
            case "reconexao" -> WhatsAppTipoNotificacao.CRM_RECONEXAO;
            default -> null;
        };
        if (tipo == null) {
            return resultadoDominio(false, "WHATSAPP_TIPO_NAO_SUPORTADO");
        }
        String requestId = request.requestId();
        if (requestId == null || requestId.isBlank() || !REQUEST_ID_PATTERN.matcher(requestId.trim()).matches()) {
            return resultadoDominio(false, "WHATSAPP_REQUEST_ID_INVALIDO");
        }
        String plano = assinaturaService.buscarAtualPorEmpresa(empresaId)
                .map(a -> a.getPlano().getNome())
                .orElse(null);
        if (!WhatsAppPlanoPolicy.possuiWhatsApp(plano)) {
            return resultadoDominio(false, "WHATSAPP_NAO_DISPONIVEL_NO_PLANO");
        }
        String telefone = cliente.getTelefone();
        if (!phoneNumberService.canonicoValido(telefone)) {
            return resultadoDominio(false, "WHATSAPP_TELEFONE_INVALIDO");
        }
        String chave = (tipo == WhatsAppTipoNotificacao.CRM_RESGATE ? "CRM_RESGATE:" : "CRM_RECONEXAO:")
                + cliente.getId() + ":" + requestId.trim();
        String texto = montarTextoWhatsApp(tipo, cliente.getNome(), cliente.getEmpresa().getNomeFantasia());

        // Retry da mesma acao manual: mesma notificacao, sem duplicar
        // historico nem payload. Nova acao futura usa novo requestId.
        Optional<WhatsAppNotificacaoEntity> existente =
                whatsAppNotificacaoRepository.findByEmpresaIdAndIdempotencyKey(empresaId, chave);
        if (existente.isPresent()) {
            WhatsAppNotificacaoEntity notificacao = existente.get();
            return Map.of(
                    "success", true,
                    "messageId", String.valueOf(notificacao.getId()),
                    "status", "solicitado",
                    "timestamp", LocalDateTime.now().toString());
        }

        WhatsAppNotificacaoEntity notificacao = whatsAppFilaService.enfileirar(
                empresaId,
                tipo,
                chave,
                whatsAppClock.agoraUtc(),
                cliente.getId(),
                null,
                telefone.trim(),
                texto);

        CrmContatoEntity contato = CrmContatoEntity.builder()
                .empresa(cliente.getEmpresa())
                .cliente(cliente)
                .tipo("whatsapp")
                .template(template)
                .mensagem(texto)
                .status("solicitado")
                .build();
        crmContatoRepository.save(contato);

        return Map.of(
                "success", true,
                "messageId", String.valueOf(notificacao.getId()),
                "status", "solicitado",
                "timestamp", contato.getDataCriacao().toString());
    }

    private static boolean isCanalWhatsApp(String canal) {
        return canal != null && canal.trim().equalsIgnoreCase("whatsapp");
    }

    private Map<String, Object> resultadoDominio(boolean sucesso, String codigo) {
        return Map.of(
                "success", sucesso,
                "status", codigo,
                "timestamp", LocalDateTime.now().toString());
    }

    /**
     * Texto simples preservando a intencao dos templates de e-mail atuais
     * (saudade/volta no Resgate; saber como esta/volte quando quiser na
     * Reconexao). Sem promocao, desconto, cupom, preco, cobranca, dados
     * financeiros, observacoes internas ou classificacao de risco.
     */
    private String montarTextoWhatsApp(
            WhatsAppTipoNotificacao tipo, String nomeCliente, String nomeEmpresa) {
        String cliente = nomeCliente == null || nomeCliente.isBlank() ? "cliente" : nomeCliente;
        String empresa = nomeEmpresa == null || nomeEmpresa.isBlank() ? "nossa equipe" : nomeEmpresa;
        if (tipo == WhatsAppTipoNotificacao.CRM_RESGATE) {
            return "Olá, " + cliente + "! Tudo bem? Sentimos sua falta na " + empresa
                    + ". Se quiser agendar um novo atendimento, estamos à disposição.";
        }
        return "Olá, " + cliente + "! Tudo bem? Passando para saber como você está. Quando quiser voltar à "
                + empresa + ", estaremos por aqui.";
    }

    @Transactional(readOnly = true)
    public List<HistoricoContatoResponse> historicoContatos(Long empresaId, Long clienteId) {        ClienteEntity cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new BusinessException("Cliente nao encontrado."));
        if (cliente.getEmpresa() == null || !Objects.equals(cliente.getEmpresa().getId(), empresaId)) {
            throw new BusinessException("Empresa nao foi encontrada");
        }
        List<CrmContatoEntity> contatos = crmContatoRepository.findByClienteIdOrderByDataCriacaoDesc(clienteId);
        return contatos.stream().map(c -> new HistoricoContatoResponse(
                c.getId(),
                c.getTipo(),
                c.getTemplate(),
                c.getAssunto(),
                c.getDataCriacao(),
                c.getStatus(),
                c.getAberturaData()
        )).toList();
    }

    @Transactional
    public void registrarAbertura(String messageId) {
        try {
            Long id = Long.parseLong(messageId);
            crmContatoRepository.findById(id).ifPresent(contato -> {
                contato.setStatus("aberto");
                contato.setAberturaData(LocalDateTime.now());
                crmContatoRepository.save(contato);
            });
        } catch (Exception e) {
            log.warn("[crm] erro ao registrar abertura messageId={}. erroTipo={}", messageId, e.getClass().getSimpleName());
        }
    }

    private int calcularDiasSemAgendar(ClienteEntity cliente, List<AgendamentoEntity> agendamentos) {
        Optional<AgendamentoEntity> ultimo = agendamentos.stream()
                .filter(a -> a.getStatus() != StatusAgendamento.CANCELADO)
                .max(Comparator.comparing(AgendamentoEntity::getData));
        
        if (ultimo.isPresent()) {
            int dias = (int) ChronoUnit.DAYS.between(ultimo.get().getData(), LocalDate.now());
            return Math.max(0, dias);
        }
        
        if (cliente.getDataCriacao() != null) {
            int dias = (int) ChronoUnit.DAYS.between(cliente.getDataCriacao().toLocalDate(), LocalDate.now());
            return Math.max(0, dias);
        }
        
        return 0;
    }

    private LocalDate calcularUltimoAgendamentoData(List<AgendamentoEntity> agendamentos) {
        return agendamentos.stream()
                .filter(a -> a.getStatus() != StatusAgendamento.CANCELADO)
                .max(Comparator.comparing(AgendamentoEntity::getData))
                .map(AgendamentoEntity::getData)
                .orElse(null);
    }

    private int calcularPadraoFrequencia(List<AgendamentoEntity> agendamentos) {
        List<AgendamentoEntity> ordenados = agendamentos.stream()
                .filter(a -> a.getStatus() != StatusAgendamento.CANCELADO)
                .sorted(Comparator.comparing(AgendamentoEntity::getData))
                .toList();
        if (ordenados.size() < 2) return 30;
        long totalDias = 0;
        for (int i = 0; i < ordenados.size() - 1; i++) {
            totalDias += ChronoUnit.DAYS.between(
                    ordenados.get(i).getData(),
                    ordenados.get(i + 1).getData());
        }
        return (int) (totalDias / (ordenados.size() - 1));
    }

    private String calcularSegmento(int scoreRisco, ClienteEntity cliente) {
        if (cliente.getDataCriacao() != null
                && ChronoUnit.DAYS.between(cliente.getDataCriacao().toLocalDate(), LocalDate.now()) < 7) return "novo";
        if (scoreRisco >= 70) return "at_risk";
        return "regular";
    }

    private int calcularScoreRisco(double totalGasto, int agendamentos, int diasSemAgendar, int padraoFrequencia, ClienteEntity cliente) {
        if (agendamentos <= 0) {
            return 87;
        }
        if (diasSemAgendar > 60) {
            return 87;
        }
        if (diasSemAgendar > 30) {
            return 72;
        }
        if (diasSemAgendar > padraoFrequencia) {
            return 58;
        }
        if (totalGasto <= 0) {
            return 46;
        }
        if (cliente.getDataCriacao() != null
                && ChronoUnit.DAYS.between(cliente.getDataCriacao().toLocalDate(), LocalDate.now()) < 30) {
            return 24;
        }
        return 34;
    }

    private String montarAssunto(String template, String nome) {
        return switch (template) {
            case "resgate" -> "Estamos com saudade, " + nome + "!";
            case "reconexao" -> nome + ", sentimos sua falta!";
            case "promocao" -> "Oferta especial pra voce, " + nome + "!";
            case "lembrete" -> nome + ", lembrete do seu proximo compromisso";
            default -> "Mensagem da nossa equipe";
        };
    }

    private String montarTitulo(String template) {
        return "resgate".equals(template) ? "Estamos com saudade de voce" : "Queremos falar com voce novamente";
    }

    private String montarSubtitulo(String template) {
        return "resgate".equals(template)
                ? "Seu ultimo contato foi ha algum tempo. Volte quando quiser para continuar seu atendimento."
                : "A Gendaz esta pronta para atender voce de novo com praticidade e proximidade.";
    }

    private String montarCorpo(String template, String nome, String customMessage, String slugEmpresa) {
        String nomeSafe = nome != null ? nome : "cliente";
        String msgPersonalizada = customMessage != null && !customMessage.isBlank() ? customMessage : null;

        String mensagemPadrao = switch (template) {
            case "resgate" -> "Oi " + nomeSafe + "! Sentimos sua falta e queremos te receber novamente. Que tal voltar para um novo atendimento?";
            case "reconexao" -> nomeSafe + ", faz tempo que nao aparece por aqui! Queremos saber como voce esta e deixar tudo pronto para sua volta.";
            case "promocao" -> nomeSafe + ", preparamos uma oferta especial so pra voce! Aproveite e agende seu proximo atendimento com desconto.";
            case "lembrete" -> nomeSafe + ", lembrete: voce tem um compromisso agendado. Se precisar remarcar, esta tudo bem!";
            default -> "Entre em contato conosco para mais informacoes.";
        };

        String textoFinal = msgPersonalizada != null ? msgPersonalizada : mensagemPadrao;
        String ctaUrl = montarUrlMeuGendaz(slugEmpresa);

        return """
                <p style="margin:0 0 12px; font-size:15px; line-height:1.8; color:#111111;">%s</p>
                <p style="margin:0; font-size:14px; line-height:1.7; color:#6b7280;">
                  Voce tambem pode acessar diretamente o Meu Gendaz da sua empresa:
                  <a href="%s" style="color:#111111; text-decoration:underline; font-weight:700;">%s</a>
                </p>
                """.formatted(textoFinal, ctaUrl, ctaUrl);
    }

    private String montarUrlMeuGendaz(String slugEmpresa) {
        String base = frontendUrl == null || frontendUrl.isBlank() ? "https://gendaz.site" : frontendUrl.trim();
        String baseNormalizada = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        if (slugEmpresa == null || slugEmpresa.isBlank()) {
            return baseNormalizada + "/meu-gendaz";
        }
        return baseNormalizada + "/meu-gendaz/" + slugEmpresa.trim().toLowerCase();
    }
}


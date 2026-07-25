package com.minhaempresa.agendapro.insights.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhaempresa.agendapro.email.ResendEmailService;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.repository.EmpresaRepository;
import com.minhaempresa.agendapro.insights.client.GroqClient;
import com.minhaempresa.agendapro.insights.dto.InsightsDtos.ChatMessageRequest;
import com.minhaempresa.agendapro.insights.dto.InsightsDtos.DashboardResponse;
import com.minhaempresa.agendapro.insights.dto.InsightsDtos.InsightAction;
import com.minhaempresa.agendapro.insights.dto.InsightsDtos.InsightHistoryResponse;
import com.minhaempresa.agendapro.insights.dto.InsightsDtos.InsightItem;
import com.minhaempresa.agendapro.insights.dto.InsightsDtos.InsightsResponse;
import com.minhaempresa.agendapro.insights.entity.InsightEntity;
import com.minhaempresa.agendapro.insights.repository.InsightRepository;
import com.minhaempresa.agendapro.pagamento.repository.PagamentoRepository;
import com.minhaempresa.agendapro.usuario.entity.UsuarioEntity;
import com.minhaempresa.agendapro.usuario.enums.PerfilUsuario;
import com.minhaempresa.agendapro.usuario.repository.UsuarioRepository;
import com.minhaempresa.agendapro.shared.CompanyContext;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InsightsService {
    private final InsightsAnalyzer analyzer;
    private final GroqClient groqClient;
    private final InsightRepository insightRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PagamentoRepository pagamentoRepository;
    private final ResendEmailService resendEmailService;
    private final ObjectMapper objectMapper;

    @Value("${app.timezone:America/Cuiaba}")
    private String appTimezone;

    @Transactional(readOnly = true)
    public DashboardResponse gerarDashboard(Long empresaId, Integer periodo) {
        Map<String, Object> dados = analyzer.coletarDados(empresaId, periodo);
        DashboardResponse local = construirDashboardLocal(empresaId, dados);
        if (!groqClient.disponivel()) {
            return local;
        }

        String promptSistema = """
                VocÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Âª ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â© um consultor de negÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â³cios para empresas de serviÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â§os. Analise os dados da empresa e devolva JSON puro no formato:
                {
                  "scoreGeral": 0,
                  "alertas": [{"titulo":"","descricao":"","impacto":"","urgencia":"","tipo":"problema"}],
                  "oportunidades": [{"titulo":"","descricao":"","impacto":"","urgencia":"","tipo":"oportunidade"}],
                  "acoes": [{"descricao":"","urgencia":"","impactoEstimado":""}],
                  "impactoTotal": ""
                }
                Regras:
                - Use somente os dados fornecidos.
                - Seja direto e objetivo.
                - NÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â£o explique o JSON.
                """;
        String promptUsuario = montarPromptDados(dados);
        Optional<String> resposta = groqClient.analisar(promptSistema, promptUsuario);
        if (resposta.isEmpty()) {
            return local;
        }

        try {
            Map<String, Object> mapa = objectMapper.readValue(resposta.get(), new TypeReference<>() {});
            Integer scoreGeral = local.scoreGeral();
            Object scoreBruto = mapa.get("scoreGeral");
            if (scoreBruto instanceof Number numero) {
                scoreGeral = numero.intValue();
            } else if (scoreBruto != null) {
                try {
                    scoreGeral = Integer.parseInt(String.valueOf(scoreBruto));
                } catch (Exception ignored) {
                    scoreGeral = local.scoreGeral();
                }
            }
            return new DashboardResponse(
                    empresaId,
                    String.valueOf(dados.getOrDefault("empresaNome", "")),
                    scoreGeral,
                    converterItens(mapa.get("alertas"), local.alertas()),
                    converterItens(mapa.get("oportunidades"), local.oportunidades()),
                    converterAcoes(mapa.get("acoes"), local.acoes()),
                    String.valueOf(mapa.getOrDefault("impactoTotal", local.impactoTotal())),
                    LocalDateTime.now(ZoneId.of(appTimezone))
            );
        } catch (Exception e) {
            log.warn("[insights] nao foi possivel parsear resposta groq, usando analise local: {}", e.getMessage());
            return local;
        }
    }

    @Transactional(readOnly = true)
    public String analisarPergunta(Long empresaId, String pergunta, List<ChatMessageRequest> historico) {
        validarAcessoEmpresa(empresaId);
        Map<String, Object> dados = analyzer.coletarDados(empresaId, 30);
        if (pergunta != null && pergunta.trim().toLowerCase().contains("nome")) {
            return "Meu nome Ã© GendazIA! ðŸ‘‹ Sou o assistente de negÃ³cios da plataforma Gendaz.";
        }

        String promptSistema = """
                VocÃª Ã© GendazIA, assistente de negÃ³cios da plataforma Gendaz.
                Responda sempre em portuguÃªs do Brasil.
                Seja conversacional, humano e direto.
                Nunca ignore a pergunta do usuÃ¡rio.
                Use os dados fornecidos para contextualizar a resposta.
                Seja breve.
                Sempre termine com uma pergunta relevante.
                """;
        String ramoEmpresa = identificarRamoEmpresa(listaMapa(dados.get("servicos")));
        String promptUsuario = """
                Dados da empresa (ramo: %s):
                %s

                Pergunta do usuÃ¡rio:
                %s

                Responda em portuguÃªs do Brasil de forma natural.
                """.formatted(ramoEmpresa, montarPromptDadosConversa(dados), pergunta);

        if (historico != null && !historico.isEmpty()) {
            promptUsuario += """

                    HistÃ³rico recente da conversa:
                    %s
                    """.formatted(String.valueOf(historicoParaGroq(historico)));
        }

        System.out.println("=== GROQ INPUT ===");
        System.out.println("Pergunta: " + pergunta);
        System.out.println("HistÃ³rico: " + (historico == null ? "vazio" : historico.size() + " mensagens"));
        System.out.println("Dados formatados:\n" + montarPromptDadosConversa(dados));
        System.out.println("Ramo detectado: " + ramoEmpresa);
        System.out.println("==================");

        Optional<String> resposta = groqClient.conversar(promptSistema, historicoParaGroq(historico), promptUsuario);
        if (resposta.isEmpty()) {
            return "Desculpa, tive um problema ao processar sua pergunta. Tente novamente.";
        }
        return humanizarResposta(resposta.get(), pergunta, dados, historico);
    }
    @Transactional
    public InsightsResponse analisarERegistrar(Long empresaId, String pergunta) {
        String resposta = analisarPergunta(empresaId, pergunta, List.of());
        salvarAnalise(empresaId, "pergunta", pergunta, resposta);
        return new InsightsResponse(true, resposta, LocalDateTime.now(ZoneId.of(appTimezone)));
    }

    @Transactional
    public void salvarAnalise(Long empresaId, String tipo, String pergunta, String resposta) {
        InsightEntity insight = InsightEntity.builder()
                .empresaId(empresaId)
                .tipo(tipo == null || tipo.isBlank() ? "pergunta" : tipo)
                .pergunta(pergunta)
                .resposta(resposta)
                .dataCriacao(LocalDateTime.now(ZoneId.of(appTimezone)))
                .build();
        insightRepository.save(insight);
    }

    @Transactional(readOnly = true)
    public InsightEntity obterInsight(Long id) {
        return insightRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<InsightHistoryResponse> obterHistorico(Long empresaId) {
        return insightRepository.findByEmpresaIdOrderByDataCriacaoDesc(empresaId).stream()
                .map(item -> new InsightHistoryResponse(item.getId(), item.getEmpresaId(), item.getTipo(), item.getPergunta(), item.getResposta(), item.getDataCriacao()))
                .toList();
    }

    @Transactional
    @Scheduled(cron = "0 0 8 ? * MON", zone = "${app.timezone:America/Cuiaba}")
    public void analisarEmpresasAgendado() {
        List<EmpresaEntity> empresas = empresaRepository.findAll();
        for (EmpresaEntity empresa : empresas) {
            try {
                DashboardResponse dashboard = gerarDashboard(empresa.getId(), 30);
                salvarAnalise(empresa.getId(), "dashboard", "AUTOMATICO - Dashboard semanal", serializarDashboard(dashboard));
                enviarEmailResumo(empresa, dashboard);
            } catch (Exception e) {
                log.error("[insights] erro ao gerar dashboard agendado para empresa {}: {}", empresa.getId(), e.getMessage(), e);
            }
        }
    }

    private DashboardResponse construirDashboardLocal(Long empresaId, Map<String, Object> dados) {
        List<Map<String, Object>> servicos = listaMapa(dados.get("servicos"));
        List<Map<String, Object>> profissionais = listaMapa(dados.get("profissionais"));
        Map<String, Object> clientes = mapa(dados.get("clientes"));
        Map<String, Object> financeiro = mapa(dados.get("financeiro"));
        Map<String, Object> resumo = mapa(dados.get("resumo"));

        double receita30d = numero(financeiro.get("receita_30d"));
        double receita60d = numero(financeiro.get("receita_60d"));
        double pendente = numero(financeiro.get("pendente"));
        long clientesAtRisk = longo(clientes.get("at_risk"));
        long churned = longo(clientes.get("churned"));
        long cancelamentos = longo(financeiro.get("cancelamentos"));

        List<InsightItem> alertas = new ArrayList<>();
        if (pendente > 0) {
            alertas.add(new InsightItem(
                    "Receita pendente em aberto",
                    "Ha valores aguardando confirmacao no periodo atual.",
                    String.format("R$ %.2f", pendente),
                    pendente > receita30d * 0.35 ? "Alta" : "Media",
                    "problema"
            ));
        }
        if (clientesAtRisk > 0) {
            alertas.add(new InsightItem(
                    "Clientes em risco",
                    "Ha clientes sem retorno recente e com chance de perda de recorrencia.",
                    clientesAtRisk + " clientes",
                    "Alta",
                    "problema"
            ));
        }
        if (churned > 0) {
            alertas.add(new InsightItem(
                    "Base inativa crescendo",
                    "Existem clientes sem agendamento ha mais de 60 dias.",
                    churned + " clientes",
                    "Alta",
                    "problema"
            ));
        }
        if (cancelamentos > 0) {
            alertas.add(new InsightItem(
                    "Cancelamentos detectados",
                    "Reveja os horarios ou profissionais com mais cancelamento.",
                    cancelamentos + " cancelamentos",
                    cancelamentos > 10 ? "Alta" : "Media",
                    "problema"
            ));
        }

        List<InsightItem> oportunidades = new ArrayList<>();
        Map<String, Object> topServico = servicos.stream()
                .filter(item -> item.get("vendas_30d") != null)
                .max((a, b) -> Long.compare(longo(a.get("vendas_30d")), longo(b.get("vendas_30d"))))
                .orElse(null);
        if (topServico != null) {
            oportunidades.add(new InsightItem(
                    "Servico mais vendido",
                    String.valueOf(topServico.get("nome")),
                    String.valueOf(topServico.get("vendas_30d")) + " vendas no periodo",
                    "Media",
                    "oportunidade"
            ));
        }
        Map<String, Object> topProfissional = profissionais.stream()
                .filter(item -> item.get("receita_30d") != null)
                .max((a, b) -> Double.compare(numero(a.get("receita_30d")), numero(b.get("receita_30d"))))
                .orElse(null);
        if (topProfissional != null) {
            oportunidades.add(new InsightItem(
                    "Profissional com maior receita",
                    String.valueOf(topProfissional.get("nome")),
                    String.format("R$ %.2f", numero(topProfissional.get("receita_30d"))),
                    "Media",
                    "oportunidade"
            ));
        }

        List<InsightAction> acoes = new ArrayList<>();
        if (pendente > 0) {
            acoes.add(new InsightAction("Cobrar pagamentos pendentes com prioridade", "Alta", String.format("R$ %.2f", pendente)));
        }
        if (!servicos.isEmpty()) {
            acoes.add(new InsightAction("Promover o servico de maior demanda", "Media", "Aumentar agendamentos recorrentes"));
        }
        if (clientesAtRisk > 0) {
            acoes.add(new InsightAction("Reativar clientes inativos com mensagem personalizada", "Alta", clientesAtRisk + " contatos"));
        }

        int score = 100;
        score -= Math.min(30, (int) churned * 4);
        score -= Math.min(20, (int) clientesAtRisk * 2);
        if (receita60d > 0 && receita30d < receita60d) {
            score -= 15;
        }
        if (pendente > 0) {
            score -= 10;
        }
        if (cancelamentos > 0) {
            score -= Math.min(10, (int) cancelamentos);
        }
        score = Math.max(0, Math.min(100, score));

        String impactoTotal = receita30d > 0
                ? String.format("+R$ %.2f/periodo", Math.max(0, receita30d * 0.12))
                : "+R$ 0,00/periodo";

        return new DashboardResponse(
                empresaId,
                String.valueOf(dados.getOrDefault("empresaNome", "")),
                score,
                alertas,
                oportunidades,
                acoes,
                impactoTotal,
                LocalDateTime.now(ZoneId.of(appTimezone))
        );
    }

    private String montarPromptDados(Map<String, Object> dados) {
        try {
            return objectMapper.writeValueAsString(dados);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String montarPromptDadosConversa(Map<String, Object> dados) {
        Map<String, Object> financeiro = mapa(dados.get("financeiro"));
        Map<String, Object> clientes = mapa(dados.get("clientes"));
        List<Map<String, Object>> servicos = listaMapa(dados.get("servicos"));
        List<Map<String, Object>> profissionais = listaMapa(dados.get("profissionais"));

        int scoreGeral = construirDashboardLocal(Long.valueOf(String.valueOf(dados.get("empresaId"))), dados).scoreGeral();

        return """
                Aqui estÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¡ um resumo simples da empresa:
                - Score geral: %d/100
                - Receita dos ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Âºltimos 30 dias: R$ %.2f
                - PendÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Âªncias financeiras: R$ %.2f
                - Total de clientes: %d
                - Clientes ativos: %d
                - Clientes em risco: %d
                - Total de serviÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â§os: %d
                - Total de profissionais: %d

                Use esses dados como contexto para responder de forma humana, consultiva e natural.
                """.formatted(
                scoreGeral,
                numero(financeiro.get("receita_30d")),
                numero(financeiro.get("pendente")),
                inteiro(clientes.get("total")),
                inteiro(clientes.get("ativos")),
                inteiro(clientes.get("at_risk")),
                servicos.size(),
                profissionais.size()
        );
    }


    private String identificarRamoEmpresa(List<Map<String, Object>> servicos) {
        if (servicos == null || servicos.isEmpty()) {
            return "Empresa de ServiÃ§os";
        }

        String servicosTxt = servicos.stream()
                .map(s -> String.valueOf(s.getOrDefault("nome", "")))
                .collect(java.util.stream.Collectors.joining(", "))
                .toLowerCase();

        if (servicosTxt.contains("cabelo") || servicosTxt.contains("corte") || servicosTxt.contains("barba")) {
            return "SalÃ£o de Beleza / Barbearia";
        }
        if (servicosTxt.contains("consulta") || servicosTxt.contains("atendimento")) {
            return "ClÃ­nica / ConsultÃ³rio";
        }
        if (servicosTxt.contains("aula") || servicosTxt.contains("treinamento")) {
            return "Academia / Centro de Treinamento";
        }
        if (servicosTxt.contains("reparo") || servicosTxt.contains("manutenÃ§Ã£o")) {
            return "ServiÃ§os TÃ©cnicos";
        }
        return "Empresa de ServiÃ§os";
    }

    private String serializarDashboard(DashboardResponse dashboard) {
        try {
            return objectMapper.writeValueAsString(dashboard);
        } catch (Exception e) {
            return "{}";
        }
    }

    private List<Map<String, String>> historicoParaGroq(List<ChatMessageRequest> historico) {
        if (historico == null || historico.isEmpty()) {
            return List.of();
        }

        Set<String> processadas = new HashSet<>();
        List<Map<String, String>> mensagens = new ArrayList<>();
        for (ChatMessageRequest item : historico) {
            if (item == null || item.content() == null || item.content().isBlank()) {
                continue;
            }
            String chave = normalizarRole(item.role()) + ":" + item.content().trim();
            if (!processadas.add(chave)) {
                continue;
            }
            Map<String, String> mensagem = new LinkedHashMap<>();
            mensagem.put("role", normalizarRole(item.role()));
            mensagem.put("content", item.content().trim());
            mensagens.add(mensagem);
        }
        return mensagens;
    }

    private void validarAcessoEmpresa(Long empresaId) {
        if (empresaId == null) {
            throw new IllegalArgumentException("Empresa nao identificada.");
        }
        Long empresaContexto = CompanyContext.getCompanyId();
        if (empresaContexto != null && !empresaContexto.equals(empresaId)) {
            throw new SecurityException("Acesso negado. Voce nao tem permissao para acessar essa empresa.");
        }
    }

    private String normalizarRole(String role) {
        String valor = role == null ? "" : role.trim().toLowerCase();
        return switch (valor) {
            case "assistant", "bot", "ia" -> "assistant";
            case "system" -> "system";
            default -> "user";
        };
    }

    private void enviarEmailResumo(EmpresaEntity empresa, DashboardResponse dashboard) {
        String destinatario = resolverEmailEmpresa(empresa);
        if (destinatario == null || destinatario.isBlank()) {
            return;
        }

        String assunto = "Relatorio de Insights - " + empresa.getNomeFantasia();
        String html = """
                <html>
                  <body style="margin:0; padding:0; background-color:#0b0b0c; font-family:Arial, Helvetica, sans-serif; color:#111111;">
                    <div style="max-width:760px; margin:0 auto; padding:36px 20px;">
                      <div style="background:#ffffff; border-radius:20px; overflow:hidden; box-shadow:0 18px 60px rgba(0,0,0,0.18); border:1px solid #e5e7eb;">
                        <div style="padding:36px 36px 28px; text-align:center; background:#ffffff;">
                          <img src="https://api.gendaz.site/email/gendazpngpreto.png" alt="Gendaz" style="max-width:180px; width:100%; height:auto; display:block; margin:0 auto 16px;" />
                          <div style="display:inline-block; padding:6px 12px; border-radius:999px; background:#111111; color:#ffffff; font-size:12px; font-weight:700; letter-spacing:0.08em; text-transform:uppercase;">Insights</div>
                          <h2 style="margin:18px 0 10px; font-size:28px; line-height:1.2; color:#111111;">Insights semanais</h2>
                          <p style="margin:0 auto; max-width:520px; font-size:16px; line-height:1.7; color:#4b5563;">Resumo automatico da conta.</p>
                        </div>
                        <div style="padding:0 36px 28px;">
                          <div style="background:#f7f7f7; border:1px solid #e5e7eb; border-radius:16px; padding:22px 20px; color:#111111; font-size:14px; line-height:1.8;">
                            <p style="margin:0 0 12px; font-size:15px; line-height:1.8; color:#111111;">Score geral: <strong>%s</strong>/100</p>
                            <p style="margin:0 0 12px; font-size:15px; line-height:1.8; color:#111111;">Impacto potencial: <strong>%s</strong></p>
                            <p style="margin:0 0 12px; font-size:15px; line-height:1.8; color:#111111;">Alertas: %s</p>
                            <p style="margin:0 0 12px; font-size:15px; line-height:1.8; color:#111111;">Oportunidades: %s</p>
                            <p style="margin:0; font-size:15px; line-height:1.8; color:#111111;">Acoes: %s</p>
                          </div>
                        </div>
                      </div>
                    </div>
                  </body>
                </html>
                """.formatted(
                dashboard.scoreGeral(),
                dashboard.impactoTotal(),
                dashboard.alertas().size(),
                dashboard.oportunidades().size(),
                dashboard.acoes().size()
        );
        resendEmailService.enviarEmailCrm(destinatario, assunto, html);
    }

    private String resolverEmailEmpresa(EmpresaEntity empresa) {
        List<UsuarioEntity> donos = usuarioRepository.findByEmpresaIdAndPerfil(empresa.getId(), PerfilUsuario.DONO);
        if (!donos.isEmpty() && donos.get(0).getEmail() != null && !donos.get(0).getEmail().isBlank()) {
            return donos.get(0).getEmail();
        }
        return empresa.getEmail();
    }

    private List<InsightItem> converterItens(Object valor, List<InsightItem> fallback) {
        if (valor instanceof List<?> lista && !lista.isEmpty()) {
            List<InsightItem> resultado = new ArrayList<>();
            for (Object item : lista) {
                if (item instanceof Map<?, ?> mapa) {
                    resultado.add(new InsightItem(
                            stringValor(mapa.get("titulo")),
                            stringValor(mapa.get("descricao")),
                            stringValor(mapa.get("impacto")),
                            stringValor(mapa.get("urgencia")),
                            stringValor(mapa.get("tipo"))
                    ));
                }
            }
            if (!resultado.isEmpty()) {
                return resultado;
            }
        }
        return fallback;
    }

    private List<InsightAction> converterAcoes(Object valor, List<InsightAction> fallback) {
        if (valor instanceof List<?> lista && !lista.isEmpty()) {
            List<InsightAction> resultado = new ArrayList<>();
            for (Object item : lista) {
                if (item instanceof Map<?, ?> mapa) {
                    resultado.add(new InsightAction(
                            stringValor(mapa.get("descricao")),
                            stringValor(mapa.get("urgencia")),
                            stringValor(mapa.get("impactoEstimado"))
                    ));
                }
            }
            if (!resultado.isEmpty()) {
                return resultado;
            }
        }
        return fallback;
    }

    private List<Map<String, Object>> listaMapa(Object valor) {
        if (valor instanceof List<?> lista) {
            List<Map<String, Object>> resultado = new ArrayList<>();
            for (Object item : lista) {
                if (item instanceof Map<?, ?> mapa) {
                    Map<String, Object> convertido = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : mapa.entrySet()) {
                        convertido.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    resultado.add(convertido);
                }
            }
            return resultado;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapa(Object valor) {
        if (valor instanceof Map<?, ?> mapa) {
            Map<String, Object> convertido = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) mapa).entrySet()) {
                convertido.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return convertido;
        }
        return Map.of();
    }

    private long longo(Object valor) {
        if (valor == null) return 0L;
        if (valor instanceof Number numero) return numero.longValue();
        try {
            return Long.parseLong(String.valueOf(valor));
        } catch (Exception e) {
            return 0L;
        }
    }

    private double numero(Object valor) {
        if (valor == null) return 0D;
        if (valor instanceof Number numero) return numero.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(valor));
        } catch (Exception e) {
            return 0D;
        }
    }

    private String stringValor(Object valor) {
        return valor == null ? "" : String.valueOf(valor);
    }

    private int inteiro(Object valor) {
        if (valor == null) return 0;
        if (valor instanceof Number numero) return numero.intValue();
        try {
            return Integer.parseInt(String.valueOf(valor));
        } catch (Exception e) {
            return 0;
        }
    }

    public String humanizarResposta(String resposta, String pergunta, Map<String, Object> dados, List<ChatMessageRequest> historico) {
        if (resposta == null || resposta.isBlank()) {
            return responderLocalmente(pergunta, dados);
        }

        String texto = resposta.trim();
        if (texto.startsWith("{") || texto.startsWith("[")) {
            return responderLocalmente(pergunta, dados);
        }

        if (texto.length() > 500) {
            texto = texto.substring(0, 450).trim() + "...";
        }

        if (!texto.contains("?")) {
            texto = texto + " O que mais vocÃƒÆ’Ã‚Âª quer saber?";
        }

        return texto;
    }

    private String normalizarTomConversacional(String texto, String perguntaNormalizada, Map<String, Object> dados) {
        Map<String, Object> clientes = mapa(dados.get("clientes"));
        Map<String, Object> financeiro = mapa(dados.get("financeiro"));
        int scoreCalculado = construirDashboardLocal(Long.valueOf(String.valueOf(dados.get("empresaId"))), dados).scoreGeral();

        String saudacao = perguntaNormalizada.matches(".*\\b(oi|olÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡|ola|eae|opa|bom dia|boa tarde|boa noite)\\b.*")
                ? "OlÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡! Tudo bem? Estou aqui pra te ajudar a entender os dados da sua empresa."
                : "Vou te mostrar isso de forma simples:";

        String clientesEmRisco = String.valueOf(clientes.getOrDefault("at_risk", 0));
        String pendente = String.format("R$ %.2f", numero(financeiro.get("pendente")));

        String corpo = String.format(
                "Seu score estÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡ em %s/100, com %s clientes em risco e %s em pendÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Âªncias financeiras.",
                scoreCalculado,
                clientesEmRisco,
                pendente
        );

        String fechamento = "Quer que eu detalhe os alertas, as oportunidades ou o que merece atenÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â§ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â£o primeiro?";
        return saudacao + "\n" + corpo + "\n" + fechamento;
    }

    private String responderLocalmente(String pergunta, Map<String, Object> dados) {
        String texto = pergunta == null ? "" : pergunta.toLowerCase();
        Map<String, Object> financeiro = mapa(dados.get("financeiro"));
        Map<String, Object> clientes = mapa(dados.get("clientes"));
        List<Map<String, Object>> servicos = listaMapa(dados.get("servicos"));
        List<Map<String, Object>> profissionais = listaMapa(dados.get("profissionais"));

        if (texto.contains("receita") || texto.contains("faturamento")) {
            return String.format(
                    "Receita confirmada no periodo: R$ %.2f. Pendencias: R$ %.2f.",
                    numero(financeiro.get("receita_30d")),
                    numero(financeiro.get("pendente"))
            );
        }
        if (texto.contains("cliente") || texto.contains("churn") || texto.contains("at risk")) {
            return String.format(
                    "A empresa tem %s clientes, sendo %s ativos e %s em risco de perda.",
                    clientes.getOrDefault("total", 0),
                    clientes.getOrDefault("ativos", 0),
                    clientes.getOrDefault("at_risk", 0)
            );
        }
        if (texto.contains("servico")) {
            if (servicos.isEmpty()) {
                return "Ainda nao ha dados de servicos suficientes para uma analise detalhada.";
            }
            Map<String, Object> destaque = servicos.get(0);
            return String.format(
                    "O servico com maior movimento e %s, com %s vendas no periodo analisado.",
                    destaque.getOrDefault("nome", "Servico"),
                    destaque.getOrDefault("vendas_30d", 0)
            );
        }
        if (texto.contains("profissional")) {
            if (profissionais.isEmpty()) {
                return "Ainda nao ha dados de profissionais suficientes para uma analise detalhada.";
            }
            Map<String, Object> destaque = profissionais.get(0);
            return String.format(
                    "O profissional com melhor desempenho no periodo analisado foi %s.",
                    destaque.getOrDefault("nome", "Profissional")
            );
        }
        return String.format(
                "Pelo que eu estou vendo, sua empresa estÃƒÆ’Ã‚Â¡ com score %s/100. Os principais pontos de atenÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o sÃƒÆ’Ã‚Â£o clientes em risco (%s) e pendÃƒÆ’Ã‚Âªncias financeiras (R$ %.2f). Quer que eu detalhe algum ponto primeiro?",
                construirDashboardLocal(Long.valueOf(String.valueOf(dados.get("empresaId"))), dados).scoreGeral(),
                clientes.getOrDefault("at_risk", 0),
                numero(financeiro.get("pendente"))
        );
    }
}


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
import java.time.ZoneId;
import java.util.ArrayList;
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
                Você é um consultor de negócios para empresas de serviços. Analise os dados da empresa e devolva JSON puro no formato:
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
                - Não explique o JSON.
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
        Map<String, Object> dados = analyzer.coletarDados(empresaId, 30);
        if (!groqClient.disponivel()) {
            return responderLocalmente(pergunta, dados);
        }

        String promptSistema = """
                Você é um assistente de conversação amigável, humano e direto para uma plataforma de insights de negócios.

                OBJETIVO:
                - Responder como um bom consultor humano, não como relatório cru.
                - Sempre contextualizar o número antes de jogar a métrica.
                - Sempre terminar com uma pergunta curta e útil para manter a conversa.

                TOM:
                - Conversacional
                - Natural
                - Empático
                - Leve, sem formalidade excessiva
                - Use primeira pessoa quando fizer sentido ("eu posso te ajudar", "estou vendo...")

                REGRAS OBRIGATÓRIAS:
                1. Se a mensagem for saudação ("oi", "olá", "eae", "opa", "bom dia", "boa tarde", "boa noite"):
                   responda com algo como:
                   "Olá! Tudo bem? Estou aqui pra te ajudar a entender os dados da sua empresa."
                   Depois, cite o score/resumo de forma humana e finalize com uma pergunta.

                2. Se a pergunta pedir dados, explique primeiro o que os números indicam e só depois mostre os valores.
                   Exemplo:
                   "Pelo que eu estou vendo, o cenário está assim: ..."

                3. Se a pergunta estiver vaga, faça uma pergunta de esclarecimento em vez de responder seco.

                4. Use o histórico da conversa para não repetir contexto já dito.

                5. Nunca responda só com métricas, lista fria ou JSON cru.

                6. Seja breve: de 2 a 4 linhas, com no máximo 1 bloco curto por ideia.

                7. Sempre termine com uma pergunta relevante.

                EXEMPLOS DE ESTILO:
                - "Olá! Tudo bem? Estou aqui pra te ajudar com os dados da sua empresa. Hoje seu score está em 75/100 e isso indica estabilidade com alguns pontos de atenção. Quer que eu detalhe os alertas?"
                - "Seu faturamento está saudável, mas ainda há pendências que merecem atenção. Quer que eu mostre onde está o gargalo?"
                - "Ainda não entendi exatamente o que você quer analisar. Você quer ver receita, clientes, serviços ou profissionais?"

                PROIBIDO:
                - Tom robótico ou distante
                - Texto técnico demais
                - Resposta sem contexto
                - Resposta sem pergunta final
                - Texto gigante
                """;
        String promptUsuario;
        if (historico == null || historico.isEmpty()) {
            promptUsuario = """
                    Contexto da empresa:
                    %s

                    Pergunta do usuário:
                    %s

                    Responda em português do Brasil e de forma conversacional, usando o contexto acima.
                    """.formatted(montarPromptDados(dados), pergunta);
        } else {
            promptUsuario = pergunta;
        }

        Optional<String> resposta = groqClient.conversar(promptSistema, historicoParaGroq(historico), promptUsuario);
        return resposta.map(valor -> humanizarResposta(valor, pergunta, dados, historico)).orElseGet(() -> responderLocalmente(pergunta, dados));
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

        List<Map<String, String>> mensagens = new ArrayList<>();
        for (ChatMessageRequest item : historico) {
            if (item == null || item.content() == null || item.content().isBlank()) {
                continue;
            }
            Map<String, String> mensagem = new LinkedHashMap<>();
            mensagem.put("role", normalizarRole(item.role()));
            mensagem.put("content", item.content().trim());
            mensagens.add(mensagem);
        }
        return mensagens;
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
                  <body style="font-family: Arial, sans-serif; background: #f5f5f5; padding: 24px;">
                    <div style="max-width: 640px; margin: 0 auto; background: #fff; border-radius: 12px; padding: 24px;">
                      <h2 style="margin-top: 0;">Insights semanais</h2>
                      <p>Score geral: <strong>%s</strong>/100</p>
                      <p>Impacto potencial: <strong>%s</strong></p>
                      <p>Alertas: %s</p>
                      <p>Oportunidades: %s</p>
                      <p>Acoes: %s</p>
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

    public String humanizarResposta(String resposta, String pergunta, Map<String, Object> dados, List<ChatMessageRequest> historico) {
        if (resposta == null || resposta.isBlank()) {
            return responderLocalmente(pergunta, dados);
        }

        String texto = resposta.trim();
        String perguntaNormalizada = pergunta == null ? "" : pergunta.toLowerCase();
        boolean saudacao = perguntaNormalizada.matches(".*\\b(oi|olá|ola|eae|opa|bom dia|boa tarde|boa noite)\\b.*");
        boolean pareceMetricaFria = texto.matches("(?is).*(score\\s*:?\\s*\\d+/?\\d+|alertas?\\s*:?\\s*\\d+|oportunidades?\\s*:?\\s*\\d+).*");
        boolean primeiraMensagem = historico == null || historico.isEmpty();

        if (texto.startsWith("{") && texto.endsWith("}")) {
            try {
                Object json = objectMapper.readValue(texto, Object.class);
                texto = "Aqui estão os dados que encontrei: "
                        + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
            } catch (Exception ignored) {
                texto = "Aqui estão os dados que encontrei: " + texto;
            }
        }

        if (texto.length() < 20) {
            texto = texto + "\n\nGostaria de saber mais sobre algo específico?";
        }

        if (saudacao || pareceMetricaFria || primeiraMensagem) {
            texto = normalizarTomConversacional(texto, perguntaNormalizada, dados);
        }

        if (texto.length() > 1000) {
            texto = texto.substring(0, 800).trim() + "...\n\nSe quiser, eu posso detalhar mais algum ponto.";
        }

        if (!texto.contains("?")) {
            texto = texto + "\n\nO que mais você gostaria de saber?";
        }

        return texto;
    }

    private String normalizarTomConversacional(String texto, String perguntaNormalizada, Map<String, Object> dados) {
        Map<String, Object> clientes = mapa(dados.get("clientes"));
        Map<String, Object> financeiro = mapa(dados.get("financeiro"));
        int scoreCalculado = construirDashboardLocal(Long.valueOf(String.valueOf(dados.get("empresaId"))), dados).scoreGeral();

        String saudacao = perguntaNormalizada.matches(".*\\b(oi|olá|ola|eae|opa|bom dia|boa tarde|boa noite)\\b.*")
                ? "Olá! Tudo bem? Estou aqui pra te ajudar a entender os dados da sua empresa."
                : "Vou te mostrar isso de forma simples:";

        String clientesEmRisco = String.valueOf(clientes.getOrDefault("at_risk", 0));
        String pendente = String.format("R$ %.2f", numero(financeiro.get("pendente")));

        String corpo = String.format(
                "Seu score está em %s/100, com %s clientes em risco e %s em pendências financeiras.",
                scoreCalculado,
                clientesEmRisco,
                pendente
        );

        String fechamento = "Quer que eu detalhe os alertas, as oportunidades ou o que merece atenção primeiro?";
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
                "Score atual da empresa: %s/100. Alertas principais: clientes em risco (%s) e pendencias financeiras (R$ %.2f).",
                construirDashboardLocal(Long.valueOf(String.valueOf(dados.get("empresaId"))), dados).scoreGeral(),
                clientes.getOrDefault("at_risk", 0),
                numero(financeiro.get("pendente"))
        );
    }
}

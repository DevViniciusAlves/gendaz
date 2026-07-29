package com.minhaempresa.agendapro.insights.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.minhaempresa.agendapro.shared.CompanyContext;
import java.math.BigDecimal;
import java.time.LocalDate;
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
    private final ObjectMapper objectMapper;

    @Value("${app.timezone:America/Cuiaba}")
    private String appTimezone;

    @Transactional(readOnly = true)
    public DashboardResponse gerarDashboard(Long empresaId, Integer periodo) {
        return obterDashboard(empresaId, periodo, false);
    }

    @Transactional
    public DashboardResponse recalcularDashboard(Long empresaId, Integer periodo) {
        return obterDashboard(empresaId, periodo, true);
    }

    @Transactional(readOnly = true)
    public DashboardResponse obterDashboard(Long empresaId, Integer periodo, boolean forcar) {
        validarAcessoEmpresa(empresaId);
        Map<String, Object> dados = analyzer.coletarDados(empresaId, periodo);
        DashboardResponse fallback = construirDashboardLocal(empresaId, dados, "AUTO");
        LocalDateTime agora = LocalDateTime.now(ZoneId.of(appTimezone));

        InsightEntity ultimo = ultimoDashboard(empresaId);
        if (!forcar && ultimo != null && ultimo.getDataExpiracao() != null && ultimo.getDataExpiracao().isAfter(agora)) {
            return parseDashboard(ultimo, fallback, agora);
        }

        DashboardResponse gerado = gerarDashboardNovo(empresaId, periodo, dados, fallback, forcar ? "MANUAL" : "AUTO");
        salvarDashboard(empresaId, dados, gerado, forcar ? "MANUAL" : "AUTO", agora);
        return gerado;
    }

    @Transactional(readOnly = true)
    public String analisarPergunta(Long empresaId, String pergunta, List<ChatMessageRequest> historico) {
        validarAcessoEmpresa(empresaId);
        Map<String, Object> dados = analyzer.coletarDados(empresaId, 30);
        String promptSistema = """
                Voce e uma IA consultora de negocios para pequenas empresas de servicos.
                Responda sempre em portugues do Brasil.
                Use apenas os dados fornecidos.
                Nao invente numeros.
                Nao retorne texto fora do JSON quando a pergunta for sobre analise.
                """;
        String promptUsuario = """
                Dados da empresa:
                %s

                Pergunta:
                %s
                """.formatted(serializar(dados), pergunta);
        Optional<String> resposta = groqClient.conversar(promptSistema, historicoParaGroq(historico), promptUsuario);
        return resposta.orElseGet(() -> responderLocalmente(pergunta, dados));
    }

    @Transactional
    public InsightsResponse analisarERegistrar(Long empresaId, String pergunta) {
        String resposta = analisarPergunta(empresaId, pergunta, List.of());
        salvarAnalise(empresaId, "pergunta", pergunta, resposta, "MANUAL");
        return new InsightsResponse(true, resposta, LocalDateTime.now(ZoneId.of(appTimezone)));
    }

    @Transactional
    public void salvarAnalise(Long empresaId, String tipo, String pergunta, String resposta) {
        salvarAnalise(empresaId, tipo, pergunta, resposta, "MANUAL");
    }

    @Transactional
    public void salvarAnalise(Long empresaId, String tipo, String pergunta, String resposta, String origem) {
        InsightEntity insight = InsightEntity.builder()
                .empresaId(empresaId)
                .tipo(tipo == null || tipo.isBlank() ? "dashboard" : tipo)
                .pergunta(pergunta == null ? "" : pergunta)
                .resposta(resposta == null ? "" : resposta)
                .payloadJson(tipo != null && tipo.equalsIgnoreCase("dashboard") ? resposta : null)
                .origem(origem)
                .dataReferencia(LocalDateTime.now(ZoneId.of(appTimezone)))
                .dataExpiracao(LocalDateTime.now(ZoneId.of(appTimezone)).plusHours(24))
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
    @Scheduled(cron = "0 0 6 * * *", zone = "${app.timezone:America/Cuiaba}")
    public void analisarEmpresasAgendado() {
        for (EmpresaEntity empresa : empresaRepository.findAll()) {
            try {
                DashboardResponse dashboard = obterDashboard(empresa.getId(), 30, false);
                log.info("[insights] analise diaria gerada empresa={} origem=SCHEDULED", empresa.getId());
            } catch (Exception e) {
                log.error("[insights] erro ao gerar analise diaria empresa={}: {}", empresa.getId(), e.getMessage());
            }
        }
    }

    private DashboardResponse gerarDashboardNovo(Long empresaId, Integer periodo, Map<String, Object> dados, DashboardResponse fallback, String origem) {
        String promptSistema = """
                Voce e uma IA consultora de negocios para pequenas empresas de servicos.
                Voce deve analisar apenas os dados fornecidos.
                Nao invente numeros.
                Nao cite dados que nao existem no payload.
                Nao retorne texto fora do JSON.
                Responda sempre em portugues do Brasil.
                Se nao houver dados suficientes, explique isso no campo descricao.
                Gere recomendacoes praticas e acionaveis.
                """;
        String promptUsuario = """
                Analise os dados agregados reais desta empresa e devolva JSON puro no formato abaixo.
                Estrutura esperada:
                {
                  "alertas": [
                    {"titulo":"", "descricao":"", "impacto":"", "urgencia":"", "tipo":"alerta"}
                  ],
                  "principais": [
                    {"titulo":"", "descricao":"", "impacto":"", "urgencia":"", "tipo":"acao"}
                  ],
                  "oportunidades": [],
                  "acoes": []
                }

                Dados:
                %s
                """.formatted(serializar(dados));

        if (!groqClient.disponivel()) {
            return fallback;
        }

        Optional<String> resposta = groqClient.analisar(promptSistema, promptUsuario);
        if (resposta.isEmpty()) {
            return fallback;
        }
        try {
            Map<String, Object> json = objectMapper.readValue(resposta.get(), new TypeReference<>() {});
            return construirDashboardLocal(empresaId, dados, origem, json, fallback);
        } catch (Exception e) {
            log.warn("[insights] json invalido do groq, usando fallback: {}", e.getMessage());
            return fallback;
        }
    }

    private DashboardResponse construirDashboardLocal(Long empresaId, Map<String, Object> dados, String origem) {
        return construirDashboardLocal(empresaId, dados, origem, Map.of(), null);
    }

    private DashboardResponse construirDashboardLocal(Long empresaId, Map<String, Object> dados, String origem, Map<String, Object> groq, DashboardResponse fallback) {
        Map<String, Object> financeiro = mapa(dados.get("financeiro"));
        Map<String, Object> clientes = mapa(dados.get("clientes"));
        List<Map<String, Object>> servicos = listaMapa(dados.get("servicos"));
        List<Map<String, Object>> profissionais = listaMapa(dados.get("profissionais"));

        double pendente = numero(financeiro.get("pendente"));
        long atRisk = longo(clientes.get("at_risk"));
        double receita30 = numero(financeiro.get("receita_30d"));
        double receita60 = numero(financeiro.get("receita_60d"));

        List<InsightItem> alertas = montarAlertasReais(pendente, atRisk, receita30, receita60, servicos, profissionais);
        List<InsightItem> principais = montarInsightsPrincipais(dados);
        List<InsightItem> oportunidades = new ArrayList<>();
        List<InsightAction> acoes = new ArrayList<>();

        if (pendente > 0) {
            oportunidades.add(new InsightItem("Cobrança ativa", "Entrar em contato com clientes com pagamento em aberto.", "Existe valor recuperável no financeiro.", formatarMoeda(pendente), "Alta"));
            acoes.add(new InsightAction("Cobrar pagamentos pendentes", "Alta", formatarMoeda(pendente)));
        }
        if (atRisk > 0) {
            acoes.add(new InsightAction("Reativar clientes em risco", "Alta", atRisk + " contatos"));
        }
        if (servicos.stream().anyMatch(s -> longo(s.get("vendas_30d")) == 0)) {
            oportunidades.add(new InsightItem("Divulgar serviço sem venda", "Há serviço sem conversão no período.", "Usar dados reais do catálogo.", "Impacto n\u00e3o estimado", "Média"));
        }
        if (profissionais.stream().anyMatch(p -> longo(p.get("agendamentos_30d")) == 0)) {
            oportunidades.add(new InsightItem("Redistribuir agenda", "Profissional com baixa ocupação pode absorver demanda.", "Baseado no movimento real.", "Impacto n\u00e3o estimado", "Média"));
        }
        if (receita60 > 0 && receita30 < receita60) {
            oportunidades.add(new InsightItem("Queda de receita", "A receita recente caiu em relação ao período anterior.", "Comparação 30d vs 60d.", "Impacto n\u00e3o estimado", "Alta"));
        }

        if (groq.containsKey("principais")) {
            principais = limitarItens(parsePrincipais(groq.get("principais")), 4, principais);
        }
        if (groq.containsKey("alertas")) {
            alertas = limitarItens(parsePrincipais(groq.get("alertas")), 4, alertas);
        }
        if (groq.containsKey("oportunidades")) {
            oportunidades = limitarOportunidades(groq.get("oportunidades"), oportunidades);
        }
        if (groq.containsKey("acoes")) {
            acoes = limitarAcoes(groq.get("acoes"), acoes);
        }

        int score = calcularScore((int) atRisk, pendente, receita30, receita60);
        String impactoTotal = pendente > 0 ? formatarMoeda(pendente) : "Impacto não estimado";
        return new DashboardResponse(
                empresaId,
                stringValor(dados.get("empresaNome")),
                score,
                principais,
                alertas,
                oportunidades.size() > 3 ? oportunidades.subList(0, 3) : oportunidades,
                acoes.size() > 4 ? acoes.subList(0, 4) : acoes,
                impactoTotal,
                LocalDateTime.now(ZoneId.of(appTimezone))
        );
    }

    private InsightEntity ultimoDashboard(Long empresaId) {
        return insightRepository.findByEmpresaIdOrderByDataCriacaoDesc(empresaId).stream()
                .filter(item -> "dashboard".equalsIgnoreCase(item.getTipo()))
                .findFirst()
                .orElse(null);
    }

    private DashboardResponse parseDashboard(InsightEntity insight, DashboardResponse fallback, LocalDateTime agora) {
        try {
            if (insight.getResposta() == null || insight.getResposta().isBlank()) {
                return fallback;
            }
            return objectMapper.readValue(insight.getResposta(), DashboardResponse.class);
        } catch (Exception e) {
            return fallback;
        }
    }

    private void salvarDashboard(Long empresaId, Map<String, Object> dados, DashboardResponse dashboard, String origem, LocalDateTime agora) {
        InsightEntity insight = InsightEntity.builder()
                .empresaId(empresaId)
                .tipo("dashboard")
                .pergunta("AUTOMATICO - Dashboard")
                .resposta(serializar(dashboard))
                .payloadJson(serializar(dados))
                .origem(origem)
                .dataReferencia(agora)
                .dataExpiracao(agora.plusHours(24))
                .dataCriacao(agora)
                .build();
        insightRepository.save(insight);
    }

    private List<Map<String, String>> historicoParaGroq(List<ChatMessageRequest> historico) {
        if (historico == null) return List.of();
        List<Map<String, String>> msgs = new ArrayList<>();
        for (ChatMessageRequest item : historico) {
            if (item == null || item.content() == null || item.content().isBlank()) continue;
            msgs.add(Map.of("role", item.role() == null ? "user" : item.role(), "content", item.content()));
        }
        return msgs;
    }

    private String responderLocalmente(String pergunta, Map<String, Object> dados) {
        Map<String, Object> financeiro = mapa(dados.get("financeiro"));
        Map<String, Object> clientes = mapa(dados.get("clientes"));
        return "Score local: " + calcularScore((int) longo(clientes.get("at_risk")), numero(financeiro.get("pendente")), numero(financeiro.get("receita_30d")), numero(financeiro.get("receita_60d"))) + "/100.";
    }

    private void validarAcessoEmpresa(Long empresaId) {
        if (empresaId == null) {
            throw new IllegalArgumentException("Empresa nao identificada.");
        }
        Long empresaContexto = CompanyContext.getCompanyId();
        if (empresaContexto != null && !empresaContexto.equals(empresaId)) {
            throw new SecurityException("Acesso negado.");
        }
    }

    private List<InsightItem> parsePrincipais(Object valor) {
        if (!(valor instanceof List<?> lista)) return List.of();
        List<InsightItem> itens = new ArrayList<>();
        for (Object item : lista) {
            if (item instanceof Map<?, ?> mapa) {
                itens.add(new InsightItem(stringValor(mapa.get("titulo")), stringValor(mapa.get("descricao")), stringValor(mapa.get("impacto")), stringValor(mapa.get("urgencia")), stringValor(mapa.get("tipo"))));
            }
        }
        return itens;
    }

    private List<InsightItem> montarInsightsPrincipais(Map<String, Object> dados) {
        Map<String, Object> financeiro = mapa(dados.get("financeiro"));
        Map<String, Object> clientes = mapa(dados.get("clientes"));
        List<Map<String, Object>> servicos = listaMapa(dados.get("servicos"));
        List<Map<String, Object>> profissionais = listaMapa(dados.get("profissionais"));

        double pendente = numero(financeiro.get("pendente"));
        double receita30 = numero(financeiro.get("receita_30d"));
        double receita60 = numero(financeiro.get("receita_60d"));
        long atRisk = longo(clientes.get("at_risk"));

        long servicosSemMovimento = servicos.stream()
                .filter(servico -> longo(servico.get("vendas_30d")) <= 0)
                .count();
        long profissionaisSemMovimento = profissionais.stream()
                .filter(profissional -> longo(profissional.get("agendamentos_30d")) <= 0)
                .count();

        boolean quedaReceita = receita60 > 0 && receita30 < receita60;
        boolean riscoOciosidade = servicosSemMovimento > 0 || profissionaisSemMovimento > 0;
        boolean clienteEmRisco = atRisk > 0;
        boolean perdaFinanceira = pendente > 0 || quedaReceita;

        List<InsightItem> itens = new ArrayList<>();
        itens.add(montarPrincipalAcao(pendente, atRisk, servicosSemMovimento, profissionaisSemMovimento, quedaReceita));
        itens.add(montarPrincipalOciosidade(servicosSemMovimento, profissionaisSemMovimento, riscoOciosidade));
        itens.add(montarPrincipalFinanceiro(pendente, receita30, receita60, perdaFinanceira));
        itens.add(montarPrincipalClienteRisco(atRisk, clienteEmRisco));
        return itens;
    }

    private List<InsightItem> montarAlertasReais(double pendente, long atRisk, double receita30, double receita60, List<Map<String, Object>> servicos, List<Map<String, Object>> profissionais) {
        List<InsightItem> alertas = new ArrayList<>();

        if (pendente > 0) {
            alertas.add(new InsightItem(
                    "Cobrança pendente",
                    "Existem pagamentos em aberto que ainda exigem acompanhamento.",
                    formatarMoeda(pendente),
                    "Alta",
                    "alerta"
            ));
        }

        if (atRisk > 0) {
            alertas.add(new InsightItem(
                    "Clientes em risco",
                    "Clientes sem retorno recente devem ser reativados antes de virar churn.",
                    atRisk + " clientes",
                    "Alta",
                    "alerta"
            ));
        }

        long servicosSemMovimento = servicos.stream().filter(servico -> longo(servico.get("vendas_30d")) <= 0).count();
        long profissionaisSemMovimento = profissionais.stream().filter(profissional -> longo(profissional.get("agendamentos_30d")) <= 0).count();
        if (servicosSemMovimento > 0 || profissionaisSemMovimento > 0) {
            alertas.add(new InsightItem(
                    "Movimento abaixo do ideal",
                    "Há serviços ou profissionais sem movimentação relevante no período.",
                    profissionaisSemMovimento + " profissionais e " + servicosSemMovimento + " serviços",
                    "Média",
                    "alerta"
            ));
        }

        if (receita60 > 0 && receita30 < receita60) {
            alertas.add(new InsightItem(
                    "Receita em queda",
                    "O faturamento recente ficou abaixo do período comparado.",
                    "Comparação 30d vs 60d",
                    "Média",
                    "alerta"
            ));
        }

        if (alertas.isEmpty()) {
            alertas.add(new InsightItem(
                    "Operação estável",
                    "Nenhum alerta crítico foi encontrado na análise atual.",
                    "Dados sincronizados com a empresa vinculada.",
                    "Baixa",
                    "alerta"
            ));
        }

        return alertas.size() > 4 ? alertas.subList(0, 4) : alertas;
    }

    private InsightItem montarPrincipalAcao(double pendente, long atRisk, long servicosSemMovimento, long profissionaisSemMovimento, boolean quedaReceita) {
        if (pendente > 0) {
            return new InsightItem(
                    "Próxima Melhor Ação",
                    "Priorize a cobrança dos pagamentos em aberto para recuperar caixa imediato.",
                    formatarMoeda(pendente),
                    "Alta",
                    "acao"
            );
        }
        if (atRisk > 0) {
            return new InsightItem(
                    "Próxima Melhor Ação",
                    "Reative os clientes sem retorno recente antes que virem churn.",
                    atRisk + " clientes em risco",
                    "Alta",
                    "acao"
            );
        }
        if (profissionaisSemMovimento > 0 || servicosSemMovimento > 0) {
            return new InsightItem(
                    "Próxima Melhor Ação",
                    "Redistribua a agenda e divulgue os itens sem movimento para gerar novas conversões.",
                    profissionaisSemMovimento + " profissionais e " + servicosSemMovimento + " serviços sem movimento",
                    "Média",
                    "acao"
            );
        }
        if (quedaReceita) {
            return new InsightItem(
                    "Próxima Melhor Ação",
                    "Compense a queda recente de faturamento com campanhas de reativação e recorrência.",
                    "Receita recente abaixo do período anterior",
                    "Média",
                    "acao"
            );
        }
        return new InsightItem(
                "Próxima Melhor Ação",
                "Mantenha a operação atual e acompanhe os sinais da empresa diariamente.",
                "Sem ação crítica no momento",
                "Baixa",
                "acao"
        );
    }

    private InsightItem montarPrincipalOciosidade(long servicosSemMovimento, long profissionaisSemMovimento, boolean riscoOciosidade) {
        if (riscoOciosidade) {
            return new InsightItem(
                    "Risco de Ociosidade",
                    "Existem recursos sem uso consistente no período analisado.",
                    profissionaisSemMovimento + " profissionais e " + servicosSemMovimento + " serviços sem vendas recentes",
                    "Média",
                    "agenda"
            );
        }
        return new InsightItem(
                "Risco de Ociosidade",
                "Não há sinais relevantes de ociosidade agora.",
                "Agenda e serviços com movimento suficiente no período",
                "Baixa",
                "agenda"
        );
    }

    private InsightItem montarPrincipalFinanceiro(double pendente, double receita30, double receita60, boolean perdaFinanceira) {
        if (pendente > 0) {
            return new InsightItem(
                    "Perda Financeira Evitável",
                    "Há pagamentos em aberto que ainda podem ser recuperados.",
                    formatarMoeda(pendente),
                    "Alta",
                    "financeiro"
            );
        }
        if (perdaFinanceira) {
            return new InsightItem(
                    "Perda Financeira Evitável",
                    "A receita recente caiu em relação ao período anterior e merece atenção.",
                    "Comparação entre 30 dias e 60 dias",
                    "Média",
                    "financeiro"
            );
        }
        return new InsightItem(
                "Perda Financeira Evitável",
                "Não há perda financeira evidente no momento.",
                receita30 > 0 ? formatarMoeda(receita30) : "Sem receita recente relevante",
                "Baixa",
                "financeiro"
        );
    }

    private InsightItem montarPrincipalClienteRisco(long atRisk, boolean clienteEmRisco) {
        if (clienteEmRisco) {
            return new InsightItem(
                    "Cliente em Risco",
                    "Clientes sem retorno recente precisam de reativação para evitar churn.",
                    atRisk + " clientes sem agendamento recente",
                    "Alta",
                    "cliente"
            );
        }
        return new InsightItem(
                "Cliente em Risco",
                "A base de clientes não mostra risco imediato agora.",
                "Sem clientes críticos no período atual",
                "Baixa",
                "cliente"
        );
    }

    private List<InsightItem> limitarItens(List<InsightItem> itens, int max, List<InsightItem> fallback) {
        if (itens == null || itens.isEmpty()) return fallback;
        return itens.size() > max ? itens.subList(0, max) : itens;
    }

    private List<InsightItem> limitarOportunidades(Object valor, List<InsightItem> fallback) {
        if (!(valor instanceof List<?> lista)) return fallback;
        List<InsightItem> itens = new ArrayList<>();
        for (Object item : lista) {
            if (item instanceof Map<?, ?> mapa) {
                itens.add(new InsightItem(stringValor(mapa.get("titulo")), stringValor(mapa.get("descricao")), stringValor(mapa.get("impactoEstimado")), stringValor(mapa.get("prioridade")), "oportunidade"));
            }
        }
        return itens.isEmpty() ? fallback : itens.size() > 3 ? itens.subList(0, 3) : itens;
    }

    private List<InsightAction> limitarAcoes(Object valor, List<InsightAction> fallback) {
        if (!(valor instanceof List<?> lista)) return fallback;
        List<InsightAction> itens = new ArrayList<>();
        for (Object item : lista) {
            if (item instanceof Map<?, ?> mapa) {
                itens.add(new InsightAction(stringValor(mapa.get("descricao")), stringValor(mapa.get("prioridade")), stringValor(mapa.get("impactoEstimado"))));
            }
        }
        return itens.isEmpty() ? fallback : itens.size() > 4 ? itens.subList(0, 4) : itens;
    }

    private int calcularScore(int atRisk, double pendente, double receita30, double receita60) {
        int score = 100;
        score -= Math.min(30, atRisk * 5);
        if (pendente > 0) score -= 10;
        if (receita60 > 0 && receita30 < receita60) score -= 15;
        return Math.max(0, score);
    }

    private String serializar(Object valor) {
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> mapa(Object valor) {
        if (valor instanceof Map<?, ?> mapa) {
            Map<String, Object> convertido = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapa.entrySet()) {
                convertido.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return convertido;
        }
        return Map.of();
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

    private long longo(Object valor) {
        if (valor instanceof Number numero) return numero.longValue();
        try { return Long.parseLong(String.valueOf(valor)); } catch (Exception e) { return 0L; }
    }

    private double numero(Object valor) {
        if (valor instanceof Number numero) return numero.doubleValue();
        try { return Double.parseDouble(String.valueOf(valor)); } catch (Exception e) { return 0D; }
    }

    private String stringValor(Object valor) {
        return valor == null ? "" : String.valueOf(valor);
    }

    private String formatarMoeda(double valor) {
        return "R$ " + BigDecimal.valueOf(valor).setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    public record DashboardSnapshot(DashboardResponse dashboard, LocalDateTime geradoEm, LocalDateTime validoAte, String origem) {}
}

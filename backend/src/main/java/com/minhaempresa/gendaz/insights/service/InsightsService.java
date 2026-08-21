package com.minhaempresa.gendaz.insights.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhaempresa.gendaz.empresa.enums.RamoEmpresa;
import com.minhaempresa.gendaz.insights.client.GroqClient;
import com.minhaempresa.gendaz.insights.dto.InsightsDtos.ChatMessageRequest;
import com.minhaempresa.gendaz.insights.dto.InsightsDtos.DashboardResponse;
import com.minhaempresa.gendaz.insights.dto.InsightsDtos.InsightAction;
import com.minhaempresa.gendaz.insights.dto.InsightsDtos.InsightHistoryResponse;
import com.minhaempresa.gendaz.insights.dto.InsightsDtos.InsightItem;
import com.minhaempresa.gendaz.insights.dto.InsightsDtos.MeuGendazIAResponse;
import com.minhaempresa.gendaz.insights.dto.InsightsDtos.InsightsResponse;
import com.minhaempresa.gendaz.insights.entity.InsightEntity;
import com.minhaempresa.gendaz.insights.repository.InsightRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
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
    private final ObjectMapper objectMapper;

    @Value("${app.timezone:America/Cuiaba}")
    private String appTimezone;

    @Transactional(readOnly = true)
    public Optional<DashboardResponse> buscarUltimoDashboardPersistido(Long empresaId) {
        validarAcessoEmpresa(empresaId);
        log.info("[INSIGHTS] empresa={} acao=READ_SNAPSHOT", empresaId);
        Optional<InsightEntity> ultimo = ultimoDashboard(empresaId);
        log.info("[INSIGHTS] empresa={} acao=READ_SNAPSHOT snapshotEncontrado={}", empresaId, ultimo.isPresent());
        return ultimo.flatMap(this::parseDashboard);
    }

    @Transactional
    public DashboardResponse recalcularDashboard(Long empresaId, Integer periodo) {
        validarAcessoEmpresa(empresaId);
        log.info("[INSIGHTS] empresa={} acao=SYNC_START", empresaId);
        Map<String, Object> dados = analyzer.coletarDados(empresaId, periodo);
        DashboardResponse fallback = construirDashboardLocal(empresaId, dados, "MANUAL");
        LocalDateTime agora = LocalDateTime.now(ZoneId.of(appTimezone));

        DashboardResponse gerado;
        if (contaNova(dados)) {
            gerado = montarDashboardContaNova(empresaId, dados);
        } else {
            gerado = gerarDashboardNovo(empresaId, periodo, dados, fallback, "MANUAL");
        }

        InsightEntity salvo = salvarDashboard(empresaId, dados, gerado, "MANUAL", agora);
        log.info("[INSIGHTS] empresa={} acao=SYNC_SAVED snapshotId={}", empresaId, salvo.getId());
        return parseDashboard(salvo).orElse(gerado);
    }

    @Transactional(readOnly = true)
    public String analisarPergunta(Long empresaId, String pergunta, List<ChatMessageRequest> historico) {
        validarAcessoEmpresa(empresaId);
        Map<String, Object> dados = analyzer.coletarDados(empresaId, 30);
        String promptSistema = """
                Voce e uma IA consultora de negocios para pequenas empresas de servicos.
                Responda sempre em portugues do Brasil.
                Use apenas os dados fornecidos.
                Escreva de forma humana, natural e direta, como uma pessoa experiente conversando com o dono do negocio.
                Nao use tom robótico, nem frases prontas de IA, nem expressões repetidas como "com base nos dados fornecidos".
                Evite listas numeradas, marcadores e asteriscos quando der para responder em texto corrido.
                Se precisar listar pontos, faca isso de forma curta, simples e bem conversada.
                Nao invente numeros.
                """;
        String promptUsuario = """
                Dados da empresa:
                %s

                Pergunta:
                %s
                """.formatted(serializar(dados), pergunta);
        if (!groqClient.disponivel()) {
            throw new BusinessException("GendazIA indisponivel: configure a chave da Groq para usar a IA nos insights.");
        }

        Optional<String> resposta = groqClient.conversar(promptSistema, historicoParaGroq(historico), promptUsuario);
        if (resposta.isEmpty() || resposta.get().isBlank()) {
            throw new BusinessException("GendazIA indisponivel no momento. Tente novamente em instantes.");
        }

        return humanizarTexto(resposta.get());
    }

    @Transactional(readOnly = true)
    public MeuGendazIAResponse responderCliente(Long empresaId, String pergunta, List<ChatMessageRequest> historico) {
        validarAcessoEmpresa(empresaId);
        Map<String, Object> dados = analyzer.coletarDados(empresaId, 30);

        // Tenta usar a Groq se estiver disponível
        if (groqClient.disponivel()) {
            String promptSistema = """
                    Voce e a GendazIA, uma atendente virtual de uma empresa de servicos.
                    Responda sempre em portugues do Brasil.
                    Use apenas os dados fornecidos.
                    Seja cordial, humana, acolhedora e natural.
                    Fale como uma atendente de verdade, sem soar mecanica ou com cara de IA.
                    Prefira frases curtas, fluidas e espontaneas.
                    Nao invente valores, horarios ou servicos.
                    Ajude o cliente com duvidas sobre agendar, reagendar, cancelar, servicos, precos, profissionais, horarios e promocoes.
                    Se a pergunta pedir acao, conduza o atendimento passo a passo.
                    Quando faltar informacao, pergunte apenas a proxima coisa que precisa.
                    Se o cliente quiser agendar, pedir o servico, a data e o horario de forma natural, uma coisa por vez.
                    Se quiser reagendar, pedir a identificacao do agendamento e a nova data/horario.
                    Se quiser cancelar, confirmar o agendamento e, se necessario, o motivo.
                    Retorne somente JSON valido neste formato:
                    {
                      "resposta": "texto",
                      "sugestoes": ["opcao 1", "opcao 2", "opcao 3"],
                      "acao": "agenda|reagendar|cancelar|servicos|precos|nenhuma"
                    }
                    """;
            String promptUsuario = """
                    Dados da empresa:
                    %s

                    Pergunta do cliente:
                    %s

                    Historico recente:
                    %s
                    """.formatted(serializar(dados), pergunta, serializar(historico == null ? List.of() : historico));

            try {
                Optional<String> resposta = groqClient.conversar(promptSistema, historicoParaGroq(historico), promptUsuario);
                if (resposta.isPresent() && !resposta.get().isBlank()) {
                    try {
                        Map<String, Object> json = objectMapper.readValue(resposta.get(), new TypeReference<>() {});
                        String texto = stringValor(json.get("resposta"));
                        List<String> sugestoes = listaStrings(json.get("sugestoes"));
                        String acao = stringValor(json.get("acao"));
                        if (!texto.isBlank()) {
                            return new MeuGendazIAResponse(
                                    humanizarTexto(texto),
                                    sugestoes,
                                    acao == null || acao.isBlank() ? "nenhuma" : acao.trim().toLowerCase(),
                                    LocalDateTime.now(ZoneId.of(appTimezone))
                            );
                        }
                    } catch (Exception e) {
                        log.warn("[meu-gendaz-ia] resposta invalida da groq, usando fallback local. erroTipo={}", e.getClass().getSimpleName());
                    }
                }
            } catch (Exception e) {
                log.warn("[meu-gendaz-ia] falha ao chamar groq, usando fallback local. erroTipo={}", e.getClass().getSimpleName());
            }
        }

        // Fallback local inteligente
        return responderClienteLocalmente(empresaId, pergunta, dados);
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
    public InsightEntity obterInsight(Long id, Long empresaId) {
        InsightEntity insight = insightRepository.findById(id).orElse(null);
        if (insight == null) {
            return null;
        }
        if (empresaId != null && !empresaId.equals(insight.getEmpresaId())) {
            throw new BusinessException("Insight nao encontrado para a empresa atual.");
        }
        return insight;
    }

    @Transactional(readOnly = true)
    public List<InsightHistoryResponse> obterHistorico(Long empresaId) {
        return insightRepository.findByEmpresaIdOrderByDataCriacaoDesc(empresaId).stream()
                .map(item -> new InsightHistoryResponse(item.getId(), item.getEmpresaId(), item.getTipo(), item.getPergunta(), item.getResposta(), item.getDataCriacao()))
                .toList();
    }

    @Scheduled(cron = "0 0 6 * * *", zone = "${app.timezone:America/Cuiaba}")
    public void analisarEmpresasAgendado() {
        log.info("[INSIGHTS] acao=SCHEDULED_SKIP_DASHBOARD motivo=snapshot_dashboard_apenas_manual");
    }

    private DashboardResponse gerarDashboardNovo(Long empresaId, Integer periodo, Map<String, Object> dados, DashboardResponse fallback, String origem) {
        String ramoContexto = contextoRamo(dados);
        String promptSistema = """
                Voce e uma IA consultora de negocios para pequenas empresas de servicos.
                Voce deve analisar apenas os dados fornecidos.
                Nao invente numeros.
                Nao cite dados que nao existem no payload.
                Nao retorne texto fora do JSON.
                Responda sempre em portugues do Brasil.
                Se nao houver dados suficientes, explique isso no campo descricao.
                Gere recomendacoes praticas e acionaveis.
                Adapte as recomendacoes ao ramo informado.
                """;
        String promptUsuario = """
                Analise os dados agregados reais desta empresa e devolva JSON puro no formato abaixo.
                Contexto do ramo da empresa:
                %s

                Diretrizes por ramo:
                - BARBERSHOP: foco em recorrencia, retorno rapido, barba e servicos complementares.
                - SALAO_CABELO: foco em recorrencia, combos, tratamentos, coloracao e fidelizacao.
                - PERSONAL_TRAINER: foco em retenção, pacotes de sessoes, frequencia semanal e acompanhamento.
                - CLINICA_FISIOTERAPIA: foco em reavaliacao, continuidade de tratamento e follow-up.
                - CLINICA_ODONTOLOGIA: foco em prevençao, retorno periodico e agenda preventiva.
                - OUTRO: use recomendacoes genericas e praticas, sem inventar servicos.

                Estrutura esperada:
                {
                  "alertas": [
                    {"titulo":"", "descricao":"", "impacto":"", "urgencia":"", "tipo":"alerta"}
                  ],
                  "principais": [
                    {"titulo":"", "descricao":"", "impacto":"", "urgencia":"", "tipo":"acao"}
                  ],
                  "oportunidades": [
                    {"titulo":"", "descricao":"", "motivo":"", "impactoEstimado":"", "prioridade":"Média"}
                  ],
                  "acoes": [
                    {"titulo":"", "descricao":"", "motivo":"", "impactoEstimado":"", "prioridade":"Média", "status":"Pendente"}
                  ]
                }

                Regras:
                - Retorne no máximo 3 oportunidades.
                - Retorne no máximo 4 ações.
                - Se houver pendências, devolva ações para cobrança e recuperação.
                - So recomende reativacao de clientes quando clientes.inativos_status ou resumo.clientes_inativos for maior que 0.
                - Se clientes.inativos_status for 0, nao cite clientes inativos, reativacao de clientes, churn ou clientes em risco.
                - Se nao houver sinal real suficiente, devolva arrays vazios para oportunidades e acoes.
                - Se houver serviço sem venda ou profissional ocioso, devolva ações práticas.
                - Se a Groq não conseguir estimar impacto, use "Impacto não estimado".

                Dados:
                %s
                """.formatted(ramoContexto, serializar(dados));

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
            log.warn("[insights] json invalido do groq, usando fallback. erroTipo={}", e.getClass().getSimpleName());
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

        Map<String, Object> resumo = mapa(dados.get("resumo"));
        double pendente = numero(financeiro.get("pendente"));
        long clientesInativos = longo(clientes.get("inativos_status"));
        long atRisk = 0;
        long servicosInativos = longo(resumo.get("servicos_inativos"));
        long profissionaisInativos = longo(resumo.get("profissionais_inativos"));
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
        if (servicos.stream().anyMatch(s -> longo(s.get("vendas_30d")) == 0)) {
            oportunidades.add(new InsightItem("Divulgar serviço sem venda", "Há serviço sem conversão no período.", "O catálogo da empresa mostra um serviço sem movimento recente.", "Impacto não estimado", "Média"));
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
        if (groq.containsKey("acoes_recomendadas")) {
            acoes = limitarAcoes(groq.get("acoes_recomendadas"), acoes);
        }
        if (groq.containsKey("recomendacoes")) {
            acoes = limitarAcoes(groq.get("recomendacoes"), acoes);
        }

        principais = filtrarItensSemBaseReal(principais, clientesInativos, servicosInativos, profissionaisInativos);
        alertas = filtrarItensSemBaseReal(alertas, clientesInativos, servicosInativos, profissionaisInativos);
        oportunidades = filtrarItensSemBaseReal(oportunidades, clientesInativos, servicosInativos, profissionaisInativos);
        acoes = filtrarAcoesSemBaseReal(acoes, clientesInativos, servicosInativos, profissionaisInativos);

        if (acoes.isEmpty()) {
            acoes = List.of(new InsightAction("Nenhuma acao recomendada no momento", "Baixa", "Os dados reais nao indicam uma acao prioritaria agora"));
        }
        if (oportunidades.isEmpty()) {
            oportunidades = List.of(new InsightItem("Sem recomendacao no momento", "Os dados reais sincronizados nao mostram uma acao prioritaria clara.", "Sem sinal forte no periodo analisado.", "Baixa", "Media"));
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

    private boolean contaNova(Map<String, Object> dados) {
        Map<String, Object> clientes = mapa(dados.get("clientes"));
        Map<String, Object> financeiro = mapa(dados.get("financeiro"));
        List<Map<String, Object>> agendamentosRecentes = listaMapa(dados.get("agendamentosRecentes"));
        long totalClientes = longo(clientes.get("total"));
        double receita30 = numero(financeiro.get("receita_30d"));
        return totalClientes == 0 && receita30 <= 0 && (agendamentosRecentes == null || agendamentosRecentes.isEmpty());
    }

    private DashboardResponse montarDashboardContaNova(Long empresaId, Map<String, Object> dados) {
        return new DashboardResponse(
                empresaId,
                stringValor(dados.get("empresaNome")),
                100,
                List.of(new InsightItem(
                        "Bem-vindo ao Insights!",
                        "Sua conta esta pronta para receber analises quando voce registrar dados reais.",
                        "N/A",
                        "Baixa",
                        "info"
                )),
                List.of(),
                List.of(),
                List.of(new InsightAction(
                        "Registre seu primeiro cliente",
                        "Alta",
                        "Comece criando clientes para que o Gendaz possa analisar tendencias."
                )),
                "N/A",
                LocalDateTime.now(ZoneId.of(appTimezone))
        );
    }

    private String contextoRamo(Map<String, Object> dados) {
        String ramo = stringValor(dados.get("empresaRamo"));
        String display = stringValor(dados.get("empresaRamoDisplayName"));
        if (ramo.isBlank() && display.isBlank()) {
            return "OUTRO - ramo nao identificado";
        }
        if (ramo.isBlank()) {
            return display;
        }
        if (display.isBlank()) {
            return ramo;
        }
        return ramo + " - " + display;
    }

    private Optional<InsightEntity> ultimoDashboard(Long empresaId) {
        return insightRepository.findFirstByEmpresaIdAndTipoOrderByDataCriacaoDesc(empresaId, "dashboard");
    }

    private Optional<DashboardResponse> parseDashboard(InsightEntity insight) {
        try {
            if (insight.getResposta() == null || insight.getResposta().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(insight.getResposta(), DashboardResponse.class));
        } catch (Exception e) {
            log.warn("[INSIGHTS] empresa={} acao=READ_SNAPSHOT_PARSE_ERROR snapshotId={} erroTipo={}", insight.getEmpresaId(), insight.getId(), e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private InsightEntity salvarDashboard(Long empresaId, Map<String, Object> dados, DashboardResponse dashboard, String origem, LocalDateTime agora) {
        InsightEntity insight = InsightEntity.builder()
                .empresaId(empresaId)
                .tipo("dashboard")
                .pergunta("MANUAL - Dashboard")
                .resposta(serializar(dashboard))
                .payloadJson(serializar(dados))
                .origem(origem)
                .dataReferencia(agora)
                .dataExpiracao(null)
                .dataCriacao(agora)
                .build();
        return insightRepository.saveAndFlush(insight);
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

    private MeuGendazIAResponse responderClienteLocalmente(Long empresaId, String pergunta, Map<String, Object> dados) {
        String perguntaNormalizada = pergunta == null ? "" : pergunta.toLowerCase().trim();
        List<String> sugestoes = new ArrayList<>();
        String resposta;
        String acao = "nenhuma";

        // Detectar intenção da pergunta
        if (perguntaNormalizada.matches(".*(agendar|marcar|reservar|horario|horário|disponibilidade).*")) {
            resposta = "Claro! Para agendar, me diga qual serviço você gostaria e qual dia/horário prefere. Vamos confirmar tudo para você.";
            sugestoes = List.of("Quero agendar um serviço", "Ver horários disponíveis", "Ver serviços");
            acao = "agenda";
        } else if (perguntaNormalizada.matches(".*(reagendar|remarcar|alterar|mudar|trocar).*")) {
            resposta = "Para reagendar, me informe qual agendamento você quer alterar e qual o novo dia/horário.";
            sugestoes = List.of("Reagendar agendamento", "Ver meus agendamentos", "Cancelar agendamento");
            acao = "reagendar";
        } else if (perguntaNormalizada.matches(".*(cancelar|desmarcar|remover|excluir).*")) {
            resposta = "Para cancelar, me diga qual agendamento você quer desmarcar. Se precisar, pode informar o motivo.";
            sugestoes = List.of("Cancelar agendamento", "Ver meus agendamentos");
            acao = "cancelar";
        } else if (perguntaNormalizada.matches(".*(serviço|servicos|tratamento|procedimento|o que voces fazem|o que voces oferecem).*")) {
            resposta = "Oferecemos diversos serviços. Me diga qual você tem interesse ou se quer ver a lista completa.";
            sugestoes = List.of("Ver serviços", "Quero agendar", "Ver preços");
            acao = "servicos";
        } else if (perguntaNormalizada.matches(".*(preço|preco|valor|quanto custa|orçamento|orcamento|pagamento|forma de pagamento).*")) {
            resposta = "Os preços variam de acordo com o serviço. Me diga qual serviço você quer saber o valor ou se prefere ver a lista de preços.";
            sugestoes = List.of("Ver preços", "Quero agendar", "Ver serviços");
            acao = "precos";
        } else if (perguntaNormalizada.matches(".*(profissional|quem vai me atender|atendente|especialista).*")) {
            resposta = "Temos uma equipe qualificada para te atender. Me diga se você tem preferência por alguém ou se quer ver a lista de profissionais.";
            sugestoes = List.of("Ver profissionais", "Quero agendar", "Ver serviços");
            acao = "servicos";
        } else if (perguntaNormalizada.matches(".*(promoção|promocao|desconto|oferta|cupom).*")) {
            resposta = "Às vezes temos promoções! Me diga se você quer saber das ofertas atuais ou se quer agendar com desconto.";
            sugestoes = List.of("Ver promoções", "Quero agendar", "Ver serviços");
            acao = "servicos";
        } else {
            resposta = "Posso te ajudar com agendamentos, reagendamentos, cancelamentos, serviços, preços, profissionais e promoções. Como posso te ajudar?";
            sugestoes = List.of("Quero agendar", "Reagendar", "Cancelar", "Ver serviços");
        }

        return new MeuGendazIAResponse(
                resposta,
                sugestoes,
                acao,
                LocalDateTime.now(ZoneId.of(appTimezone))
        );
    }

    private void validarAcessoEmpresa(Long empresaId) {
        if (empresaId == null) {
            throw new BusinessException("Empresa nao identificada.");
        }
        Long empresaContexto = CompanyContext.requireCompanyId();
        if (!empresaContexto.equals(empresaId)) {
            throw new BusinessException("Acesso negado para esta empresa.");
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
        long atRisk = 0;

        long servicosSemMovimento = servicos.stream()
                .filter(servico -> longo(servico.get("vendas_30d")) <= 0)
                .count();
        long profissionaisSemMovimento = profissionais.stream()
                .filter(profissional -> longo(profissional.get("agendamentos_30d")) <= 0)
                .count();

        boolean quedaReceita = receita60 > 0 && receita30 < receita60;
        boolean riscoOciosidade = servicosSemMovimento > 0 || profissionaisSemMovimento > 0;
        boolean clienteEmRisco = false;
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
                    "Compense a queda recente de faturamento com uma campanha comercial para os serviços com melhor potencial.",
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
        return new InsightItem(
                "Base de Clientes",
                "A base cadastrada nao mostra alerta de clientes no momento.",
                "Sem acao critica pelos dados atuais",
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
                String descricao = primeiroNaoVazio(mapa, "descricao", "titulo", "texto");
                String urgencia = primeiroNaoVazio(mapa, "prioridade", "urgencia", "status");
                String impacto = primeiroNaoVazio(mapa, "impactoEstimado", "impacto", "valor", "resposta");
                itens.add(new InsightAction(descricao, urgencia, impacto));
            }
        }
        return itens.isEmpty() ? fallback : itens.size() > 4 ? itens.subList(0, 4) : itens;
    }

    private List<InsightItem> filtrarItensSemBaseReal(List<InsightItem> itens, long clientesInativos, long servicosInativos, long profissionaisInativos) {
        if (itens == null || itens.isEmpty()) return List.of();
        return itens.stream()
                .filter(item -> temBaseReal(textoDoItem(item), clientesInativos, servicosInativos, profissionaisInativos))
                .toList();
    }

    private List<InsightAction> filtrarAcoesSemBaseReal(List<InsightAction> acoes, long clientesInativos, long servicosInativos, long profissionaisInativos) {
        if (acoes == null || acoes.isEmpty()) return List.of();
        return acoes.stream()
                .filter(acao -> temBaseReal(textoDaAcao(acao), clientesInativos, servicosInativos, profissionaisInativos))
                .toList();
    }

    private boolean temBaseReal(String texto, long clientesInativos, long servicosInativos, long profissionaisInativos) {
        String normalizado = texto == null ? "" : texto.toLowerCase();
        boolean mencionaCliente = normalizado.contains("client");
        boolean clienteSemBase =
                normalizado.contains("reativ")
                        || normalizado.contains("inativ")
                        || normalizado.contains("risco")
                        || normalizado.contains("churn")
                        || normalizado.contains("sem retorno")
                        || normalizado.contains("sem agendamento");
        if (mencionaCliente && clienteSemBase && clientesInativos <= 0) {
            return false;
        }
        if (normalizado.contains("servi") && normalizado.contains("inativ") && servicosInativos <= 0) {
            return false;
        }
        if (normalizado.contains("profission") && normalizado.contains("inativ") && profissionaisInativos <= 0) {
            return false;
        }
        return true;
    }

    private String textoDoItem(InsightItem item) {
        if (item == null) return "";
        return String.join(" ", item.titulo(), item.descricao(), item.impacto(), item.urgencia(), item.tipo());
    }

    private String textoDaAcao(InsightAction acao) {
        if (acao == null) return "";
        return String.join(" ", acao.descricao(), acao.urgencia(), acao.impactoEstimado());
    }

    private List<InsightAction> montarAcoesReais(double pendente, long atRisk, List<Map<String, Object>> servicos, List<Map<String, Object>> profissionais, double receita30, double receita60) {
        List<InsightAction> acoes = new ArrayList<>();
        if (pendente > 0) {
            acoes.add(new InsightAction(
                    "Cobrar pagamentos pendentes",
                    "Alta",
                    formatarMoeda(pendente)
            ));
        }
        boolean servicoSemVenda = servicos.stream().anyMatch(s -> longo(s.get("vendas_30d")) <= 0);
        boolean profissionalOcioso = profissionais.stream().anyMatch(p -> longo(p.get("agendamentos_30d")) <= 0);
        if (servicoSemVenda) {
            acoes.add(new InsightAction(
                    "Divulgar serviço sem venda",
                    "Média",
                    "Criar campanha para o serviço com menor movimento"
            ));
        }
        if (profissionalOcioso) {
            acoes.add(new InsightAction(
                    "Redistribuir agenda",
                    "Média",
                    "Aproveitar profissionais com baixa ocupação"
            ));
        }
        if (acoes.isEmpty() && receita60 > 0 && receita30 < receita60) {
            acoes.add(new InsightAction(
                    "Recuperar receita perdida",
                    "Média",
                    "Divulgar os servicos com melhor potencial para recuperar faturamento"
            ));
        }
        if (acoes.isEmpty()) {
            acoes.add(new InsightAction(
                    "Acompanhar indicadores",
                    "Baixa",
                    "Nenhum sinal crítico suficiente para ação imediata"
            ));
        }
        return acoes.size() > 4 ? acoes.subList(0, 4) : acoes;
    }

    private List<InsightItem> montarOportunidadesReais(double pendente, long atRisk, List<Map<String, Object>> servicos, List<Map<String, Object>> profissionais, double receita30, double receita60) {
        List<InsightItem> oportunidades = new ArrayList<>();
        if (pendente > 0) {
            oportunidades.add(new InsightItem(
                    "Recuperar valores em aberto",
                    "Existe dinheiro pendente no financeiro e vale priorizar cobrança.",
                    "Baseado no saldo pendente real.",
                    formatarMoeda(pendente),
                    "Alta"
            ));
        }
        long servicosSemVenda = servicos.stream().filter(s -> longo(s.get("vendas_30d")) <= 0).count();
        if (servicosSemVenda > 0) {
            oportunidades.add(new InsightItem(
                    "Revisar serviços sem venda",
                    "Há serviços sem conversão no período e isso pede divulgação ou ajuste de oferta.",
                    servicosSemVenda + " serviços sem venda recente",
                    "Impacto não estimado",
                    "Média"
            ));
        }
        long profissionaisOciosos = profissionais.stream().filter(p -> longo(p.get("agendamentos_30d")) <= 0).count();
        if (profissionaisOciosos > 0) {
            oportunidades.add(new InsightItem(
                    "Aproveitar agenda ociosa",
                    "Existe capacidade parada que pode receber mais demanda.",
                    profissionaisOciosos + " profissionais com baixa ocupação",
                    "Impacto não estimado",
                    "Média"
            ));
        }
        if (receita60 > 0 && receita30 < receita60) {
            oportunidades.add(new InsightItem(
                    "Compensar queda de receita",
                    "A receita recente caiu em relação ao período anterior.",
                    "Comparação real de 30d vs 60d.",
                    "Impacto não estimado",
                    "Alta"
            ));
        }
        if (oportunidades.isEmpty()) {
            oportunidades.add(new InsightItem(
                    "Sem recomendacao no momento",
                    "Os dados reais sincronizados nao mostram uma acao prioritaria agora.",
                    "Sem sinal forte no período analisado.",
                    "Baixa",
                    "Média"
            ));
        }
        return oportunidades.size() > 3 ? oportunidades.subList(0, 3) : oportunidades;
    }

    private String primeiroNaoVazio(Map<?, ?> mapa, String... chaves) {
        for (String chave : chaves) {
            Object valor = mapa.get(chave);
            if (valor != null && !String.valueOf(valor).isBlank()) {
                return String.valueOf(valor);
            }
        }
        return "";
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

    private List<String> listaStrings(Object valor) {
        if (!(valor instanceof List<?> lista)) {
            return List.of();
        }
        List<String> itens = new ArrayList<>();
        for (Object item : lista) {
            String texto = String.valueOf(item == null ? "" : item).trim();
            if (!texto.isBlank()) {
                itens.add(texto);
            }
        }
        return itens.size() > 3 ? itens.subList(0, 3) : itens;
    }

    private String humanizarTexto(String texto) {
        if (texto == null) {
            return "";
        }
        return texto
                .replace("**", "")
                .replace("*", "")
                .replace("```", "")
                .replaceAll("(?m)^\\s*\\d+\\.\\s*", "")
                .replaceAll("\\s+", " ")
                .trim();
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


package com.minhaempresa.gendaz.insights.service;

import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.empresa.enums.RamoEmpresa;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity;
import com.minhaempresa.gendaz.profissional.repository.ProfissionalRepository;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import com.minhaempresa.gendaz.servico.repository.ServicoRepository;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InsightsAnalyzer {
    private final EmpresaRepository empresaRepository;
    private final ServicoRepository servicoRepository;
    private final ClienteRepository clienteRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final ProfissionalRepository profissionalRepository;
    private final PagamentoRepository pagamentoRepository;

    @Value("${app.timezone:America/Cuiaba}")
    private String appTimezone;

    public Map<String, Object> coletarDados(Long empresaId, Integer periodo) {
        EmpresaEntity empresa = empresaRepository.findById(empresaId).orElse(null);
        ZoneId zoneId = ZoneId.of(appTimezone);
        LocalDate hoje = LocalDate.now(zoneId);
        int dias = periodo == null || periodo <= 0 ? 30 : periodo;
        LocalDate inicioPeriodo = hoje.minusDays(dias - 1L);
        LocalDate inicioPeriodoAnterior = hoje.minusDays((dias * 2L) - 1L);
        LocalDate fimPeriodoAnterior = hoje.minusDays(dias);

        List<ServicoEntity> servicos = servicoRepository.findByEmpresaId(empresaId);
        List<ProfissionalEntity> profissionais = profissionalRepository.findByEmpresaId(empresaId);
        List<ClienteEntity> clientes = clienteRepository.findByEmpresaIdAndStatusNot(empresaId, StatusCadastro.EXCLUIDO);
        List<AgendamentoEntity> agendamentos = agendamentoRepository.findByEmpresaIdOperacional(empresaId, StatusCadastro.EXCLUIDO);
        List<PagamentoEntity> pagamentos = pagamentoRepository.findByEmpresaId(empresaId);

        List<Map<String, Object>> servicosAnalise = servicos.stream()
                .map(servico -> analisarServico(servico, agendamentos, pagamentos, inicioPeriodo, hoje))
                .toList();
        List<Map<String, Object>> profissionaisAnalise = profissionais.stream()
                .map(profissional -> analisarProfissional(profissional, agendamentos, pagamentos, inicioPeriodo, hoje))
                .toList();

        Map<Long, LocalDate> ultimaDataPorCliente = new HashMap<>();
        for (AgendamentoEntity agendamento : agendamentos) {
            if (agendamento.getCliente() == null || agendamento.getData() == null) continue;
            ultimaDataPorCliente.merge(
                    agendamento.getCliente().getId(),
                    agendamento.getData(),
                    (atual, nova) -> nova.isAfter(atual) ? nova : atual
            );
        }

        long ativos = clientes.stream().filter(cliente -> cliente.getStatus() == StatusCadastro.ATIVO).count();
        long atRisk = clientes.stream().filter(cliente -> {
            if (cliente.getStatus() != StatusCadastro.ATIVO || !ultimaDataPorCliente.containsKey(cliente.getId())) return false;
            long diasSem = diasDesdeUltimoAgendamento(cliente, ultimaDataPorCliente, hoje);
            return diasSem > 30 && diasSem <= 60;
        }).count();
        long churned = clientes.stream().filter(cliente -> cliente.getStatus() == StatusCadastro.INATIVO).count();
        long clientesAtivos = clientes.stream().filter(cliente -> cliente.getStatus() == StatusCadastro.ATIVO).count();
        long clientesInativos = clientes.stream().filter(cliente -> cliente.getStatus() == StatusCadastro.INATIVO).count();
        long servicosAtivos = servicos.stream().filter(servico -> servico.getStatus() == StatusCadastro.ATIVO).count();
        long servicosInativos = servicos.stream().filter(servico -> servico.getStatus() == StatusCadastro.INATIVO).count();
        long profissionaisAtivos = profissionais.stream().filter(profissional -> profissional.getStatus() == StatusCadastro.ATIVO).count();
        long profissionaisInativos = profissionais.stream().filter(profissional -> profissional.getStatus() == StatusCadastro.INATIVO).count();

        BigDecimal receitaPeriodo = somarPagamentos(pagamentos, inicioPeriodo, hoje, true);
        BigDecimal receitaPeriodoAnterior = somarPagamentos(pagamentos, inicioPeriodoAnterior, fimPeriodoAnterior, true);
        BigDecimal pendente = somarPagamentos(pagamentos, inicioPeriodo, hoje, false);
        long cancelamentos = agendamentos.stream()
                .filter(agendamento -> agendamento.getData() != null && !agendamento.getData().isBefore(inicioPeriodo) && !agendamento.getData().isAfter(hoje))
                .filter(agendamento -> agendamento.getStatus() == StatusAgendamento.CANCELADO)
                .count();
        long agendamentosPeriodo = agendamentos.stream()
                .filter(agendamento -> agendamento.getData() != null && !agendamento.getData().isBefore(inicioPeriodo) && !agendamento.getData().isAfter(hoje))
                .count();

        Map<String, Object> clientesResumo = new LinkedHashMap<>();
        clientesResumo.put("total", clientes.size());
        clientesResumo.put("ativos", ativos);
        clientesResumo.put("at_risk", atRisk);
        clientesResumo.put("churned", churned);
        clientesResumo.put("ativos_status", clientesAtivos);
        clientesResumo.put("inativos_status", clientesInativos);
        clientesResumo.put("lifetime_value_medio", calcularTicketMedio(pagamentos, clientes.size()));

        Map<String, Object> financeiroResumo = new LinkedHashMap<>();
        financeiroResumo.put("receitaPeriodoAtual", receitaPeriodo.doubleValue());
        financeiroResumo.put("receitaPeriodoAnterior", receitaPeriodoAnterior.doubleValue());
        financeiroResumo.put("pendente", pendente.doubleValue());
        financeiroResumo.put("cancelamentos", cancelamentos);
        financeiroResumo.put("tendencia", receitaPeriodo.compareTo(receitaPeriodoAnterior) >= 0 ? "crescimento" : "queda");

        Map<String, Object> resumo = new LinkedHashMap<>();
        resumo.put("servicos_total", servicos.size());
        resumo.put("profissionais_total", profissionais.size());
        resumo.put("servicos_ativos", servicosAtivos);
        resumo.put("servicos_inativos", servicosInativos);
        resumo.put("profissionais_ativos", profissionaisAtivos);
        resumo.put("profissionais_inativos", profissionaisInativos);
        resumo.put("clientes_total", clientes.size());
        resumo.put("clientes_at_risk", atRisk);
        resumo.put("clientes_inativos", clientesInativos);
        resumo.put("receita_confirmada", receitaPeriodo.doubleValue());
        resumo.put("pendente_cobranca", pendente.doubleValue());
        resumo.put("agendamentos_total", agendamentos.size());
        resumo.put("agendamentos_periodo", agendamentosPeriodo);
        resumo.put("agendamentos_cancelados", cancelamentos);

        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("empresaId", empresaId);
        dados.put("empresaNome", empresa != null ? empresa.getNomeFantasia() : "");
        RamoEmpresa ramo = empresa != null ? empresa.getRamo() : null;
        dados.put("empresaRamo", ramo != null ? ramo.name() : null);
        dados.put("empresaRamoDisplayName", ramo != null ? ramo.getDisplayName() : null);
        dados.put("periodo", dias);
        dados.put("servicos", servicosAnalise);
        dados.put("profissionais", profissionaisAnalise);
        dados.put("clientes", clientesResumo);
        dados.put("financeiro", financeiroResumo);
        dados.put("resumo", resumo);
        dados.put("topClientes", topClientesParaRecuperar(clientes, agendamentos, ultimaDataPorCliente, hoje));
        dados.put("clientesParaAtivar", clientesParaAtivar(clientes, agendamentos, ultimaDataPorCliente, hoje));
        dados.put("pagamentosRecentes", pagamentos.stream()
                .sorted(Comparator.comparing(PagamentoEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(5)
                .map(this::mapPagamentoResumo)
                .toList());
        dados.put("agendamentosRecentes", agendamentos.stream()
                .sorted(Comparator.comparing(AgendamentoEntity::getData, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AgendamentoEntity::getHoraInicio, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(this::mapAgendamentoResumo)
                .toList());

        return dados;
    }

    private Map<String, Object> analisarServico(ServicoEntity servico, List<AgendamentoEntity> agendamentos, List<PagamentoEntity> pagamentos, LocalDate inicio, LocalDate fim) {
        long total = agendamentos.stream()
                .filter(agendamento -> agendamento.getServico() != null && Objects.equals(agendamento.getServico().getId(), servico.getId()))
                .filter(agendamento -> agendamento.getData() != null && !agendamento.getData().isBefore(inicio) && !agendamento.getData().isAfter(fim))
                .filter(agendamento -> agendamento.getStatus() != StatusAgendamento.CANCELADO)
                .count();
        long cancelados = agendamentos.stream()
                .filter(agendamento -> agendamento.getServico() != null && Objects.equals(agendamento.getServico().getId(), servico.getId()))
                .filter(agendamento -> agendamento.getData() != null && !agendamento.getData().isBefore(inicio) && !agendamento.getData().isAfter(fim))
                .filter(agendamento -> agendamento.getStatus() == StatusAgendamento.CANCELADO)
                .count();
        BigDecimal receita = pagamentos.stream()
                .filter(pagamento -> pagamento.getAgendamento() != null && pagamento.getAgendamento().getServico() != null)
                .filter(pagamento -> Objects.equals(pagamento.getAgendamento().getServico().getId(), servico.getId()))
                .filter(pagamento -> isPago(pagamento.getStatus()))
                .filter(pagamento -> dentroDaJanela(pagamento.getDataPagamento(), inicio, fim))
                .map(PagamentoEntity::getValor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", servico.getId());
        item.put("nome", servico.getNome());
        item.put("valor", servico.getValor() == null ? 0 : servico.getValor().doubleValue());
        item.put("vendas_30d", total);
        item.put("cancelamentos", cancelados);
        item.put("receita_30d", receita.doubleValue());
        item.put("status", total > 0 ? "ativo" : "sem_movimento");
        return item;
    }

    private Map<String, Object> analisarProfissional(ProfissionalEntity profissional, List<AgendamentoEntity> agendamentos, List<PagamentoEntity> pagamentos, LocalDate inicio, LocalDate fim) {
        long total = agendamentos.stream()
                .filter(agendamento -> agendamento.getProfissional() != null && Objects.equals(agendamento.getProfissional().getId(), profissional.getId()))
                .filter(agendamento -> agendamento.getData() != null && !agendamento.getData().isBefore(inicio) && !agendamento.getData().isAfter(fim))
                .filter(agendamento -> agendamento.getStatus() != StatusAgendamento.CANCELADO)
                .count();
        long cancelados = agendamentos.stream()
                .filter(agendamento -> agendamento.getProfissional() != null && Objects.equals(agendamento.getProfissional().getId(), profissional.getId()))
                .filter(agendamento -> agendamento.getData() != null && !agendamento.getData().isBefore(inicio) && !agendamento.getData().isAfter(fim))
                .filter(agendamento -> agendamento.getStatus() == StatusAgendamento.CANCELADO)
                .count();
        BigDecimal receita = pagamentos.stream()
                .filter(pagamento -> pagamento.getAgendamento() != null && pagamento.getAgendamento().getProfissional() != null)
                .filter(pagamento -> Objects.equals(pagamento.getAgendamento().getProfissional().getId(), profissional.getId()))
                .filter(pagamento -> isPago(pagamento.getStatus()))
                .filter(pagamento -> dentroDaJanela(pagamento.getDataPagamento(), inicio, fim))
                .map(PagamentoEntity::getValor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", profissional.getId());
        item.put("nome", profissional.getNome());
        item.put("especialidade", profissional.getEspecialidade());
        item.put("agendamentos_30d", total);
        item.put("cancelamentos", cancelados);
        item.put("receita_30d", receita.doubleValue());
        item.put("status", total > 0 ? "ativo" : "sem_movimento");
        return item;
    }

    private long diasDesdeUltimoAgendamento(ClienteEntity cliente, Map<Long, LocalDate> ultimaDataPorCliente, LocalDate hoje) {
        LocalDate ultimaData = ultimaDataPorCliente.get(cliente.getId());
        if (ultimaData == null) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(ultimaData, hoje));
    }

    private Long diasSemAgendar(Map<String, Object> item) {
        Object valor = item.get("dias_sem_agendar");
        if (valor == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(valor));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> topClientesParaRecuperar(List<ClienteEntity> clientes, List<AgendamentoEntity> agendamentos, Map<Long, LocalDate> ultimaDataPorCliente, LocalDate hoje) {
        List<Map<String, Object>> comHistorico = infoClientes(clientes, agendamentos, ultimaDataPorCliente, hoje).stream()
                .filter(item -> diasSemAgendar(item) != null && diasSemAgendar(item) > 30)
                .sorted((a, b) -> {
                    long diasA = Long.parseLong(String.valueOf(a.get("dias_sem_agendar")));
                    long diasB = Long.parseLong(String.valueOf(b.get("dias_sem_agendar")));
                    int comparacao = Long.compare(diasB, diasA);
                    if (comparacao != 0) return comparacao;
                    return Long.compare(
                            Long.parseLong(String.valueOf(b.getOrDefault("total_atendimentos", 0L))),
                            Long.parseLong(String.valueOf(a.getOrDefault("total_atendimentos", 0L))));
                })
                .limit(5)
                .toList();
        // Recuperação: SOMENTE quem já foi atendido e está afastado há mais de 30 dias.
        // Quem nunca foi atendido é ativação/conversão e vai em "clientesParaAtivar".
        // Quem voltou nos últimos 30 dias não entra em nenhuma das duas listas.
        return Map.of("itens", comHistorico);
    }

    private Map<String, Object> clientesParaAtivar(List<ClienteEntity> clientes, List<AgendamentoEntity> agendamentos, Map<Long, LocalDate> ultimaDataPorCliente, LocalDate hoje) {
        List<Map<String, Object>> semHistorico = infoClientes(clientes, agendamentos, ultimaDataPorCliente, hoje).stream()
                .filter(item -> item.get("dias_sem_agendar") == null)
                .toList();
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("total", semHistorico.size());
        resultado.put("itens", semHistorico.stream().limit(5).toList());
        return resultado;
    }

    private List<Map<String, Object>> infoClientes(List<ClienteEntity> clientes, List<AgendamentoEntity> agendamentos, Map<Long, LocalDate> ultimaDataPorCliente, LocalDate hoje) {
        Map<Long, Long> atendimentosPorCliente = new HashMap<>();
        for (AgendamentoEntity agendamento : agendamentos) {
            if (agendamento.getCliente() == null || agendamento.getCliente().getId() == null) continue;
            if (agendamento.getStatus() == StatusAgendamento.CANCELADO) continue;
            atendimentosPorCliente.merge(agendamento.getCliente().getId(), 1L, Long::sum);
        }
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (ClienteEntity cliente : clientes) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", cliente.getId());
            item.put("nome", cliente.getNome());
            item.put("status", cliente.getStatus() == null ? null : cliente.getStatus().name());
            LocalDate ultimaData = ultimaDataPorCliente.get(cliente.getId());
            item.put("dias_sem_agendar", ultimaData == null ? null : diasDesdeUltimoAgendamento(cliente, ultimaDataPorCliente, hoje));
            item.put("total_atendimentos", atendimentosPorCliente.getOrDefault(cliente.getId(), 0L));
            resultado.add(item);
        }
        return resultado;
    }

    private Map<String, Object> mapPagamentoResumo(PagamentoEntity pagamento) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", pagamento.getId());
        item.put("clienteNome", pagamento.getCliente() != null ? pagamento.getCliente().getNome() : null);
        item.put("valor", pagamento.getValor() == null ? 0 : pagamento.getValor().doubleValue());
        item.put("metodoPagamento", pagamento.getMetodoPagamento() != null ? pagamento.getMetodoPagamento().name() : null);
        item.put("status", pagamento.getStatus() != null ? pagamento.getStatus().name() : null);
        item.put("dataPagamento", pagamento.getDataPagamento());
        return item;
    }

    private Map<String, Object> mapAgendamentoResumo(AgendamentoEntity agendamento) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", agendamento.getId());
        item.put("clienteNome", agendamento.getCliente() != null ? agendamento.getCliente().getNome() : null);
        item.put("servicoNome", agendamento.getServico() != null ? agendamento.getServico().getNome() : null);
        item.put("profissionalNome", agendamento.getProfissional() != null ? agendamento.getProfissional().getNome() : null);
        item.put("data", agendamento.getData());
        item.put("horaInicio", agendamento.getHoraInicio());
        item.put("status", agendamento.getStatus() != null ? agendamento.getStatus().name() : null);
        return item;
    }

    private boolean dentroDaJanela(LocalDateTime dataPagamento, LocalDate inicio, LocalDate fim) {
        if (dataPagamento == null) return false;
        LocalDate data = dataPagamento.toLocalDate();
        return !data.isBefore(inicio) && !data.isAfter(fim);
    }

    private BigDecimal somarPagamentos(List<PagamentoEntity> pagamentos, LocalDate inicio, LocalDate fim, boolean apenasConfirmados) {
        return pagamentos.stream()
                .filter(pagamento -> pagamento.getDataPagamento() != null)
                .filter(pagamento -> {
                    LocalDate data = pagamento.getDataPagamento().toLocalDate();
                    return !data.isBefore(inicio) && !data.isAfter(fim);
                })
                .filter(pagamento -> apenasConfirmados ? isPago(pagamento.getStatus()) : pagamento.getStatus() == StatusPagamento.PENDENTE)
                .map(PagamentoEntity::getValor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private double calcularTicketMedio(List<PagamentoEntity> pagamentos, int totalClientes) {
        if (totalClientes <= 0) return 0;
        BigDecimal total = pagamentos.stream()
                .filter(pagamento -> isPago(pagamento.getStatus()))
                .map(PagamentoEntity::getValor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.doubleValue() / totalClientes;
    }

    private boolean isPago(StatusPagamento status) {
        if (status == null) return false;
        // Dinheiro confirmado no fluxo operacional: somente PAGO.
        // PAYMENT_APPROVED pertence ao fluxo de plano/assinatura (ver PagamentoService)
        // e nao pode inflar receita, ticket medio ou somas do Insights.
        return status == StatusPagamento.PAGO;
    }
}


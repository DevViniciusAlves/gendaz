package com.minhaempresa.agendapro.insights.service;

import com.minhaempresa.agendapro.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.agendapro.agendamento.enums.StatusAgendamento;
import com.minhaempresa.agendapro.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.agendapro.cliente.entity.ClienteEntity;
import com.minhaempresa.agendapro.cliente.repository.ClienteRepository;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.repository.EmpresaRepository;
import com.minhaempresa.agendapro.pagamento.entity.PagamentoEntity;
import com.minhaempresa.agendapro.pagamento.enums.StatusPagamento;
import com.minhaempresa.agendapro.pagamento.repository.PagamentoRepository;
import com.minhaempresa.agendapro.profissional.entity.ProfissionalEntity;
import com.minhaempresa.agendapro.profissional.repository.ProfissionalRepository;
import com.minhaempresa.agendapro.servico.entity.ServicoEntity;
import com.minhaempresa.agendapro.servico.repository.ServicoRepository;
import com.minhaempresa.agendapro.shared.enums.StatusCadastro;
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
        List<ClienteEntity> clientes = clienteRepository.findByEmpresaId(empresaId);
        List<AgendamentoEntity> agendamentos = agendamentoRepository.findByEmpresaId(empresaId);
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

        long ativos = clientes.stream().filter(cliente -> diasDesdeUltimoAgendamento(cliente, ultimaDataPorCliente, hoje) <= 30).count();
        long atRisk = clientes.stream().filter(cliente -> {
            long diasSem = diasDesdeUltimoAgendamento(cliente, ultimaDataPorCliente, hoje);
            return diasSem > 30 && diasSem <= 60;
        }).count();
        long churned = clientes.stream().filter(cliente -> diasDesdeUltimoAgendamento(cliente, ultimaDataPorCliente, hoje) > 60).count();

        BigDecimal receitaPeriodo = somarPagamentos(pagamentos, inicioPeriodo, hoje, true);
        BigDecimal receitaPeriodoAnterior = somarPagamentos(pagamentos, inicioPeriodoAnterior, fimPeriodoAnterior, true);
        BigDecimal pendente = somarPagamentos(pagamentos, inicioPeriodo, hoje, false);
        long cancelamentos = agendamentos.stream()
                .filter(agendamento -> agendamento.getData() != null && !agendamento.getData().isBefore(inicioPeriodo))
                .filter(agendamento -> agendamento.getStatus() == StatusAgendamento.CANCELADO)
                .count();

        Map<String, Object> clientesResumo = new LinkedHashMap<>();
        clientesResumo.put("total", clientes.size());
        clientesResumo.put("ativos", ativos);
        clientesResumo.put("at_risk", atRisk);
        clientesResumo.put("churned", churned);
        clientesResumo.put("lifetime_value_medio", calcularTicketMedio(pagamentos, clientes.size()));

        Map<String, Object> financeiroResumo = new LinkedHashMap<>();
        financeiroResumo.put("receita_30d", receitaPeriodo.doubleValue());
        financeiroResumo.put("receita_60d", receitaPeriodoAnterior.doubleValue());
        financeiroResumo.put("pendente", pendente.doubleValue());
        financeiroResumo.put("cancelamentos", cancelamentos);
        financeiroResumo.put("tendencia", receitaPeriodo.compareTo(receitaPeriodoAnterior) >= 0 ? "crescimento" : "queda");

        Map<String, Object> resumo = new LinkedHashMap<>();
        resumo.put("servicos_total", servicos.size());
        resumo.put("profissionais_total", profissionais.size());
        resumo.put("clientes_total", clientes.size());
        resumo.put("clientes_at_risk", atRisk);
        resumo.put("receita_confirmada", receitaPeriodo.doubleValue());
        resumo.put("pendente_cobranca", pendente.doubleValue());
        resumo.put("agendamentos_total", agendamentos.size());
        resumo.put("agendamentos_cancelados", cancelamentos);

        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("empresaId", empresaId);
        dados.put("empresaNome", empresa != null ? empresa.getNomeFantasia() : "");
        dados.put("periodo", dias);
        dados.put("servicos", servicosAnalise);
        dados.put("profissionais", profissionaisAnalise);
        dados.put("clientes", clientesResumo);
        dados.put("financeiro", financeiroResumo);
        dados.put("resumo", resumo);
        dados.put("topClientes", topClientes(clienteRepository.findByEmpresaId(empresaId), ultimaDataPorCliente, hoje));
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
                .filter(agendamento -> agendamento.getStatus() == StatusAgendamento.CANCELADO)
                .count();
        BigDecimal receita = pagamentos.stream()
                .filter(pagamento -> pagamento.getAgendamento() != null && pagamento.getAgendamento().getServico() != null)
                .filter(pagamento -> Objects.equals(pagamento.getAgendamento().getServico().getId(), servico.getId()))
                .filter(pagamento -> isPago(pagamento.getStatus()))
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
                .filter(agendamento -> agendamento.getStatus() == StatusAgendamento.CANCELADO)
                .count();
        BigDecimal receita = pagamentos.stream()
                .filter(pagamento -> pagamento.getAgendamento() != null && pagamento.getAgendamento().getProfissional() != null)
                .filter(pagamento -> Objects.equals(pagamento.getAgendamento().getProfissional().getId(), profissional.getId()))
                .filter(pagamento -> isPago(pagamento.getStatus()))
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

    private Map<String, Object> topClientes(List<ClienteEntity> clientes, Map<Long, LocalDate> ultimaDataPorCliente, LocalDate hoje) {
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (ClienteEntity cliente : clientes) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", cliente.getId());
            item.put("nome", cliente.getNome());
            item.put("dias_sem_agendar", diasDesdeUltimoAgendamento(cliente, ultimaDataPorCliente, hoje));
            resultado.add(item);
        }
        resultado.sort(Comparator.comparingLong(item -> Long.parseLong(String.valueOf(item.get("dias_sem_agendar")))));
        return Map.of("itens", resultado.stream().limit(5).toList());
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
        return switch (status) {
            case PAGO, PAYMENT_APPROVED -> true;
            default -> false;
        };
    }
}

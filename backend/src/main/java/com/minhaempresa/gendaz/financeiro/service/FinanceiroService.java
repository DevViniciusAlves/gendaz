package com.minhaempresa.gendaz.financeiro.service;

import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.financeiro.dto.FinanceiroDtos.ItemResumoResponse;
import com.minhaempresa.gendaz.financeiro.dto.FinanceiroDtos.PagamentoRecenteItem;
import com.minhaempresa.gendaz.financeiro.dto.FinanceiroDtos.ResumoFinanceiroResponse;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FinanceiroService {
    private final PagamentoRepository pagamentoRepository;
    private final AgendamentoRepository agendamentoRepository;

    @Transactional(readOnly = true)
    public ResumoFinanceiroResponse resumo(Long empresaId, int mes, int ano) {
        Long empresaResolvida = resolverEmpresaAtual(empresaId);
        LocalDateTime inicio = LocalDate.of(ano, mes, 1).atStartOfDay();
        LocalDateTime fim = inicio.toLocalDate().withDayOfMonth(inicio.toLocalDate().lengthOfMonth()).atTime(LocalTime.MAX);
        List<PagamentoEntity> pagamentosMes = pagamentoRepository.findByEmpresaIdAndDataPagamentoBetween(empresaResolvida, inicio, fim);
        BigDecimal recebido = pagamentosMes.stream()
                .filter(p -> p.getStatus() == StatusPagamento.PAGO)
                .map(PagamentoEntity::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pendente = pagamentoRepository.findByEmpresaIdAndStatus(empresaResolvida, StatusPagamento.PENDENTE).stream()
                .map(PagamentoEntity::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var agendamentos = agendamentoRepository.findByEmpresaId(empresaResolvida);
        long consultasRealizadas = agendamentos.stream().filter(a -> a.getStatus() == StatusAgendamento.FINALIZADO).count();
        List<ItemResumoResponse> clientes = agendamentos.stream()
                .collect(Collectors.groupingBy(a -> a.getCliente().getNome(), Collectors.counting()))
                .entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).limit(5)
                .map(e -> new ItemResumoResponse(e.getKey(), e.getValue(), BigDecimal.ZERO)).toList();
        List<ItemResumoResponse> servicos = agendamentos.stream()
                .collect(Collectors.groupingBy(a -> a.getServico().getNome(), Collectors.counting()))
                .entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).limit(5)
                .map(e -> new ItemResumoResponse(e.getKey(), e.getValue(), BigDecimal.ZERO)).toList();
        List<PagamentoRecenteItem> pagamentosRecentes = pagamentosMes.stream()
                .sorted(Comparator.comparing(this::dataOrdenacaoPagamento, Comparator.nullsLast(Comparator.naturalOrder())).reversed()
                        .thenComparing(PagamentoEntity::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(this::toPagamentoRecenteItem)
                .toList();
        return new ResumoFinanceiroResponse(recebido, pendente, consultasRealizadas, pagamentosRecentes, clientes, servicos);
    }

    private PagamentoRecenteItem toPagamentoRecenteItem(PagamentoEntity pagamento) {
        return new PagamentoRecenteItem(
                pagamento.getId(),
                pagamento.getCliente() != null ? pagamento.getCliente().getNome() : "",
                pagamento.getValor(),
                pagamento.getMetodoPagamento() != null ? pagamento.getMetodoPagamento().name() : "",
                pagamento.getStatus() != null ? pagamento.getStatus().name() : "",
                pagamento.getDataPagamento()
        );
    }

    private LocalDateTime dataOrdenacaoPagamento(PagamentoEntity pagamento) {
        return pagamento.getDataPagamento() != null ? pagamento.getDataPagamento() : null;
    }

    private Long resolverEmpresaAtual(Long empresaId) {
        Long empresaContexto = CompanyContext.requireCompanyId();
        if (empresaId != null && !empresaContexto.equals(empresaId)) {
            throw new BusinessException("Empresa da sessao nao corresponde ao recurso solicitado.");
        }
        return empresaContexto;
    }
}


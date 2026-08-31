package com.minhaempresa.gendaz.financeiro.service;

import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.financeiro.dto.FinanceiroDtos.ItemResumoResponse;
import com.minhaempresa.gendaz.financeiro.dto.FinanceiroDtos.PagamentoRecenteItem;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos;
import com.minhaempresa.gendaz.financeiro.dto.FinanceiroDtos.ResumoFinanceiroResponse;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
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
    private final LogAtividadeService logAtividadeService;

    private static final java.util.Set<com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento> METODOS_CREDITO = java.util.Set.of(
            com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento.CREDITO,
            com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento.CREDIT_CARD,
            com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento.CARTAO
    );

    private boolean ehCreditoParcelado(PagamentoDtos.PagamentoResponse p) {
        return p.metodoPagamento() != null
                && METODOS_CREDITO.contains(p.metodoPagamento())
                && p.parcelas() != null
                && p.parcelas() > 1;
    }

    private BigDecimal valorDaParcela(PagamentoDtos.PagamentoResponse p, int index) {
        BigDecimal valorTotal = p.valor();
        int totalParcelas = p.parcelas();
        BigDecimal valorBase = valorTotal.divide(BigDecimal.valueOf(totalParcelas), 2, RoundingMode.HALF_UP);
        if (index < totalParcelas - 1) {
            return valorBase;
        }
        return valorTotal.subtract(valorBase.multiply(BigDecimal.valueOf(totalParcelas - 1)));
    }

    private List<PagamentoDtos.PagamentoResponse> expandirParcelasVirtuais(PagamentoDtos.PagamentoResponse p) {
        if (!ehCreditoParcelado(p)) {
            return List.of(p);
        }
        LocalDate dataBase = p.dataPagamento() != null ? p.dataPagamento().toLocalDate() : null;
        if (dataBase == null) {
            return List.of(p);
        }
        int totalParcelas = p.parcelas();
        List<PagamentoDtos.PagamentoResponse> resultado = new ArrayList<>();
        for (int i = 0; i < totalParcelas; i++) {
            LocalDate dataParcela = dataBase.plusMonths(i);
            LocalDateTime dataParcelaLdt = dataParcela.atStartOfDay();
            BigDecimal valorParcela = valorDaParcela(p, i);
            resultado.add(new PagamentoDtos.PagamentoResponse(
                    p.id(),
                    p.agendamentoId(),
                    p.protocolo(),
                    p.servicoNome(),
                    p.clienteId(),
                    p.clienteNome(),
                    p.empresaId(),
                    valorParcela,
                    p.metodoPagamento(),
                    p.parcelas(),
                    p.status(),
                    dataParcelaLdt,
                    p.statusCliente()
            ));
        }
        return resultado;
    }

    @Transactional(readOnly = true)
    public ResumoFinanceiroResponse resumo(Long empresaId, int mes, int ano) {
        Long empresaResolvida = resolverEmpresaAtual(empresaId);
        LocalDate inicioMes = LocalDate.of(ano, mes, 1);
        LocalDate fimMes = inicioMes.withDayOfMonth(inicioMes.lengthOfMonth());

        List<PagamentoDtos.PagamentoResponse> todosPagamentos = pagamentoRepository.findByEmpresaIdForFinanceiro(empresaResolvida);

        List<PagamentoDtos.PagamentoResponse> pagamentosExpandidos = todosPagamentos.stream()
                .flatMap(p -> expandirParcelasVirtuais(p).stream())
                .toList();

        List<PagamentoDtos.PagamentoResponse> pagamentosMes = pagamentosExpandidos.stream()
                .filter(p -> p.dataPagamento() != null)
                .filter(p -> {
                    LocalDate dataPag = p.dataPagamento().toLocalDate();
                    return !dataPag.isBefore(inicioMes) && !dataPag.isAfter(fimMes);
                })
                .toList();

        BigDecimal recebido = pagamentosMes.stream()
                .filter(p -> p.status() == StatusPagamento.PAGO)
                .map(PagamentoDtos.PagamentoResponse::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pendente = pagamentoRepository.somarValorByEmpresaIdAndStatusIn(empresaResolvida, List.of(StatusPagamento.PENDENTE));

        long consultasRealizadas = agendamentoRepository.countConsultasFinalizadas(empresaResolvida);

        List<ItemResumoResponse> clientes = agendamentoRepository.resumoClientesMaisAgendados(
                empresaResolvida, StatusAgendamento.CANCELADO, org.springframework.data.domain.PageRequest.of(0, 5));

        List<ItemResumoResponse> servicos = agendamentoRepository.resumoServicosMaisAgendadosFinanceiro(
                empresaResolvida, StatusAgendamento.CANCELADO, org.springframework.data.domain.PageRequest.of(0, 5));

        List<PagamentoRecenteItem> pagamentosRecentes = pagamentosMes.stream()
                .limit(10)
                .map(p -> new PagamentoRecenteItem(
                        p.id(),
                        p.clienteNome(),
                        p.statusCliente(),
                        p.valor(),
                        p.metodoPagamento() != null ? p.metodoPagamento().name() : "",
                        p.status() != null ? p.status().name() : "",
                        p.dataPagamento()
                ))
                .toList();

        return new ResumoFinanceiroResponse(recebido, pendente, consultasRealizadas, pagamentosRecentes, clientes, servicos);
    }

    private PagamentoRecenteItem toPagamentoRecenteItem(PagamentoEntity pagamento) {
        return new PagamentoRecenteItem(
                pagamento.getId(),
                pagamento.getCliente() != null ? pagamento.getCliente().getNome() : "",
                pagamento.getCliente() != null ? pagamento.getCliente().getStatus() : null,
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


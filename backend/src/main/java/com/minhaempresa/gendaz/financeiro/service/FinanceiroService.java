package com.minhaempresa.gendaz.financeiro.service;

import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.financeiro.dto.FinanceiroDtos.ItemResumoResponse;
import com.minhaempresa.gendaz.financeiro.dto.FinanceiroDtos.PagamentoRecenteItem;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos;
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
        
        // Usar queries seguras que retornam DTOs e não carregam a coluna clientes.status
        List<PagamentoDtos.PagamentoResponse> pagamentosMes = pagamentoRepository.findByEmpresaIdAndDataPagamentoBetweenForFinanceiro(empresaResolvida, inicio, fim);
        
        BigDecimal recebido = pagamentosMes.stream()
                .filter(p -> p.status() == StatusPagamento.PAGO)
                .map(PagamentoDtos.PagamentoResponse::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
        BigDecimal pendente = pagamentoRepository.somarValorByEmpresaIdAndStatusIn(empresaResolvida, List.of(StatusPagamento.PENDENTE));
        
        // Queries diretas para evitar carregar a entidade AgendamentoEntity e ClienteEntity completas
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


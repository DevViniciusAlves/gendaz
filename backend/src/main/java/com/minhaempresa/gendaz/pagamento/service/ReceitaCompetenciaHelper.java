package com.minhaempresa.gendaz.pagamento.service;

import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Regra unica de competencia financeira usada pelo Financeiro e pelo Dashboard.
 * Pagamentos parcelados no credito sao projetados por competencia: cada parcela
 * cai no mes correspondente a partir da dataPagamento (mes + indice da parcela).
 */
public final class ReceitaCompetenciaHelper {

    private static final Set<MetodoPagamento> METODOS_CREDITO = Set.of(
            MetodoPagamento.CREDITO,
            MetodoPagamento.CREDIT_CARD,
            MetodoPagamento.CARTAO
    );

    private ReceitaCompetenciaHelper() {}

    public static boolean ehCreditoParcelado(PagamentoDtos.PagamentoResponse p) {
        return p.metodoPagamento() != null
                && METODOS_CREDITO.contains(p.metodoPagamento())
                && p.parcelas() != null
                && p.parcelas() > 1;
    }

    public static BigDecimal valorDaParcela(PagamentoDtos.PagamentoResponse p, int index) {
        BigDecimal valorTotal = p.valor();
        int totalParcelas = p.parcelas();
        BigDecimal valorBase = valorTotal.divide(BigDecimal.valueOf(totalParcelas), 2, RoundingMode.HALF_UP);
        if (index < totalParcelas - 1) {
            return valorBase;
        }
        return valorTotal.subtract(valorBase.multiply(BigDecimal.valueOf(totalParcelas - 1)));
    }

    public static List<PagamentoDtos.PagamentoResponse> expandirParcelasVirtuais(PagamentoDtos.PagamentoResponse p) {
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
}
package com.minhaempresa.gendaz.financeiro.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.financeiro.dto.FinanceiroDtos.ResumoFinanceiroResponse;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class FinanceiroServiceTest {
    @Mock
    private PagamentoRepository pagamentoRepository;
    @Mock
    private AgendamentoRepository agendamentoRepository;
    private FinanceiroService service;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new FinanceiroService(pagamentoRepository, agendamentoRepository, mock(LogAtividadeService.class));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    void deveUsarEmpresaDaSessaoQuandoParametroNaoForEnviado() {
        CompanyContext.setCompanyId(9L);
        when(pagamentoRepository.findByEmpresaIdForFinanceiro(eq(9L))).thenReturn(List.of());
        when(pagamentoRepository.somarValorByEmpresaIdAndStatusIn(eq(9L), any())).thenReturn(BigDecimal.ZERO);
        when(agendamentoRepository.countConsultasFinalizadas(eq(9L))).thenReturn(0L);
        when(agendamentoRepository.resumoClientesMaisAgendados(eq(9L), any(), any())).thenReturn(List.of());
        when(agendamentoRepository.resumoServicosMaisAgendadosFinanceiro(eq(9L), any(), any())).thenReturn(List.of());

        assertDoesNotThrow(() -> {
            ResumoFinanceiroResponse resposta = service.resumo(null, 8, 2026);
            org.junit.jupiter.api.Assertions.assertNotNull(resposta);
        });

        verify(pagamentoRepository).findByEmpresaIdForFinanceiro(eq(9L));
    }

    @Test
    void deveRejeitarEmpresaDivergenteDaSessao() {
        CompanyContext.setCompanyId(9L);

        assertThrows(BusinessException.class, () -> service.resumo(1L, 8, 2026));
    }

    @Test
    void financeiroSomaOValorFinalPersistidoNoPagamento() {
        CompanyContext.setCompanyId(9L);

        PagamentoDtos.PagamentoResponse pagoComCupom = criarPagamentoResponse(1L, 9L, new BigDecimal("50.00"), StatusPagamento.PAGO, MetodoPagamento.PIX, null, LocalDateTime.of(2026, 8, 15, 10, 0));
        PagamentoDtos.PagamentoResponse pagoCheio = criarPagamentoResponse(2L, 9L, new BigDecimal("100.00"), StatusPagamento.PAGO, MetodoPagamento.PIX, null, LocalDateTime.of(2026, 8, 20, 14, 0));
        PagamentoDtos.PagamentoResponse pendente = criarPagamentoResponse(3L, 9L, new BigDecimal("30.00"), StatusPagamento.PENDENTE, MetodoPagamento.PIX, null, LocalDateTime.of(2026, 8, 10, 9, 0));

        when(pagamentoRepository.findByEmpresaIdForFinanceiro(eq(9L)))
                .thenReturn(List.of(pagoComCupom, pagoCheio, pendente));
        when(pagamentoRepository.somarValorByEmpresaIdAndStatusIn(eq(9L), any()))
                .thenReturn(new BigDecimal("30.00"));
        when(agendamentoRepository.countConsultasFinalizadas(eq(9L))).thenReturn(0L);
        when(agendamentoRepository.resumoClientesMaisAgendados(eq(9L), any(), any())).thenReturn(List.of());
        when(agendamentoRepository.resumoServicosMaisAgendadosFinanceiro(eq(9L), any(), any())).thenReturn(List.of());

        ResumoFinanceiroResponse resposta = service.resumo(null, 8, 2026);

        assertEquals(0, new BigDecimal("150.00").compareTo(resposta.totalRecebidoMes()));
        assertEquals(0, new BigDecimal("30.00").compareTo(resposta.totalPendente()));
    }

    @Test
    void credito3xEmMarcoDeveDistribuir100PorMes() {
        CompanyContext.setCompanyId(9L);

        PagamentoDtos.PagamentoResponse pagamento = criarPagamentoResponse(
                1L, 9L, new BigDecimal("300.00"), StatusPagamento.PAGO,
                MetodoPagamento.CREDITO, 3, LocalDateTime.of(2026, 3, 10, 10, 0));

        when(pagamentoRepository.findByEmpresaIdForFinanceiro(eq(9L)))
                .thenReturn(List.of(pagamento));
        when(pagamentoRepository.somarValorByEmpresaIdAndStatusIn(eq(9L), any()))
                .thenReturn(BigDecimal.ZERO);
        when(agendamentoRepository.countConsultasFinalizadas(eq(9L))).thenReturn(0L);
        when(agendamentoRepository.resumoClientesMaisAgendados(eq(9L), any(), any())).thenReturn(List.of());
        when(agendamentoRepository.resumoServicosMaisAgendadosFinanceiro(eq(9L), any(), any())).thenReturn(List.of());

        ResumoFinanceiroResponse marco = service.resumo(null, 3, 2026);
        assertEquals(0, new BigDecimal("100.00").compareTo(marco.totalRecebidoMes()));

        ResumoFinanceiroResponse abril = service.resumo(null, 4, 2026);
        assertEquals(0, new BigDecimal("100.00").compareTo(abril.totalRecebidoMes()));

        ResumoFinanceiroResponse maio = service.resumo(null, 5, 2026);
        assertEquals(0, new BigDecimal("100.00").compareTo(maio.totalRecebidoMes()));
    }

    @Test
    void credito3xSomaTotalDeveSerExatamente100() {
        CompanyContext.setCompanyId(9L);

        PagamentoDtos.PagamentoResponse pagamento = criarPagamentoResponse(
                1L, 9L, new BigDecimal("100.00"), StatusPagamento.PAGO,
                MetodoPagamento.CREDITO, 3, LocalDateTime.of(2026, 8, 10, 10, 0));

        when(pagamentoRepository.findByEmpresaIdForFinanceiro(eq(9L)))
                .thenReturn(List.of(pagamento));
        when(pagamentoRepository.somarValorByEmpresaIdAndStatusIn(eq(9L), any()))
                .thenReturn(BigDecimal.ZERO);
        when(agendamentoRepository.countConsultasFinalizadas(eq(9L))).thenReturn(0L);
        when(agendamentoRepository.resumoClientesMaisAgendados(eq(9L), any(), any())).thenReturn(List.of());
        when(agendamentoRepository.resumoServicosMaisAgendadosFinanceiro(eq(9L), any(), any())).thenReturn(List.of());

        ResumoFinanceiroResponse agosto = service.resumo(null, 8, 2026);
        assertEquals(0, new BigDecimal("33.33").compareTo(agosto.totalRecebidoMes()));

        ResumoFinanceiroResponse setembro = service.resumo(null, 9, 2026);
        assertEquals(0, new BigDecimal("33.33").compareTo(setembro.totalRecebidoMes()));

        ResumoFinanceiroResponse outubro = service.resumo(null, 10, 2026);
        assertEquals(0, new BigDecimal("33.34").compareTo(outubro.totalRecebidoMes()));

        BigDecimal total = agosto.totalRecebidoMes().add(setembro.totalRecebidoMes()).add(outubro.totalRecebidoMes());
        assertEquals(0, new BigDecimal("100.00").compareTo(total));
    }

    @Test
    void pagamento1xDeveFicarIntegralmenteNoMesOriginal() {
        CompanyContext.setCompanyId(9L);

        PagamentoDtos.PagamentoResponse pagamento = criarPagamentoResponse(
                1L, 9L, new BigDecimal("200.00"), StatusPagamento.PAGO,
                MetodoPagamento.PIX, 1, LocalDateTime.of(2026, 6, 5, 10, 0));

        when(pagamentoRepository.findByEmpresaIdForFinanceiro(eq(9L)))
                .thenReturn(List.of(pagamento));
        when(pagamentoRepository.somarValorByEmpresaIdAndStatusIn(eq(9L), any()))
                .thenReturn(BigDecimal.ZERO);
        when(agendamentoRepository.countConsultasFinalizadas(eq(9L))).thenReturn(0L);
        when(agendamentoRepository.resumoClientesMaisAgendados(eq(9L), any(), any())).thenReturn(List.of());
        when(agendamentoRepository.resumoServicosMaisAgendadosFinanceiro(eq(9L), any(), any())).thenReturn(List.of());

        ResumoFinanceiroResponse junho = service.resumo(null, 6, 2026);
        assertEquals(0, new BigDecimal("200.00").compareTo(junho.totalRecebidoMes()));

        ResumoFinanceiroResponse julho = service.resumo(null, 7, 2026);
        assertEquals(0, BigDecimal.ZERO.compareTo(julho.totalRecebidoMes()));
    }

    @Test
    void pagamentoPIXDinheiroDeveComportamentoAntigoPreservado() {
        CompanyContext.setCompanyId(9L);

        PagamentoDtos.PagamentoResponse pagamentoPix = criarPagamentoResponse(
                1L, 9L, new BigDecimal("150.00"), StatusPagamento.PAGO,
                MetodoPagamento.PIX, null, LocalDateTime.of(2026, 4, 12, 10, 0));
        PagamentoDtos.PagamentoResponse pagamentoDinheiro = criarPagamentoResponse(
                2L, 9L, new BigDecimal("75.00"), StatusPagamento.PAGO,
                MetodoPagamento.DINHEIRO, null, LocalDateTime.of(2026, 4, 15, 14, 0));

        when(pagamentoRepository.findByEmpresaIdForFinanceiro(eq(9L)))
                .thenReturn(List.of(pagamentoPix, pagamentoDinheiro));
        when(pagamentoRepository.somarValorByEmpresaIdAndStatusIn(eq(9L), any()))
                .thenReturn(BigDecimal.ZERO);
        when(agendamentoRepository.countConsultasFinalizadas(eq(9L))).thenReturn(0L);
        when(agendamentoRepository.resumoClientesMaisAgendados(eq(9L), any(), any())).thenReturn(List.of());
        when(agendamentoRepository.resumoServicosMaisAgendadosFinanceiro(eq(9L), any(), any())).thenReturn(List.of());

        ResumoFinanceiroResponse abril = service.resumo(null, 4, 2026);
        assertEquals(0, new BigDecimal("225.00").compareTo(abril.totalRecebidoMes()));
    }

    @Test
    void pagamentoDebitoDeveFicarIntegralmenteNoMesOriginal() {
        CompanyContext.setCompanyId(9L);

        PagamentoDtos.PagamentoResponse pagamento = criarPagamentoResponse(
                1L, 9L, new BigDecimal("80.00"), StatusPagamento.PAGO,
                MetodoPagamento.DEBITO, null, LocalDateTime.of(2026, 5, 10, 10, 0));

        when(pagamentoRepository.findByEmpresaIdForFinanceiro(eq(9L)))
                .thenReturn(List.of(pagamento));
        when(pagamentoRepository.somarValorByEmpresaIdAndStatusIn(eq(9L), any()))
                .thenReturn(BigDecimal.ZERO);
        when(agendamentoRepository.countConsultasFinalizadas(eq(9L))).thenReturn(0L);
        when(agendamentoRepository.resumoClientesMaisAgendados(eq(9L), any(), any())).thenReturn(List.of());
        when(agendamentoRepository.resumoServicosMaisAgendadosFinanceiro(eq(9L), any(), any())).thenReturn(List.of());

        ResumoFinanceiroResponse maio = service.resumo(null, 5, 2026);
        assertEquals(0, new BigDecimal("80.00").compareTo(maio.totalRecebidoMes()));

        ResumoFinanceiroResponse junho = service.resumo(null, 6, 2026);
        assertEquals(0, BigDecimal.ZERO.compareTo(junho.totalRecebidoMes()));
    }

    @Test
    void filtroOutubroDeveMostrarApenasParcelaDeOutubro() {
        CompanyContext.setCompanyId(9L);

        PagamentoDtos.PagamentoResponse pagamento = criarPagamentoResponse(
                1L, 9L, new BigDecimal("300.00"), StatusPagamento.PAGO,
                MetodoPagamento.CREDITO, 3, LocalDateTime.of(2026, 8, 10, 10, 0));

        when(pagamentoRepository.findByEmpresaIdForFinanceiro(eq(9L)))
                .thenReturn(List.of(pagamento));
        when(pagamentoRepository.somarValorByEmpresaIdAndStatusIn(eq(9L), any()))
                .thenReturn(BigDecimal.ZERO);
        when(agendamentoRepository.countConsultasFinalizadas(eq(9L))).thenReturn(0L);
        when(agendamentoRepository.resumoClientesMaisAgendados(eq(9L), any(), any())).thenReturn(List.of());
        when(agendamentoRepository.resumoServicosMaisAgendadosFinanceiro(eq(9L), any(), any())).thenReturn(List.of());

        ResumoFinanceiroResponse outubro = service.resumo(null, 10, 2026);
        assertEquals(1, outubro.pagamentosRecentes().size());
        assertEquals(0, new BigDecimal("100.00").compareTo(outubro.pagamentosRecentes().get(0).valor()));
    }

    @Test
    void credito2xDezembroParaJaneiro() {
        CompanyContext.setCompanyId(9L);

        PagamentoDtos.PagamentoResponse pagamento = criarPagamentoResponse(
                1L, 9L, new BigDecimal("200.00"), StatusPagamento.PAGO,
                MetodoPagamento.CREDITO, 2, LocalDateTime.of(2025, 12, 20, 10, 0));

        when(pagamentoRepository.findByEmpresaIdForFinanceiro(eq(9L)))
                .thenReturn(List.of(pagamento));
        when(pagamentoRepository.somarValorByEmpresaIdAndStatusIn(eq(9L), any()))
                .thenReturn(BigDecimal.ZERO);
        when(agendamentoRepository.countConsultasFinalizadas(eq(9L))).thenReturn(0L);
        when(agendamentoRepository.resumoClientesMaisAgendados(eq(9L), any(), any())).thenReturn(List.of());
        when(agendamentoRepository.resumoServicosMaisAgendadosFinanceiro(eq(9L), any(), any())).thenReturn(List.of());

        ResumoFinanceiroResponse dezembro = service.resumo(null, 12, 2025);
        assertEquals(0, new BigDecimal("100.00").compareTo(dezembro.totalRecebidoMes()));

        ResumoFinanceiroResponse janeiro = service.resumo(null, 1, 2026);
        assertEquals(0, new BigDecimal("100.00").compareTo(janeiro.totalRecebidoMes()));
    }

    @Test
    void dataFinalDoMesNaoDeveGerarErro() {
        CompanyContext.setCompanyId(9L);

        PagamentoDtos.PagamentoResponse pagamento = criarPagamentoResponse(
                1L, 9L, new BigDecimal("300.00"), StatusPagamento.PAGO,
                MetodoPagamento.CREDITO, 3, LocalDateTime.of(2026, 1, 31, 10, 0));

        when(pagamentoRepository.findByEmpresaIdForFinanceiro(eq(9L)))
                .thenReturn(List.of(pagamento));
        when(pagamentoRepository.somarValorByEmpresaIdAndStatusIn(eq(9L), any()))
                .thenReturn(BigDecimal.ZERO);
        when(agendamentoRepository.countConsultasFinalizadas(eq(9L))).thenReturn(0L);
        when(agendamentoRepository.resumoClientesMaisAgendados(eq(9L), any(), any())).thenReturn(List.of());
        when(agendamentoRepository.resumoServicosMaisAgendadosFinanceiro(eq(9L), any(), any())).thenReturn(List.of());

        ResumoFinanceiroResponse janeiro = service.resumo(null, 1, 2026);
        assertEquals(0, new BigDecimal("100.00").compareTo(janeiro.totalRecebidoMes()));

        ResumoFinanceiroResponse fevereiro = service.resumo(null, 2, 2026);
        assertEquals(0, new BigDecimal("100.00").compareTo(fevereiro.totalRecebidoMes()));

        ResumoFinanceiroResponse marco = service.resumo(null, 3, 2026);
        assertEquals(0, new BigDecimal("100.00").compareTo(marco.totalRecebidoMes()));
    }

    @Test
    void filtroMarcoDeveMostrarApenasParcelaDeMarco() {
        CompanyContext.setCompanyId(9L);

        PagamentoDtos.PagamentoResponse pagamento = criarPagamentoResponse(
                1L, 9L, new BigDecimal("300.00"), StatusPagamento.PAGO,
                MetodoPagamento.CREDITO, 3, LocalDateTime.of(2026, 3, 10, 10, 0));

        when(pagamentoRepository.findByEmpresaIdForFinanceiro(eq(9L)))
                .thenReturn(List.of(pagamento));
        when(pagamentoRepository.somarValorByEmpresaIdAndStatusIn(eq(9L), any()))
                .thenReturn(BigDecimal.ZERO);
        when(agendamentoRepository.countConsultasFinalizadas(eq(9L))).thenReturn(0L);
        when(agendamentoRepository.resumoClientesMaisAgendados(eq(9L), any(), any())).thenReturn(List.of());
        when(agendamentoRepository.resumoServicosMaisAgendadosFinanceiro(eq(9L), any(), any())).thenReturn(List.of());

        ResumoFinanceiroResponse marco = service.resumo(null, 3, 2026);
        assertEquals(1, marco.pagamentosRecentes().size());
        assertEquals(0, new BigDecimal("100.00").compareTo(marco.pagamentosRecentes().get(0).valor()));
    }

    @Test
    void filtroAbrilDeveMostrarApenasParcelaDeAbril() {
        CompanyContext.setCompanyId(9L);

        PagamentoDtos.PagamentoResponse pagamento = criarPagamentoResponse(
                1L, 9L, new BigDecimal("300.00"), StatusPagamento.PAGO,
                MetodoPagamento.CREDITO, 3, LocalDateTime.of(2026, 3, 10, 10, 0));

        when(pagamentoRepository.findByEmpresaIdForFinanceiro(eq(9L)))
                .thenReturn(List.of(pagamento));
        when(pagamentoRepository.somarValorByEmpresaIdAndStatusIn(eq(9L), any()))
                .thenReturn(BigDecimal.ZERO);
        when(agendamentoRepository.countConsultasFinalizadas(eq(9L))).thenReturn(0L);
        when(agendamentoRepository.resumoClientesMaisAgendados(eq(9L), any(), any())).thenReturn(List.of());
        when(agendamentoRepository.resumoServicosMaisAgendadosFinanceiro(eq(9L), any(), any())).thenReturn(List.of());

        ResumoFinanceiroResponse abril = service.resumo(null, 4, 2026);
        assertEquals(1, abril.pagamentosRecentes().size());
        assertEquals(0, new BigDecimal("100.00").compareTo(abril.pagamentosRecentes().get(0).valor()));
    }

    @Test
    void resumoCardMensalUsaSomenteValorDaParcelaDaqueleMes() {
        CompanyContext.setCompanyId(9L);

        PagamentoDtos.PagamentoResponse pagamento = criarPagamentoResponse(
                1L, 9L, new BigDecimal("300.00"), StatusPagamento.PAGO,
                MetodoPagamento.CREDITO, 3, LocalDateTime.of(2026, 3, 10, 10, 0));

        when(pagamentoRepository.findByEmpresaIdForFinanceiro(eq(9L)))
                .thenReturn(List.of(pagamento));
        when(pagamentoRepository.somarValorByEmpresaIdAndStatusIn(eq(9L), any()))
                .thenReturn(BigDecimal.ZERO);
        when(agendamentoRepository.countConsultasFinalizadas(eq(9L))).thenReturn(0L);
        when(agendamentoRepository.resumoClientesMaisAgendados(eq(9L), any(), any())).thenReturn(List.of());
        when(agendamentoRepository.resumoServicosMaisAgendadosFinanceiro(eq(9L), any(), any())).thenReturn(List.of());

        ResumoFinanceiroResponse marco = service.resumo(null, 3, 2026);
        assertEquals(0, new BigDecimal("100.00").compareTo(marco.totalRecebidoMes()));
        assertEquals(1, marco.pagamentosRecentes().size());
    }

    private PagamentoDtos.PagamentoResponse criarPagamentoResponse(
            Long id, Long empresaId, BigDecimal valor, StatusPagamento status,
            MetodoPagamento metodo, Integer parcelas, LocalDateTime dataPagamento) {
        return new PagamentoDtos.PagamentoResponse(
                id, null, "PROTO-" + id, "Servico", 2L, "Cliente",
                empresaId, valor, metodo, parcelas, status, dataPagamento,
                com.minhaempresa.gendaz.shared.enums.StatusCadastro.ATIVO);
    }
}


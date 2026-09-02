package com.minhaempresa.gendaz.dashboard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.conversa.repository.ConversaRepository;
import com.minhaempresa.gendaz.dashboard.dto.DashboardDtos.DashboardReceitaDiaItem;
import com.minhaempresa.gendaz.dashboard.dto.DashboardDtos.DashboardResumoResponse;
import com.minhaempresa.gendaz.dashboard.dto.DashboardDtos.DashboardItemResumo;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.profissional.repository.ProfissionalRepository;
import com.minhaempresa.gendaz.servico.repository.ServicoRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;

class DashboardServiceTest {
    private static final ZoneId ZONE_SP = ZoneId.of("America/Sao_Paulo");
    @Mock UsuarioRepository usuarioRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock ServicoRepository servicoRepository;
    @Mock ProfissionalRepository profissionalRepository;
    @Mock AssinaturaService assinaturaService;
    @Mock ConversaRepository conversaRepository;
    @Mock AgendamentoRepository agendamentoRepository;
    @Mock PagamentoRepository pagamentoRepository;

    private DashboardService service;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new DashboardService(
                usuarioRepository, clienteRepository, servicoRepository, profissionalRepository,
                assinaturaService, conversaRepository, agendamentoRepository, pagamentoRepository);
    }

    private LocalDate hoje() {
        return LocalDate.now(ZONE_SP);
    }

    private UsuarioEntity usuarioComEmpresa() {
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).nomeFantasia("Empresa A").build();
        return UsuarioEntity.builder().id(7L).empresa(empresa).build();
    }

    private UsuarioEntity usuarioComEmpresaTimezone(String timezone) {
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).nomeFantasia("Empresa A").timezone(timezone).build();
        return UsuarioEntity.builder().id(7L).empresa(empresa).build();
    }

    private void preparaResumoBasico() {
        UsuarioEntity usuario = usuarioComEmpresa();
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(usuario));
        when(conversaRepository.countAbertasByEmpresaId(1L)).thenReturn(0L);
        when(clienteRepository.countByEmpresaIdAndStatusNot(1L, StatusCadastro.EXCLUIDO)).thenReturn(0L);
        when(servicoRepository.countAtivosByEmpresaId(1L)).thenReturn(0L);
        when(profissionalRepository.countAtivosByEmpresaId(1L)).thenReturn(0L);
        when(agendamentoRepository.findTop5ByEmpresaIdAndStatusInAndDataGreaterThanEqualAndClienteStatusNotOrderByDataAscHoraInicioAsc(
                anyLong(), any(), any(), any())).thenReturn(List.of());
        when(agendamentoRepository.findTop10ByEmpresaIdAndClienteStatusNotOrderByDataDescHoraInicioDesc(anyLong(), any())).thenReturn(List.of());
        when(agendamentoRepository.resumoServicosMaisAgendados(anyLong(), any(), any(), any())).thenReturn(List.of());
        when(agendamentoRepository.resumoProfissionaisMaisAgendados(anyLong(), any(), any(), any())).thenReturn(List.of());
        when(pagamentoRepository.findByEmpresaIdForFinanceiro(1L)).thenReturn(List.of());
        when(pagamentoRepository.findByEmpresaIdAndStatusInClienteAtivoAgendamentoNaoCanceladoOrderByIdDesc(anyLong(), any(), any(), any()))
                .thenReturn(List.of());
        when(assinaturaService.buscarAtualPorEmpresa(1L)).thenReturn(Optional.empty());
        when(pagamentoRepository.somarValorByEmpresaIdAndStatusInClienteAtivoAgendamentoNaoCancelado(eq(1L), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(pagamentoRepository.countByEmpresaIdAndStatusInClienteAtivoAgendamentoNaoCancelado(anyLong(), any(), any(), any()))
                .thenReturn(0L);
    }

    private PagamentoDtos.PagamentoResponse pagamento(
            Long id, BigDecimal valor, StatusPagamento status, MetodoPagamento metodo,
            Integer parcelas, LocalDateTime dataPagamento) {
        return new PagamentoDtos.PagamentoResponse(
                id, null, "PROTO-" + id, "Servico", 2L, "Cliente",
                1L, valor, metodo, parcelas, status, dataPagamento, StatusCadastro.ATIVO);
    }

    private BigDecimal somaReceitaPorDia(DashboardResumoResponse response) {
        return response.receitaPorDia().stream()
                .map(DashboardReceitaDiaItem::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void agendamentosHojeExcluiCancelado() {
        preparaResumoBasico();
        when(agendamentoRepository.countByEmpresaIdAndDataAndStatusNotAndClienteStatusNot(1L, hoje(), StatusAgendamento.CANCELADO, StatusCadastro.EXCLUIDO))
                .thenReturn(2L);

        DashboardResumoResponse response = service.resumo(7L, null, null, null);

        assertEquals(2L, response.agendamentosHoje());
        verify(agendamentoRepository).countByEmpresaIdAndDataAndStatusNotAndClienteStatusNot(
                eq(1L), eq(hoje()), eq(StatusAgendamento.CANCELADO), eq(StatusCadastro.EXCLUIDO));
        verify(agendamentoRepository, never()).countByEmpresaIdAndData(anyLong(), any());
    }

    @Test
    void agendamentosHojeZeroQuandoSoExistemAgendamentosEmOutrosDias() {
        preparaResumoBasico();
        // Cenario A: hoje = 01/09, existem agendamentos em 02/09 e 15/09 -> "Agendamentos hoje" = 0
        when(agendamentoRepository.countByEmpresaIdAndDataAndStatusNotAndClienteStatusNot(1L, hoje(), StatusAgendamento.CANCELADO, StatusCadastro.EXCLUIDO))
                .thenReturn(0L);

        DashboardResumoResponse response = service.resumo(7L, null, null, null);

        assertEquals(0L, response.agendamentosHoje());
    }

    @Test
    void agendamentosHojeUsaFusoHorarioDaEmpresa() {
        UsuarioEntity usuario = usuarioComEmpresaTimezone("America/Cuiaba");
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(usuario));
        when(conversaRepository.countAbertasByEmpresaId(1L)).thenReturn(0L);
        when(clienteRepository.countByEmpresaIdAndStatusNot(1L, StatusCadastro.EXCLUIDO)).thenReturn(0L);
        when(servicoRepository.countAtivosByEmpresaId(1L)).thenReturn(0L);
        when(profissionalRepository.countAtivosByEmpresaId(1L)).thenReturn(0L);
        when(agendamentoRepository.findTop5ByEmpresaIdAndStatusInAndDataGreaterThanEqualAndClienteStatusNotOrderByDataAscHoraInicioAsc(
                anyLong(), any(), any(), any())).thenReturn(List.of());
        when(agendamentoRepository.findTop10ByEmpresaIdAndClienteStatusNotOrderByDataDescHoraInicioDesc(anyLong(), any())).thenReturn(List.of());
        when(agendamentoRepository.resumoServicosMaisAgendados(anyLong(), any(), any(), any())).thenReturn(List.of());
        when(agendamentoRepository.resumoProfissionaisMaisAgendados(anyLong(), any(), any(), any())).thenReturn(List.of());
        when(pagamentoRepository.findByEmpresaIdForFinanceiro(1L)).thenReturn(List.of());
        when(pagamentoRepository.findByEmpresaIdAndStatusInClienteAtivoAgendamentoNaoCanceladoOrderByIdDesc(anyLong(), any(), any(), any()))
                .thenReturn(List.of());
        when(pagamentoRepository.somarValorByEmpresaIdAndStatusInClienteAtivoAgendamentoNaoCancelado(anyLong(), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(pagamentoRepository.countByEmpresaIdAndStatusInClienteAtivoAgendamentoNaoCancelado(anyLong(), any(), any(), any()))
                .thenReturn(0L);
        when(assinaturaService.buscarAtualPorEmpresa(1L)).thenReturn(Optional.empty());

        service.resumo(7L, null, null, null);

        LocalDate hojeCuiaba = LocalDate.now(ZoneId.of("America/Cuiaba"));
        verify(agendamentoRepository).countByEmpresaIdAndDataAndStatusNotAndClienteStatusNot(
                eq(1L), eq(hojeCuiaba), eq(StatusAgendamento.CANCELADO), eq(StatusCadastro.EXCLUIDO));
    }

    @Test
    void pendenciaCobrancaUsaSomenteStatusPendente() {
        preparaResumoBasico();
        when(agendamentoRepository.countByEmpresaIdAndDataAndStatusNotAndClienteStatusNot(1L, hoje(), StatusAgendamento.CANCELADO, StatusCadastro.EXCLUIDO))
                .thenReturn(0L);

        service.resumo(7L, null, null, null);

        ArgumentCaptor<List<StatusPagamento>> statusCaptor = ArgumentCaptor.forClass(List.class);
        verify(pagamentoRepository)
                .somarValorByEmpresaIdAndStatusInClienteAtivoAgendamentoNaoCancelado(eq(1L), statusCaptor.capture(), eq(StatusCadastro.EXCLUIDO), eq(StatusAgendamento.CANCELADO));
        // Receita confirmada nao usa mais o somatorio sem periodo; so a pendencia de cobranca (estado atual)
        List<StatusPagamento> statuses = statusCaptor.getValue();
        assertEquals(List.of(StatusPagamento.PENDENTE, StatusPagamento.PAYMENT_PENDING), statuses);
    }

    @Test
    void pendenciaPagamentoNaListaUsoNaoIncluiCancelado() {
        preparaResumoBasico();
        when(agendamentoRepository.countByEmpresaIdAndDataAndStatusNotAndClienteStatusNot(1L, hoje(), StatusAgendamento.CANCELADO, StatusCadastro.EXCLUIDO))
                .thenReturn(0L);

        service.resumo(7L, null, null, null);

        verify(pagamentoRepository).findByEmpresaIdAndStatusInClienteAtivoAgendamentoNaoCanceladoOrderByIdDesc(
                eq(1L), eq(List.of(StatusPagamento.PENDENTE, StatusPagamento.PAYMENT_PENDING)), eq(StatusCadastro.EXCLUIDO), eq(StatusAgendamento.CANCELADO));
    }

    @Test
    void profissionaisMaisAgendadosUsaSomenteProfissionaisReais() {
        preparaResumoBasico();
        when(agendamentoRepository.countByEmpresaIdAndDataAndStatusNotAndClienteStatusNot(1L, hoje(), StatusAgendamento.CANCELADO, StatusCadastro.EXCLUIDO))
                .thenReturn(0L);
        // Linha com profissional inexistente (valor nulo) nao pode virar ranking
        when(agendamentoRepository.resumoProfissionaisMaisAgendados(1L, StatusAgendamento.CANCELADO, StatusCadastro.EXCLUIDO, PageRequest.of(0, 5)))
                .thenReturn(List.of(
                        new Object[]{10L, "Maria", 4L},
                        new Object[]{11L, "Joao", 2L},
                        new Object[]{null, "Sem preferencia", 3L}
                ));

        DashboardResumoResponse response = service.resumo(7L, null, null, null);

        var nomes = response.profissionaisMaisAgendados().stream().map(DashboardItemResumo::nome).toList();
        assertEquals(java.util.List.of("Maria", "Joao"), nomes);
        assertEquals(4L, response.profissionaisMaisAgendados().get(0).quantidade());
    }

    @Test
    void pendentesPagamentoCardExpoeContagemDoBackend() {
        preparaResumoBasico();
        when(agendamentoRepository.countByEmpresaIdAndDataAndStatusNotAndClienteStatusNot(1L, hoje(), StatusAgendamento.CANCELADO, StatusCadastro.EXCLUIDO))
                .thenReturn(0L);
        when(pagamentoRepository.countByEmpresaIdAndStatusInClienteAtivoAgendamentoNaoCancelado(
                eq(1L), eq(List.of(StatusPagamento.PENDENTE, StatusPagamento.PAYMENT_PENDING)), eq(StatusCadastro.EXCLUIDO), eq(StatusAgendamento.CANCELADO)))
                .thenReturn(3L);

        DashboardResumoResponse response = service.resumo(7L, null, null, null);

        assertEquals(3L, response.pendentesPagamento());
    }

    @Test
    void receitaDoMesRespeitaMesSelecionado() {
        preparaResumoBasico();
        LocalDate hoje = hoje();
        LocalDate mesAnterior = hoje.minusMonths(1);
        when(pagamentoRepository.findByEmpresaIdForFinanceiro(1L))
                .thenReturn(List.of(pagamento(1L, new BigDecimal("100.00"), StatusPagamento.PAGO, MetodoPagamento.PIX, null, mesAnterior.withDayOfMonth(15).atTime(10, 0))));

        DashboardResumoResponse mesAnteriorResposta = service.resumo(7L, null, mesAnterior.getMonthValue(), mesAnterior.getYear());
        assertEquals(0, new BigDecimal("100.00").compareTo(mesAnteriorResposta.receitaConfirmada()));
        assertEquals(mesAnterior.lengthOfMonth(), mesAnteriorResposta.receitaPorDia().size());

        DashboardResumoResponse mesAtualResposta = service.resumo(7L, null, hoje.getMonthValue(), hoje.getYear());
        assertEquals(0, BigDecimal.ZERO.compareTo(mesAtualResposta.receitaConfirmada()));
        assertEquals(0, BigDecimal.ZERO.compareTo(somaReceitaPorDia(mesAtualResposta)));
    }

    @Test
    void credito3xSegueCompetenciaFinanceiraDoMesSelecionado() {
        preparaResumoBasico();
        LocalDate hoje = hoje();
        LocalDate mesPagamento = hoje.minusMonths(2).withDayOfMonth(1);
        LocalDate mesCompetencia1 = mesPagamento;
        LocalDate mesCompetencia2 = mesPagamento.plusMonths(1);
        LocalDate mesCompetencia3 = mesPagamento.plusMonths(2);
        when(pagamentoRepository.findByEmpresaIdForFinanceiro(1L))
                .thenReturn(List.of(pagamento(1L, new BigDecimal("100.00"), StatusPagamento.PAGO, MetodoPagamento.CREDITO, 3, mesPagamento.atTime(10, 0))));

        // Parcela 1/3 no mes do pagamento
        DashboardResumoResponse p1 = service.resumo(7L, null, mesCompetencia1.getMonthValue(), mesCompetencia1.getYear());
        assertEquals(0, new BigDecimal("33.33").compareTo(p1.receitaConfirmada()));

        // Parcela 2/3 no mes seguinte (mesmo que o pagamento original tenha sido criado no mes anterior)
        DashboardResumoResponse p2 = service.resumo(7L, null, mesCompetencia2.getMonthValue(), mesCompetencia2.getYear());
        assertEquals(0, new BigDecimal("33.33").compareTo(p2.receitaConfirmada()));

        // Parcela 3/3 no segundo mes seguinte
        DashboardResumoResponse p3 = service.resumo(7L, null, mesCompetencia3.getMonthValue(), mesCompetencia3.getYear());
        assertEquals(0, new BigDecimal("33.34").compareTo(p3.receitaConfirmada()));

        // Soma das competencias fecha exatamente o valor total
        BigDecimal total = p1.receitaConfirmada().add(p2.receitaConfirmada()).add(p3.receitaConfirmada());
        assertEquals(0, new BigDecimal("100.00").compareTo(total));
    }

    @Test
    void canceladoNaoEntraNaReceita() {
        preparaResumoBasico();
        LocalDate mesAnterior = hoje().minusMonths(1);
        when(pagamentoRepository.findByEmpresaIdForFinanceiro(1L))
                .thenReturn(List.of(pagamento(1L, new BigDecimal("50.00"), StatusPagamento.CANCELADO, MetodoPagamento.PIX, null, mesAnterior.withDayOfMonth(15).atTime(10, 0))));

        DashboardResumoResponse resposta = service.resumo(7L, null, mesAnterior.getMonthValue(), mesAnterior.getYear());

        assertEquals(0, BigDecimal.ZERO.compareTo(resposta.receitaConfirmada()));
        assertEquals(0, BigDecimal.ZERO.compareTo(somaReceitaPorDia(resposta)));
    }

    @Test
    void pendenteNaoEntraNaReceita() {
        preparaResumoBasico();
        LocalDate mesAnterior = hoje().minusMonths(1);
        when(pagamentoRepository.findByEmpresaIdForFinanceiro(1L))
                .thenReturn(List.of(pagamento(1L, new BigDecimal("30.00"), StatusPagamento.PENDENTE, MetodoPagamento.PIX, null, mesAnterior.withDayOfMonth(15).atTime(10, 0))));

        DashboardResumoResponse resposta = service.resumo(7L, null, mesAnterior.getMonthValue(), mesAnterior.getYear());

        assertEquals(0, BigDecimal.ZERO.compareTo(resposta.receitaConfirmada()));
        assertEquals(0, BigDecimal.ZERO.compareTo(somaReceitaPorDia(resposta)));
    }

    @Test
    void pagamentoAprovadoEntraNaReceita() {
        preparaResumoBasico();
        LocalDate mesAnterior = hoje().minusMonths(1);
        when(pagamentoRepository.findByEmpresaIdForFinanceiro(1L))
                .thenReturn(List.of(pagamento(1L, new BigDecimal("70.00"), StatusPagamento.PAYMENT_APPROVED, MetodoPagamento.PIX, null, mesAnterior.withDayOfMonth(15).atTime(10, 0))));

        DashboardResumoResponse resposta = service.resumo(7L, null, mesAnterior.getMonthValue(), mesAnterior.getYear());

        assertEquals(0, new BigDecimal("70.00").compareTo(resposta.receitaConfirmada()));
    }

    @Test
    void mesAtualReceitaPorDiaTerminaNoDiaDeHoje() {
        preparaResumoBasico();
        LocalDate hoje = hoje();
        when(pagamentoRepository.findByEmpresaIdForFinanceiro(1L))
                .thenReturn(List.of(pagamento(1L, new BigDecimal("100.00"), StatusPagamento.PAGO, MetodoPagamento.PIX, null, hoje.atTime(12, 0))));

        DashboardResumoResponse resposta = service.resumo(7L, null, null, null);

        assertEquals(hoje.getDayOfMonth(), resposta.receitaPorDia().size(),
                "Mes atual nao pode gerar dias futuros");
        assertEquals(hoje.toString(), resposta.receitaPorDia().get(resposta.receitaPorDia().size() - 1).data(),
                "Ultimo ponto deve ser a data de hoje");
    }

    @Test
    void mesPassadoGeraMesCompleto() {
        preparaResumoBasico();
        LocalDate mesAnterior = hoje().minusMonths(1);

        DashboardResumoResponse resposta = service.resumo(7L, null, mesAnterior.getMonthValue(), mesAnterior.getYear());

        assertEquals(mesAnterior.lengthOfMonth(), resposta.receitaPorDia().size());
    }

    @Test
    void diaSemReceitaApareceComoZero() {
        preparaResumoBasico();
        LocalDate mesAnterior = hoje().minusMonths(1);
        when(pagamentoRepository.findByEmpresaIdForFinanceiro(1L))
                .thenReturn(List.of(pagamento(1L, new BigDecimal("25.00"), StatusPagamento.PAGO, MetodoPagamento.PIX, null, mesAnterior.withDayOfMonth(15).atTime(10, 0))));

        DashboardResumoResponse resposta = service.resumo(7L, null, mesAnterior.getMonthValue(), mesAnterior.getYear());

        long diasComValor = resposta.receitaPorDia().stream()
                .filter(item -> item.valor().compareTo(BigDecimal.ZERO) != 0)
                .count();
        assertEquals(1, diasComValor);
        assertEquals(0, BigDecimal.ZERO.compareTo(resposta.receitaPorDia().get(0).valor()));
    }

    @Test
    void cardReceitaIgualSomaDoGrafico() {
        preparaResumoBasico();
        LocalDate mesAnterior = hoje().minusMonths(1);
        when(pagamentoRepository.findByEmpresaIdForFinanceiro(1L))
                .thenReturn(List.of(
                        pagamento(1L, new BigDecimal("40.00"), StatusPagamento.PAGO, MetodoPagamento.PIX, null, mesAnterior.withDayOfMonth(2).atTime(10, 0)),
                        pagamento(2L, new BigDecimal("60.00"), StatusPagamento.PAYMENT_APPROVED, MetodoPagamento.DEBITO, null, mesAnterior.withDayOfMonth(20).atTime(10, 0))));

        DashboardResumoResponse resposta = service.resumo(7L, null, mesAnterior.getMonthValue(), mesAnterior.getYear());

        assertEquals(0, resposta.receitaConfirmada().setScale(2).compareTo(somaReceitaPorDia(resposta).setScale(2)),
                "Receita do mes deve ser igual a soma dos pontos do grafico");
    }

    @Test
    void mesInvalidoRejeitado() {
        preparaResumoBasico();

        assertThrows(BusinessException.class, () -> service.resumo(7L, null, 0, 2026));
        assertThrows(BusinessException.class, () -> service.resumo(7L, null, 13, 2026));
    }

    @Test
    void anoInvalidoRejeitado() {
        preparaResumoBasico();

        assertThrows(BusinessException.class, () -> service.resumo(7L, null, 5, 1999));
        assertThrows(BusinessException.class, () -> service.resumo(7L, null, 5, 2101));
    }

    @Test
    void empresaDivergenteDaSessaoRejeitada() {
        preparaResumoBasico();

        assertThrows(BusinessException.class, () -> service.resumo(7L, 999L, null, null));
    }

    @Test
    void mesAnoDefaultUsamMesAtual() {
        preparaResumoBasico();
        LocalDate hoje = hoje();
        when(pagamentoRepository.findByEmpresaIdForFinanceiro(1L))
                .thenReturn(List.of(pagamento(1L, new BigDecimal("100.00"), StatusPagamento.PAGO, MetodoPagamento.PIX, null, hoje.atTime(12, 0))));

        DashboardResumoResponse resposta = service.resumo(7L, null, null, null);

        assertEquals(hoje.getDayOfMonth(), resposta.receitaPorDia().size());
        assertEquals(0, resposta.receitaConfirmada().setScale(2).compareTo(somaReceitaPorDia(resposta).setScale(2)));
    }
}
package com.minhaempresa.gendaz.insights.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.profissional.repository.ProfissionalRepository;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import com.minhaempresa.gendaz.servico.repository.ServicoRepository;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

class InsightsAnalyzerTest {
    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private ServicoRepository servicoRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private AgendamentoRepository agendamentoRepository;
    @Mock
    private ProfissionalRepository profissionalRepository;
    @Mock
    private PagamentoRepository pagamentoRepository;

    private AutoCloseable mocks;
    private InsightsAnalyzer analyzer;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        analyzer = new InsightsAnalyzer(empresaRepository, servicoRepository, clienteRepository,
                agendamentoRepository, profissionalRepository, pagamentoRepository);
        ReflectionTestUtils.setField(analyzer, "appTimezone", "America/Cuiaba");
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void receitaPorServicoUsaSomenteJanelaDe30Dias() {
        ZoneId zone = ZoneId.of("America/Cuiaba");
        LocalDate hoje = LocalDate.now(zone);
        ServicoEntity servico = ServicoEntity.builder().id(10L).nome("Corte")
                .valor(new BigDecimal("100")).status(StatusCadastro.ATIVO).build();
        AgendamentoEntity agendamento = agendamento(1L, null, servico, hoje.minusDays(5));
        PagamentoEntity recente = pagamento(1L, agendamento, new BigDecimal("100"),
                StatusPagamento.PAGO, LocalDateTime.now(zone).minusDays(5));
        PagamentoEntity antigo = pagamento(2L, agendamento, new BigDecimal("200"),
                StatusPagamento.PAGO, LocalDateTime.now(zone).minusDays(100));
        base(servico, List.of(recente, antigo), List.of());

        Map<String, Object> dados = analyzer.coletarDados(1L, 30);

        List<Map<String, Object>> servicos = (List<Map<String, Object>>) dados.get("servicos");
        assertEquals(100.0, ((Number) servicos.get(0).get("receita_30d")).doubleValue());
        Map<String, Object> financeiro = (Map<String, Object>) dados.get("financeiro");
        assertEquals(100.0, ((Number) financeiro.get("receitaPeriodoAtual")).doubleValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancelamentosPorServicoUsamSomenteJanelaDe30Dias() {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Cuiaba"));
        ServicoEntity servico = ServicoEntity.builder().id(10L).nome("Corte")
                .valor(new BigDecimal("100")).status(StatusCadastro.ATIVO).build();
        AgendamentoEntity recente = agendamentoCancelado(1L, servico, hoje.minusDays(3));
        AgendamentoEntity antigo = agendamentoCancelado(2L, servico, hoje.minusDays(100));
        base(servico, List.of(), List.of(recente, antigo));

        Map<String, Object> dados = analyzer.coletarDados(1L, 30);

        List<Map<String, Object>> servicos = (List<Map<String, Object>>) dados.get("servicos");
        assertEquals(1L, ((Number) servicos.get(0).get("cancelamentos")).longValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void receitaPeriodoAnteriorRepresentaOs30DiasAnteriores() {
        ZoneId zone = ZoneId.of("America/Cuiaba");
        ServicoEntity servico = ServicoEntity.builder().id(10L).nome("Corte")
                .valor(new BigDecimal("100")).status(StatusCadastro.ATIVO).build();
        AgendamentoEntity agendamento = agendamento(1L, null, servico, LocalDate.now(zone).minusDays(40));
        PagamentoEntity anterior = pagamento(1L, agendamento, new BigDecimal("300"),
                StatusPagamento.PAGO, LocalDateTime.now(zone).minusDays(40));
        base(servico, List.of(anterior), List.of());

        Map<String, Object> dados = analyzer.coletarDados(1L, 30);

        Map<String, Object> financeiro = (Map<String, Object>) dados.get("financeiro");
        assertEquals(0.0, ((Number) financeiro.get("receitaPeriodoAtual")).doubleValue());
        assertEquals(300.0, ((Number) financeiro.get("receitaPeriodoAnterior")).doubleValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void topClientesPriorizaQuemEstaHaMaisTempoSemAgendar() {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Cuiaba"));
        ClienteEntity afastado = cliente(1L, "Afastado");
        ClienteEntity antigo = cliente(2L, "Antigo");
        AgendamentoEntity agAfastado = agendamento(1L, afastado, null, hoje.minusDays(35));
        AgendamentoEntity agAntigo = agendamento(2L, antigo, null, hoje.minusDays(50));
        ServicoEntity servico = ServicoEntity.builder().id(10L).nome("Corte")
                .valor(new BigDecimal("100")).status(StatusCadastro.ATIVO).build();
        base(servico, List.of(), List.of(agAfastado, agAntigo), List.of(afastado, antigo));

        Map<String, Object> dados = analyzer.coletarDados(1L, 30);

        Map<String, Object> top = (Map<String, Object>) dados.get("topClientes");
        List<Map<String, Object>> itens = (List<Map<String, Object>>) top.get("itens");
        assertTrue(itens.size() >= 2);
        assertEquals("Antigo", String.valueOf(itens.get(0).get("nome")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void topClientesExcluiQuemNuncaFoiAtendido() {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Cuiaba"));
        ClienteEntity antigo = cliente(2L, "Antigo");
        ClienteEntity nuncaVeio = cliente(3L, "NuncaVeio");
        AgendamentoEntity agAntigo = agendamento(2L, antigo, null, hoje.minusDays(50));
        ServicoEntity servico = ServicoEntity.builder().id(10L).nome("Corte")
                .valor(new BigDecimal("100")).status(StatusCadastro.ATIVO).build();
        base(servico, List.of(), List.of(agAntigo), List.of(antigo, nuncaVeio));

        Map<String, Object> dados = analyzer.coletarDados(1L, 30);

        Map<String, Object> top = (Map<String, Object>) dados.get("topClientes");
        List<Map<String, Object>> itens = (List<Map<String, Object>>) top.get("itens");
        assertEquals(1, itens.size());
        assertEquals("Antigo", String.valueOf(itens.get(0).get("nome")));

        Map<String, Object> ativar = (Map<String, Object>) dados.get("clientesParaAtivar");
        assertEquals(1, ((Number) ativar.get("total")).intValue());
        List<Map<String, Object>> pendentes = (List<Map<String, Object>>) ativar.get("itens");
        assertEquals("NuncaVeio", String.valueOf(pendentes.get(0).get("nome")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void paymentApprovedNaoContaComoReceitaOperacional() {
        ZoneId zone = ZoneId.of("America/Cuiaba");
        LocalDate hoje = LocalDate.now(zone);
        ServicoEntity servico = ServicoEntity.builder().id(10L).nome("Corte")
                .valor(new BigDecimal("100")).status(StatusCadastro.ATIVO).build();
        AgendamentoEntity agendamento = agendamento(1L, null, servico, hoje.minusDays(5));
        PagamentoEntity plano = pagamento(1L, agendamento, new BigDecimal("200"),
                StatusPagamento.PAYMENT_APPROVED, LocalDateTime.now(zone).minusDays(5));
        base(servico, List.of(plano), List.of());

        Map<String, Object> dados = analyzer.coletarDados(1L, 30);

        Map<String, Object> financeiro = (Map<String, Object>) dados.get("financeiro");
        assertEquals(0.0, ((Number) financeiro.get("receitaPeriodoAtual")).doubleValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void topClientesExcluiQuemVoltouRecentemente() {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Cuiaba"));
        ClienteEntity joao = cliente(1L, "Joao");
        ClienteEntity maria = cliente(2L, "Maria");
        ClienteEntity carlos = cliente(3L, "Carlos");
        AgendamentoEntity agJoao = agendamento(1L, joao, null, hoje.minusDays(3));
        AgendamentoEntity agMaria = agendamento(2L, maria, null, hoje.minusDays(8));
        AgendamentoEntity agCarlos = agendamento(3L, carlos, null, hoje.minusDays(12));
        ServicoEntity servico = ServicoEntity.builder().id(10L).nome("Corte")
                .valor(new BigDecimal("100")).status(StatusCadastro.ATIVO).build();
        base(servico, List.of(), List.of(agJoao, agMaria, agCarlos), List.of(joao, maria, carlos));

        Map<String, Object> dados = analyzer.coletarDados(1L, 30);

        Map<String, Object> top = (Map<String, Object>) dados.get("topClientes");
        List<Map<String, Object>> itens = (List<Map<String, Object>>) top.get("itens");
        assertTrue(itens.isEmpty());

        Map<String, Object> ativar = (Map<String, Object>) dados.get("clientesParaAtivar");
        assertEquals(0, ((Number) ativar.get("total")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancelamentosIgnoramAgendamentosFuturos() {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Cuiaba"));
        ServicoEntity servico = ServicoEntity.builder().id(10L).nome("Corte")
                .valor(new BigDecimal("100")).status(StatusCadastro.ATIVO).build();
        AgendamentoEntity passado = agendamentoCancelado(1L, servico, hoje.minusDays(3));
        AgendamentoEntity futuro = agendamentoCancelado(2L, servico, hoje.plusDays(11));
        base(servico, List.of(), List.of(passado, futuro));

        Map<String, Object> dados = analyzer.coletarDados(1L, 30);

        Map<String, Object> financeiro = (Map<String, Object>) dados.get("financeiro");
        assertEquals(1L, ((Number) financeiro.get("cancelamentos")).longValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void agendamentosPeriodoContaSomenteJanelaRelevante() {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Cuiaba"));
        ServicoEntity servico = ServicoEntity.builder().id(10L).nome("Corte")
                .valor(new BigDecimal("100")).status(StatusCadastro.ATIVO).build();
        AgendamentoEntity recente = agendamento(1L, null, servico, hoje.minusDays(3));
        AgendamentoEntity antigo = agendamento(2L, null, servico, hoje.minusDays(100));
        base(servico, List.of(), List.of(recente, antigo));

        Map<String, Object> dados = analyzer.coletarDados(1L, 30);

        Map<String, Object> resumo = (Map<String, Object>) dados.get("resumo");
        assertEquals(2L, ((Number) resumo.get("agendamentos_total")).longValue());
        assertEquals(1L, ((Number) resumo.get("agendamentos_periodo")).longValue());
    }

    private void base(ServicoEntity servico, List<PagamentoEntity> pagamentos, List<AgendamentoEntity> agendamentos) {
        base(servico, pagamentos, agendamentos, List.of());
    }

    private void base(ServicoEntity servico, List<PagamentoEntity> pagamentos,
                      List<AgendamentoEntity> agendamentos, List<ClienteEntity> clientes) {
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).nomeFantasia("Empresa").build();
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
        when(servicoRepository.findByEmpresaId(1L)).thenReturn(List.of(servico));
        when(profissionalRepository.findByEmpresaId(1L)).thenReturn(List.of());
        when(clienteRepository.findByEmpresaIdAndStatusNot(eq(1L), any())).thenReturn(clientes);
        when(agendamentoRepository.findByEmpresaIdOperacional(eq(1L), any())).thenReturn(agendamentos);
        when(pagamentoRepository.findByEmpresaId(1L)).thenReturn(pagamentos);
    }

    private ClienteEntity cliente(Long id, String nome) {
        return ClienteEntity.builder().id(id).nome(nome).status(StatusCadastro.ATIVO).build();
    }

    private AgendamentoEntity agendamento(Long id, ClienteEntity cliente, ServicoEntity servico, LocalDate data) {
        return AgendamentoEntity.builder().id(id).cliente(cliente).servico(servico)
                .data(data).horaInicio(LocalTime.of(9, 0)).horaFim(LocalTime.of(10, 0))
                .status(StatusAgendamento.FINALIZADO).build();
    }

    private AgendamentoEntity agendamentoCancelado(Long id, ServicoEntity servico, LocalDate data) {
        return AgendamentoEntity.builder().id(id).servico(servico)
                .data(data).horaInicio(LocalTime.of(9, 0)).horaFim(LocalTime.of(10, 0))
                .status(StatusAgendamento.CANCELADO).build();
    }

    private PagamentoEntity pagamento(Long id, AgendamentoEntity agendamento, BigDecimal valor,
                                      StatusPagamento status, LocalDateTime dataPagamento) {
        return PagamentoEntity.builder().id(id).agendamento(agendamento).valor(valor)
                .status(status).dataPagamento(dataPagamento).build();
    }
}

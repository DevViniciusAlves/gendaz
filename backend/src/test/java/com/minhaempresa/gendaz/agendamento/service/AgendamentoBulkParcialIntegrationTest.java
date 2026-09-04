package com.minhaempresa.gendaz.agendamento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AcaoEmMassaAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.financeiro.caixadespesas.enums.TipoCaixaDespesasLog;
import com.minhaempresa.gendaz.financeiro.caixadespesas.repository.CaixaDespesasLogRepository;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity;
import com.minhaempresa.gendaz.profissional.enums.DiaSemana;
import com.minhaempresa.gendaz.profissional.repository.ProfissionalRepository;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import com.minhaempresa.gendaz.servico.repository.ServicoRepository;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prova real da semantica oficial do bulk (falha por item / sucesso parcial)
 * com Spring, proxies e transacoes de verdade, sem {@code @Transactional} nos
 * metodos de teste — cada item commita/rollbacka de forma independente e as
 * assercoes releem o BANCO (nao objetos em memoria).
 *
 * <p>Cada item roda em sua propria transacao porque
 * {@code AgendamentoBulkService.executar} NAO e transacional e delega a
 * metodos publicos transacionais do {@code AgendamentoService}. Uma
 * {@code BusinessException} no item do meio nao marca os demais como
 * rollback-only: nao ha transacao global para corromper, logo nao ha
 * {@code UnexpectedRollbackException} nem rollback total silencioso.
 */
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.test.context.TestPropertySource(
        properties = "spring.datasource.hikari.maximum-pool-size=8")
class AgendamentoBulkParcialIntegrationTest {

    @Autowired AgendamentoBulkService bulkService;
    @Autowired AgendamentoRepository agendamentoRepository;
    @Autowired PagamentoRepository pagamentoRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ServicoRepository servicoRepository;
    @Autowired ProfissionalRepository profissionalRepository;
    @Autowired CaixaDespesasLogRepository logRepository;

    @MockBean AssinaturaService assinaturaService;

    @BeforeEach
    void setup() {
        when(assinaturaService.isPlanoComRecursosAvancados(anyLong())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    // ---------- infra ----------

    private EmpresaEntity novaEmpresa() {
        EmpresaEntity empresa = empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia("Bulk " + System.nanoTime())
                .email("bulk" + System.nanoTime() + "@x.com")
                .status(StatusEmpresa.ATIVA)
                .caixaTotal(BigDecimal.ZERO)
                .despesasTotal(BigDecimal.ZERO)
                .build());
        when(assinaturaService.isPlanoComRecursosAvancados(empresa.getId())).thenReturn(true);
        return empresa;
    }

    private ClienteEntity novoCliente(EmpresaEntity empresa) {
        return clienteRepository.save(ClienteEntity.builder()
                .nome("Cli Bulk").telefone("65991" + String.format("%06d", (int) (Math.random() * 1000000)))
                .email("b" + System.nanoTime() + "@x.com")
                .empresa(empresa).status(StatusCadastro.ATIVO).build());
    }

    private ServicoEntity novoServico(EmpresaEntity empresa) {
        return servicoRepository.save(ServicoEntity.builder()
                .nome("Corte").duracaoMinutos(30).valor(new BigDecimal("200.00"))
                .status(StatusCadastro.ATIVO).empresa(empresa).build());
    }

    private ProfissionalEntity novoProfissional(EmpresaEntity empresa) {
        return profissionalRepository.save(ProfissionalEntity.builder()
                .nome("Prof").status(StatusCadastro.ATIVO)
                .diasTrabalho(EnumSet.allOf(DiaSemana.class))
                .empresa(empresa).build());
    }

    private LocalDate proximoDiaUtil() {
        LocalDate data = LocalDate.now().plusDays(1);
        while (data.getDayOfWeek() == DayOfWeek.SATURDAY || data.getDayOfWeek() == DayOfWeek.SUNDAY) {
            data = data.plusDays(1);
        }
        return data;
    }

    private AgendamentoEntity novoAgendamento(EmpresaEntity empresa, ClienteEntity cliente,
            ServicoEntity servico, ProfissionalEntity profissional, StatusAgendamento status, LocalTime hora) {
        return agendamentoRepository.save(AgendamentoEntity.builder()
                .cliente(cliente).servico(servico).profissional(profissional).empresa(empresa)
                .data(proximoDiaUtil()).horaInicio(hora).horaFim(hora.plusMinutes(30))
                .status(status).build());
    }

    private PagamentoEntity novoPagamento(EmpresaEntity empresa, ClienteEntity cliente,
            AgendamentoEntity agendamento, StatusPagamento status) {
        return pagamentoRepository.save(PagamentoEntity.builder()
                .cliente(cliente).empresa(empresa).agendamento(agendamento)
                .valor(new BigDecimal("200.00")).metodoPagamento(MetodoPagamento.OUTRO)
                .status(status).build());
    }

    private StatusAgendamento statusDe(Long agendamentoId) {
        return agendamentoRepository.findById(agendamentoId).orElseThrow().getStatus();
    }

    private StatusPagamento pagamentoDe(Long pagamentoId) {
        return pagamentoRepository.findById(pagamentoId).orElseThrow().getStatus();
    }

    private long contarAprovados(Long empresaId) {
        return logRepository.findAll().stream()
                .filter(l -> l.getTipo() == TipoCaixaDespesasLog.PAGAMENTO_APROVADO
                        && l.getBusiness() != null && empresaId.equals(l.getBusiness().getId()))
                .count();
    }

    private BigDecimal caixaDe(Long empresaId) {
        return empresaRepository.findById(empresaId).orElseThrow().getCaixaTotal();
    }

    // ---------- TESTE 1: EXCLUIR parcial (valido / invalido / valido) ----------

    @Test
    void bulkExcluirComFalhaNoMeioPersisteItensValidos() {
        EmpresaEntity empresa = novaEmpresa();
        Long empresaId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);

        AgendamentoEntity a = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.PENDENTE, LocalTime.of(9, 0));
        PagamentoEntity pagA = novoPagamento(empresa, cliente, a, StatusPagamento.PENDENTE);
        AgendamentoEntity b = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.PAUSADO, LocalTime.of(10, 0));
        PagamentoEntity pagB = novoPagamento(empresa, cliente, b, StatusPagamento.PAGO);
        AgendamentoEntity c = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.CONFIRMADO, LocalTime.of(11, 0));
        PagamentoEntity pagC = novoPagamento(empresa, cliente, c, StatusPagamento.PENDENTE);

        CompanyContext.setCompanyId(empresaId);
        var resposta = bulkService.executar(
                new AcaoEmMassaAgendamentoRequest(List.of(a.getId(), b.getId(), c.getId()), "EXCLUIR", empresaId));
        CompanyContext.clear();

        assertEquals(2, resposta.totalProcessado());
        assertEquals(1, resposta.falhas().size());

        // Estado REAL persistido no banco (releitura, nao memoria):
        assertEquals(StatusAgendamento.CANCELADO, statusDe(a.getId()));
        assertEquals(StatusAgendamento.PAUSADO, statusDe(b.getId()));
        assertEquals(StatusAgendamento.CANCELADO, statusDe(c.getId()));
        // Pagamento PAGO do item invalido permanece intacto (sem estorno implicito).
        assertEquals(StatusPagamento.PAGO, pagamentoDe(pagB.getId()));
        assertEquals(StatusPagamento.CANCELADO, pagamentoDe(pagA.getId()));
        assertEquals(StatusPagamento.CANCELADO, pagamentoDe(pagC.getId()));
    }

    // ---------- TESTE 2: erro no meio nao desfaz o anterior ----------

    @Test
    void bulkErroNoSegundoItemNaoDesfazCommitDoPrimeiro() {
        EmpresaEntity empresa = novaEmpresa();
        Long empresaId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);

        AgendamentoEntity primeiro = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.PENDENTE, LocalTime.of(9, 0));
        novoPagamento(empresa, cliente, primeiro, StatusPagamento.PENDENTE);
        AgendamentoEntity invalido = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.PAUSADO, LocalTime.of(10, 0));
        novoPagamento(empresa, cliente, invalido, StatusPagamento.PAGO);

        CompanyContext.setCompanyId(empresaId);
        var resposta = bulkService.executar(
                new AcaoEmMassaAgendamentoRequest(List.of(primeiro.getId(), invalido.getId()), "EXCLUIR", empresaId));
        CompanyContext.clear();

        assertEquals(1, resposta.totalProcessado());
        assertEquals(1, resposta.falhas().size());
        assertEquals(StatusAgendamento.CANCELADO, statusDe(primeiro.getId()));
        assertEquals(StatusAgendamento.PAUSADO, statusDe(invalido.getId()));
    }

    // ---------- TESTE 3: falha no primeiro nao impede o segundo ----------

    @Test
    void bulkFalhaNoPrimeiroItemNaoImpedeSegundo() {
        EmpresaEntity empresa = novaEmpresa();
        Long empresaId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);

        AgendamentoEntity invalido = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.PAUSADO, LocalTime.of(9, 0));
        novoPagamento(empresa, cliente, invalido, StatusPagamento.PAGO);
        AgendamentoEntity valido = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.CONFIRMADO, LocalTime.of(10, 0));
        novoPagamento(empresa, cliente, valido, StatusPagamento.PENDENTE);

        CompanyContext.setCompanyId(empresaId);
        var resposta = bulkService.executar(
                new AcaoEmMassaAgendamentoRequest(List.of(invalido.getId(), valido.getId()), "EXCLUIR", empresaId));
        CompanyContext.clear();

        assertEquals(1, resposta.totalProcessado());
        assertEquals(1, resposta.falhas().size());
        assertEquals(StatusAgendamento.PAUSADO, statusDe(invalido.getId()));
        assertEquals(StatusAgendamento.CANCELADO, statusDe(valido.getId()));
    }

    // ---------- TESTE 4: FINALIZAR parcial com pagamento e Caixa ----------

    @Test
    void bulkFinalizarParcialComCaixaCorretoParaValidos() {
        EmpresaEntity empresa = novaEmpresa();
        Long empresaId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);

        AgendamentoEntity a = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.EM_ATENDIMENTO, LocalTime.of(9, 0));
        PagamentoEntity pagA = novoPagamento(empresa, cliente, a, StatusPagamento.PENDENTE);
        AgendamentoEntity b = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.CANCELADO, LocalTime.of(10, 0));
        PagamentoEntity pagB = novoPagamento(empresa, cliente, b, StatusPagamento.CANCELADO);
        AgendamentoEntity c = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.PAUSADO, LocalTime.of(11, 0));
        PagamentoEntity pagC = novoPagamento(empresa, cliente, c, StatusPagamento.PENDENTE);

        CompanyContext.setCompanyId(empresaId);
        var resposta = bulkService.executar(new AcaoEmMassaAgendamentoRequest(
                List.of(a.getId(), b.getId(), c.getId()), "FINALIZAR", empresaId,
                true, MetodoPagamento.DINHEIRO, null));
        CompanyContext.clear();

        assertEquals(2, resposta.totalProcessado());
        assertEquals(1, resposta.falhas().size());

        // Itens validos finalizaram com pagamento e Caixa; o invalido intacto.
        assertEquals(StatusAgendamento.FINALIZADO, statusDe(a.getId()));
        assertEquals(StatusAgendamento.FINALIZADO, statusDe(c.getId()));
        assertEquals(StatusAgendamento.CANCELADO, statusDe(b.getId()));
        assertEquals(StatusPagamento.PAGO, pagamentoDe(pagA.getId()));
        assertEquals(StatusPagamento.PAGO, pagamentoDe(pagC.getId()));
        assertEquals(StatusPagamento.CANCELADO, pagamentoDe(pagB.getId()));
        assertEquals(0, new BigDecimal("400.00").compareTo(caixaDe(empresaId)));
        assertEquals(2, contarAprovados(empresaId));

        // Itens validos continuam bloqueados contra re-finalizacao (idempotencia real).
        CompanyContext.setCompanyId(empresaId);
        var repeticao = bulkService.executar(new AcaoEmMassaAgendamentoRequest(
                List.of(a.getId(), c.getId()), "FINALIZAR", empresaId,
                true, MetodoPagamento.DINHEIRO, null));
        CompanyContext.clear();
        assertEquals(0, repeticao.totalProcessado());
        assertEquals(2, repeticao.falhas().size());
        assertEquals(0, new BigDecimal("400.00").compareTo(caixaDe(empresaId)));
        assertEquals(2, contarAprovados(empresaId));
    }

    // ---------- TESTE 5: item inexistente vira falha, nao rollback ----------

    @Test
    void bulkItemInexistenteViraFalhaSemAfetarVizinhos() {
        EmpresaEntity empresa = novaEmpresa();
        Long empresaId = empresa.getId();
        ClienteEntity cliente = novoCliente(empresa);
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);

        AgendamentoEntity valido = novoAgendamento(empresa, cliente, servico, profissional,
                StatusAgendamento.PENDENTE, LocalTime.of(9, 0));
        novoPagamento(empresa, cliente, valido, StatusPagamento.PENDENTE);

        CompanyContext.setCompanyId(empresaId);
        var resposta = bulkService.executar(
                new AcaoEmMassaAgendamentoRequest(List.of(999999L, valido.getId()), "CANCELAR", empresaId));
        CompanyContext.clear();

        assertEquals(1, resposta.totalProcessado());
        assertEquals(1, resposta.falhas().size());
        assertTrue(resposta.falhas().stream().anyMatch(f -> f.id().equals(999999L)));
        assertEquals(StatusAgendamento.CANCELADO, statusDe(valido.getId()));
    }
}

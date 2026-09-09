package com.minhaempresa.gendaz.whatsapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity;
import com.minhaempresa.gendaz.profissional.enums.DiaSemana;
import com.minhaempresa.gendaz.profissional.repository.ProfissionalRepository;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import com.minhaempresa.gendaz.servico.repository.ServicoRepository;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppNotificacaoEntity;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppStatusNotificacao;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppTipoNotificacao;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppNotificacaoRepository;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppNotificacaoService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WhatsAppNotificacaoIdempotenciaTest {

    private static final AtomicLong SEQUENCIA = new AtomicLong();

    @Autowired
    private WhatsAppNotificacaoService notificacaoService;
    @Autowired
    private WhatsAppNotificacaoRepository notificacaoRepository;
    @Autowired
    private EmpresaRepository empresaRepository;
    @Autowired
    private ClienteRepository clienteRepository;
    @Autowired
    private ServicoRepository servicoRepository;
    @Autowired
    private ProfissionalRepository profissionalRepository;
    @Autowired
    private AgendamentoRepository agendamentoRepository;

    private EmpresaEntity novaEmpresa(String prefixo) {
        long seq = SEQUENCIA.incrementAndGet();
        return empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia(prefixo + " " + seq)
                .email(prefixo + "-" + seq + "-" + System.nanoTime() + "@teste.com")
                .status(StatusEmpresa.ATIVA)
                .build());
    }

    private String chaveUnica(String prefixo) {
        return prefixo + "-" + SEQUENCIA.incrementAndGet() + "-" + System.nanoTime();
    }

    @Test
    void primeiraCriacaoComChaveNovaTemSucesso() {
        EmpresaEntity empresa = novaEmpresa("wpp-idem");
        ClienteEntity cliente = clienteRepository.save(ClienteEntity.builder()
                .nome("Cliente Idem").telefone("65990000001").email("idem@x.com")
                .empresa(empresa).status(StatusCadastro.ATIVO).build());

        WhatsAppNotificacaoEntity criada = notificacaoService.criarIdempotente(
                empresa.getId(),
                WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO,
                chaveUnica("lembrete"),
                LocalDateTime.now().plusHours(2),
                cliente.getId(),
                null);

        assertNotNull(criada.getId());
        assertEquals(WhatsAppStatusNotificacao.PENDENTE, criada.getStatus());
        assertNotNull(criada.getDataCriacao());
    }

    @Test
    void segundaCriacaoComMesmaChaveNaoCriaDuplicata() {
        EmpresaEntity empresa = novaEmpresa("wpp-dupla");
        String chave = chaveUnica("resgate");

        WhatsAppNotificacaoEntity primeira = notificacaoService.criarIdempotente(
                empresa.getId(), WhatsAppTipoNotificacao.CRM_RESGATE, chave, null, null, null);
        WhatsAppNotificacaoEntity segunda = notificacaoService.criarIdempotente(
                empresa.getId(), WhatsAppTipoNotificacao.CRM_RESGATE, chave, null, null, null);

        assertEquals(primeira.getId(), segunda.getId());
    }

    @Test
    void mesmaChaveEmEmpresaDiferenteEPermitida() {
        EmpresaEntity empresaA = novaEmpresa("wpp-multi-a");
        EmpresaEntity empresaB = novaEmpresa("wpp-multi-b");
        String chave = chaveUnica("compartilhada");

        WhatsAppNotificacaoEntity na = notificacaoService.criarIdempotente(
                empresaA.getId(), WhatsAppTipoNotificacao.CRM_RECONEXAO, chave, null, null, null);
        WhatsAppNotificacaoEntity nb = notificacaoService.criarIdempotente(
                empresaB.getId(), WhatsAppTipoNotificacao.CRM_RECONEXAO, chave, null, null, null);

        assertTrue(!na.getId().equals(nb.getId()));
    }

    @Test
    void constraintDoBancoImpedeDuplicataMesmoSemPassarPeloServico() {
        EmpresaEntity empresa = novaEmpresa("wpp-constraint");
        String chave = chaveUnica("direta");

        notificacaoService.criar(
                empresa.getId(), WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO, chave, null, null, null);

        WhatsAppNotificacaoEntity duplicada = WhatsAppNotificacaoEntity.builder()
                .empresa(empresa)
                .tipo(WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO)
                .status(WhatsAppStatusNotificacao.PENDENTE)
                .idempotencyKey(chave)
                .build();
        try {
            notificacaoRepository.saveAndFlush(duplicada);
            throw new AssertionError("constraint UK (empresa, chave) deveria impedir a duplicata");
        } catch (DataIntegrityViolationException esperada) {
            // Proteção confirmada no banco, não apenas em memória.
        }
    }

    @Test
    void criacoesConcorrentesComMesmaChaveResultamEmUmUnicoRegistro() throws Exception {
        EmpresaEntity empresa = novaEmpresa("wpp-corrida");
        String chave = chaveUnica("corrida");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch largada = new CountDownLatch(1);
            List<WhatsAppNotificacaoEntity> resultados = new CopyOnWriteArrayList<>();
            List<Throwable> erros = new CopyOnWriteArrayList<>();

            Future<?> primeira = executor.submit(() -> {
                try {
                    largada.await();
                    resultados.add(notificacaoService.criarIdempotente(
                            empresa.getId(), WhatsAppTipoNotificacao.CRM_RESGATE, chave, null, null, null));
                } catch (Throwable t) {
                    erros.add(t);
                }
            });
            Future<?> segunda = executor.submit(() -> {
                try {
                    largada.await();
                    resultados.add(notificacaoService.criarIdempotente(
                            empresa.getId(), WhatsAppTipoNotificacao.CRM_RESGATE, chave, null, null, null));
                } catch (Throwable t) {
                    erros.add(t);
                }
            });
            largada.countDown();
            primeira.get(60, TimeUnit.SECONDS);
            segunda.get(60, TimeUnit.SECONDS);

            assertTrue(erros.isEmpty(), "criacao idempotente concorrente nao pode lancar excecao");
            assertEquals(2, resultados.size());
            assertEquals(resultados.get(0).getId(), resultados.get(1).getId());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void buscaPorIdNaoVazaNotificacaoDeOutraEmpresa() {
        EmpresaEntity empresaA = novaEmpresa("wpp-tenant-a");
        EmpresaEntity empresaB = novaEmpresa("wpp-tenant-b");

        WhatsAppNotificacaoEntity criada = notificacaoService.criarIdempotente(
                empresaA.getId(), WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO,
                chaveUnica("tenant"), null, null, null);

        assertTrue(notificacaoService.buscarPorId(empresaA.getId(), criada.getId()).isPresent());
        assertTrue(notificacaoService.buscarPorId(empresaB.getId(), criada.getId()).isEmpty());
        assertTrue(notificacaoService.buscarPorId(empresaA.getId(), Long.MAX_VALUE).isEmpty());
    }

    @Test
    void empresaComClienteProprioECriadaComSucesso() {
        EmpresaEntity empresa = novaEmpresa("wpp-cproprio");
        ClienteEntity cliente = novoCliente(empresa);

        WhatsAppNotificacaoEntity criada = notificacaoService.criarIdempotente(
                empresa.getId(), WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO,
                chaveUnica("cproprio"), null, cliente.getId(), null);

        assertNotNull(criada.getId());
    }

    @Test
    void empresaComClienteDeOutraEmpresaERejeitada() {
        EmpresaEntity empresaA = novaEmpresa("wpp-couter-a");
        EmpresaEntity empresaB = novaEmpresa("wpp-couter-b");
        ClienteEntity clienteB = novoCliente(empresaB);

        assertThrows(ResourceNotFoundException.class, () -> notificacaoService.criarIdempotente(
                empresaA.getId(), WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO,
                chaveUnica("couter"), null, clienteB.getId(), null));
    }

    @Test
    void empresaComClienteInexistenteERejeitadaIgualOutroTenant() {
        EmpresaEntity empresa = novaEmpresa("wpp-cmissing");

        assertThrows(ResourceNotFoundException.class, () -> notificacaoService.criarIdempotente(
                empresa.getId(), WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO,
                chaveUnica("cmissing"), null, Long.MAX_VALUE, null));
    }

    @Test
    void empresaComAgendamentoProprioECriadaComSucesso() {
        EmpresaEntity empresa = novaEmpresa("wpp-agproprio");
        AgendamentoEntity agendamento = novoAgendamento(empresa);

        WhatsAppNotificacaoEntity criada = notificacaoService.criarIdempotente(
                empresa.getId(), WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO,
                chaveUnica("agproprio"), null, null, agendamento.getId());

        assertNotNull(criada.getId());
    }

    @Test
    void empresaComAgendamentoDeOutraEmpresaERejeitada() {
        EmpresaEntity empresaA = novaEmpresa("wpp-agouter-a");
        EmpresaEntity empresaB = novaEmpresa("wpp-agouter-b");
        AgendamentoEntity agendamentoB = novoAgendamento(empresaB);

        assertThrows(ResourceNotFoundException.class, () -> notificacaoService.criarIdempotente(
                empresaA.getId(), WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO,
                chaveUnica("agouter"), null, null, agendamentoB.getId()));
    }

    private ClienteEntity novoCliente(EmpresaEntity empresa) {
        long seq = SEQUENCIA.incrementAndGet();
        return clienteRepository.save(ClienteEntity.builder()
                .nome("Cli Tenant " + seq)
                .telefone("65991" + String.format("%06d", (int) (seq % 1000000)))
                .email("tenant" + seq + "-" + System.nanoTime() + "@x.com")
                .empresa(empresa).status(StatusCadastro.ATIVO).build());
    }

    private AgendamentoEntity novoAgendamento(EmpresaEntity empresa) {
        ClienteEntity cliente = novoCliente(empresa);
        ServicoEntity servico = servicoRepository.save(ServicoEntity.builder()
                .nome("Corte").duracaoMinutos(30).valor(new BigDecimal("200.00"))
                .status(StatusCadastro.ATIVO).empresa(empresa).build());
        ProfissionalEntity profissional = profissionalRepository.save(ProfissionalEntity.builder()
                .nome("Prof").status(StatusCadastro.ATIVO)
                .diasTrabalho(EnumSet.allOf(DiaSemana.class))
                .empresa(empresa).build());
        LocalTime hora = LocalTime.of(10, 0);
        return agendamentoRepository.save(AgendamentoEntity.builder()
                .cliente(cliente).servico(servico).profissional(profissional).empresa(empresa)
                .data(LocalDate.now().plusDays(1)).horaInicio(hora).horaFim(hora.plusMinutes(30))
                .status(StatusAgendamento.PENDENTE).build());
    }
}

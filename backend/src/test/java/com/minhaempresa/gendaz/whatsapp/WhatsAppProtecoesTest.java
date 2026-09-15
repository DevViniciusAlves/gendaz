package com.minhaempresa.gendaz.whatsapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import com.minhaempresa.gendaz.assinatura.repository.AssinaturaRepository;
import com.minhaempresa.gendaz.cliente.dto.ClienteDtos.SalvarClienteRequest;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.cliente.service.ClienteService;
import com.minhaempresa.gendaz.crm.dto.CrmDtos.EnviarMensagemRequest;
import com.minhaempresa.gendaz.crm.service.CrmService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.plano.entity.PlanoEntity;
import com.minhaempresa.gendaz.plano.repository.PlanoRepository;
import com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity;
import com.minhaempresa.gendaz.profissional.enums.DiaSemana;
import com.minhaempresa.gendaz.profissional.repository.ProfissionalRepository;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import com.minhaempresa.gendaz.servico.repository.ServicoRepository;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppConfiguracaoEntity;
import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppNotificacaoEntity;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppCategoriaCota;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppStatusNotificacao;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppTipoNotificacao;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppConfiguracaoRepository;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppNotificacaoRepository;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppUsoCicloRepository;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppConfiguracaoService;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppEnvioWorker;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppFilaService;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppLembreteAgendamentoService;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppNotificacaoService;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppQuotaService;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppReserva;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class WhatsAppProtecoesTest {

    private static final AtomicLong SEQUENCIA = new AtomicLong();
    private static final String ZONA_SP = "America/Sao_Paulo";

    @MockBean
    private WhatsAppProvider provider;

    @Autowired
    private WhatsAppEnvioWorker worker;
    @Autowired
    private WhatsAppFilaService filaService;
    @Autowired
    private WhatsAppLembreteAgendamentoService lembreteService;
    @Autowired
    private WhatsAppQuotaService quotaService;
    @Autowired
    private WhatsAppNotificacaoService notificacaoService;
    @Autowired
    private CrmService crmService;
    @Autowired
    private ClienteService clienteService;
    @Autowired
    private WhatsAppNotificacaoRepository notificacaoRepository;
    @Autowired
    private WhatsAppUsoCicloRepository usoRepository;
    @Autowired
    private WhatsAppConfiguracaoRepository configuracaoRepository;
    @Autowired
    private EmpresaRepository empresaRepository;
    @Autowired
    private PlanoRepository planoRepository;
    @Autowired
    private AssinaturaRepository assinaturaRepository;
    @Autowired
    private ClienteRepository clienteRepository;
    @Autowired
    private ServicoRepository servicoRepository;
    @Autowired
    private ProfissionalRepository profissionalRepository;
    @Autowired
    private AgendamentoRepository agendamentoRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void configurarSessaoWhatsappConectada() {
        when(provider.disponivel()).thenReturn(true);
        when(provider.consultarStatus(any()))
                .thenAnswer(invocation -> WhatsAppResult.success(statusSessao(
                        (String) invocation.getArgument(0), "CONNECTED")));
    }

    @AfterEach
    void limparContexto() {
        CompanyContext.clear();
    }

    private WhatsAppSessionStatus statusSessao(String companyId, String estado) {
        WhatsAppSessionStatus status = new WhatsAppSessionStatus();
        status.setCompanyId(companyId);
        status.setState(estado);
        status.setHasQr(false);
        return status;
    }

    private EmpresaEntity novaEmpresa(String prefixo) {
        long seq = SEQUENCIA.incrementAndGet();
        return empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia(prefixo + " " + seq)
                .email(prefixo + "-" + seq + "-" + System.nanoTime() + "@teste.com")
                .status(StatusEmpresa.ATIVA)
                .timezone(ZONA_SP)
                .build());
    }

    private void comAssinatura(EmpresaEntity empresa, String planoNome) {
        PlanoEntity plano = planoRepository.findByNome(planoNome).orElseThrow();
        LocalDate hoje = LocalDate.now();
        assinaturaRepository.save(AssinaturaEntity.builder()
                .empresa(empresa).plano(plano).status(StatusAssinatura.ATIVA)
                .dataInicio(hoje.minusDays(5)).dataFim(hoje.plusDays(25)).build());
    }

    private void ativarLembretes(EmpresaEntity empresa) {
        configuracaoRepository.save(WhatsAppConfiguracaoEntity.builder()
                .empresa(empresa).lembretesAtivos(true).build());
    }

    private ClienteEntity novoCliente(EmpresaEntity empresa, String telefone) {
        long seq = SEQUENCIA.incrementAndGet();
        return clienteRepository.save(ClienteEntity.builder()
                .nome("Cli Prot")
                .telefone(telefone)
                .email("prot" + seq + "-" + System.nanoTime() + "@x.com")
                .empresa(empresa).status(StatusCadastro.ATIVO).build());
    }

    private String telefoneCanonicoNovo() {
        return "55659" + String.format("%08d", (int) (Math.random() * 100000000));
    }

    private AgendamentoEntity novoAgendamento(EmpresaEntity empresa, ClienteEntity cliente,
            LocalDate data, LocalTime hora) {
        ServicoEntity servico = servicoRepository.save(ServicoEntity.builder()
                .nome("Corte").duracaoMinutos(30).valor(new BigDecimal("100.00"))
                .status(StatusCadastro.ATIVO).empresa(empresa).build());
        ProfissionalEntity profissional = profissionalRepository.save(ProfissionalEntity.builder()
                .nome("Prof").status(StatusCadastro.ATIVO)
                .diasTrabalho(EnumSet.allOf(DiaSemana.class))
                .empresa(empresa).build());
        return agendamentoRepository.save(AgendamentoEntity.builder()
                .cliente(cliente).servico(servico).profissional(profissional).empresa(empresa)
                .data(data).horaInicio(hora).horaFim(hora.plusMinutes(30))
                .status(StatusAgendamento.PENDENTE).build());
    }

    private ZonedDateTime atendimentoFuturo(int dias, int hora) {
        return ZonedDateTime.now(ZoneId.of(ZONA_SP)).plusDays(dias)
                .withHour(hora).withMinute(0).withSecond(0).withNano(0);
    }

    private WhatsAppNotificacaoEntity recarregar(Long id) {
        return notificacaoRepository.findById(id).orElseThrow();
    }

    private void definirOptOut(EmpresaEntity empresa, ClienteEntity cliente, boolean receber) {
        ClienteEntity atual = clienteRepository.findById(cliente.getId()).orElseThrow();
        atual.setReceberWhatsapp(receber);
        clienteRepository.save(atual);
    }

    // ---------- opt-out: default e bloqueio no enqueue ----------

    @Test
    void clienteNovoPermiteWhatsAppPorDefault() {
        EmpresaEntity empresa = novaEmpresa("wpp-popt");
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        assertTrue(cliente.isReceberWhatsapp());

        CompanyContext.setCompanyId(empresa.getId());
        try {
            var response = clienteService.salvar(new SalvarClienteRequest(
                    "Maria Silva", telefoneCanonicoNovo(), "opt" + System.nanoTime() + "@x.com",
                    null, empresa.getId(), null));
            assertTrue(response.receberWhatsapp());

            var responseNegado = clienteService.salvar(new SalvarClienteRequest(
                    "Maria Silva", telefoneCanonicoNovo(), "opt2" + System.nanoTime() + "@x.com",
                    null, empresa.getId(), false));
            assertFalse(responseNegado.receberWhatsapp());
        } finally {
            CompanyContext.clear();
        }
    }

    @Test
    void optOutBloqueiaReminderResgateReconexao() {
        EmpresaEntity empresa = novaEmpresa("wpp-poff");
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        definirOptOut(empresa, cliente, false);
        ZonedDateTime at = atendimentoFuturo(3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente, at.toLocalDate(), at.toLocalTime());

        lembreteService.sincronizar(empresa.getId(), ag.getId());
        assertTrue(notificacaoRepository.findByEmpresaIdAndAgendamentoId(empresa.getId(), ag.getId()).isEmpty());

        Map<String, Object> resgate = crmService.enviarMensagem(empresa.getId(), cliente.getId(),
                new EnviarMensagemRequest("resgate", "whatsapp", null, "opt-req-1"));
        assertEquals(false, resgate.get("success"));
        assertEquals("WHATSAPP_OPT_OUT", resgate.get("status"));

        Map<String, Object> reconexao = crmService.enviarMensagem(empresa.getId(), cliente.getId(),
                new EnviarMensagemRequest("reconexao", "whatsapp", null, "opt-req-2"));
        assertEquals(false, reconexao.get("success"));
        assertEquals("WHATSAPP_OPT_OUT", reconexao.get("status"));
    }

    @Test
    void backfillSomenteFuturosEIdempotente() {
        EmpresaEntity empresa = novaEmpresa("wpp-pback");
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        ZonedDateTime futuro1 = atendimentoFuturo(3, 15);
        ZonedDateTime futuro2 = atendimentoFuturo(4, 10);
        AgendamentoEntity ag1 = novoAgendamento(empresa, cliente, futuro1.toLocalDate(), futuro1.toLocalTime());
        AgendamentoEntity ag2 = novoAgendamento(empresa, cliente, futuro2.toLocalDate(), futuro2.toLocalTime());
        ZonedDateTime passado = ZonedDateTime.now(ZoneId.of(ZONA_SP)).minusDays(2)
                .withHour(10).withMinute(0).withSecond(0).withNano(0);
        AgendamentoEntity agPassado = novoAgendamento(empresa, cliente, passado.toLocalDate(), passado.toLocalTime());

        lembreteService.reconsiliarTodos(empresa.getId());

        assertEquals(1, notificacaoRepository.findByEmpresaIdAndAgendamentoId(empresa.getId(), ag1.getId()).size());
        assertEquals(1, notificacaoRepository.findByEmpresaIdAndAgendamentoId(empresa.getId(), ag2.getId()).size());
        assertTrue(notificacaoRepository.findByEmpresaIdAndAgendamentoId(empresa.getId(), agPassado.getId()).isEmpty());

        lembreteService.reconsiliarTodos(empresa.getId());

        assertEquals(1, notificacaoRepository.findByEmpresaIdAndAgendamentoId(empresa.getId(), ag1.getId()).size());
        assertEquals(1, notificacaoRepository.findByEmpresaIdAndAgendamentoId(empresa.getId(), ag2.getId()).size());
        assertTrue(notificacaoRepository.findByEmpresaIdAndAgendamentoId(empresa.getId(), agPassado.getId()).isEmpty());
    }

    @Test
    void backfillUsaTimezoneDaEmpresaNaViradaDeData() {
        EmpresaEntity empresa = novaEmpresa("wpp-ptz");
        empresa.setTimezone("America/Cuiaba");
        empresaRepository.save(empresa);

        // 15/09 01:00Z ainda e 14/09 21:00 em Cuiaba: referencia e 14/09.
        LocalDate referencia = lembreteService.dataReferenciaBackfill(
                empresa.getId(), LocalDateTime.of(2026, 9, 15, 1, 0));
        assertEquals(LocalDate.of(2026, 9, 14), referencia);

        // Empresa em UTC+: 15/09 01:00Z ja e 15/09 na empresa.
        empresa.setTimezone("Pacific/Kiritimati");
        empresaRepository.save(empresa);
        assertEquals(LocalDate.of(2026, 9, 15), lembreteService.dataReferenciaBackfill(
                empresa.getId(), LocalDateTime.of(2026, 9, 15, 1, 0)));

        // Empresa desconhecida: cai no default da aplicacao, sem quebrar.
        assertNotNull(lembreteService.dataReferenciaBackfill(
                -999L, LocalDateTime.of(2026, 9, 15, 1, 0)));
    }

    @Test
    void templateAlteraSomentePendenciasSeguras() {
        EmpresaEntity empresa = novaEmpresa("wpp-ptpl");
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        ZonedDateTime futuro = atendimentoFuturo(3, 15);
        AgendamentoEntity agPendente = novoAgendamento(empresa, cliente, futuro.toLocalDate(), futuro.toLocalTime());
        ZonedDateTime futuro2 = atendimentoFuturo(4, 10);
        AgendamentoEntity agEnviando = novoAgendamento(empresa, cliente, futuro2.toLocalDate(), futuro2.toLocalTime());

        lembreteService.sincronizar(empresa.getId(), agPendente.getId());
        lembreteService.sincronizar(empresa.getId(), agEnviando.getId());

        WhatsAppNotificacaoEntity pendente = notificacaoRepository
                .findByEmpresaIdAndAgendamentoId(empresa.getId(), agPendente.getId()).get(0);
        WhatsAppNotificacaoEntity emEnvio = notificacaoRepository
                .findByEmpresaIdAndAgendamentoId(empresa.getId(), agEnviando.getId()).get(0);
        String mensagemOriginal = emEnvio.getMessageBody();

        // Simula worker com envio ja iniciado: nunca pode ser alterada.
        transactionTemplate.executeWithoutResult(tx -> {
            WhatsAppNotificacaoEntity atual = notificacaoRepository.findById(emEnvio.getId()).orElseThrow();
            atual.setStatus(WhatsAppStatusNotificacao.ENVIANDO);
            atual.setSendStartedAt(LocalDateTime.now());
            notificacaoRepository.save(atual);
        });

        String novoTemplate = "Novo: {cliente} na {empresa} em {data} as {hora}.";
        transactionTemplate.executeWithoutResult(tx -> {
            WhatsAppConfiguracaoEntity cfg = configuracaoRepository.findByEmpresaId(empresa.getId()).orElseThrow();
            cfg.setLembreteTemplate(novoTemplate);
            configuracaoRepository.save(cfg);
        });
        lembreteService.onTemplateAlterado(
                new WhatsAppConfiguracaoService.TemplateAlteradoEvent(empresa.getId(), novoTemplate));

        assertTrue(recarregar(pendente.getId()).getMessageBody().startsWith("Novo: "));
        assertEquals(mensagemOriginal, recarregar(emEnvio.getId()).getMessageBody());
    }

    @Test
    void flipParaOptOutViaServiceCancelaPendenciasELiberaUmaVez() {
        EmpresaEntity empresa = novaEmpresa("wpp-pflip");
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        ZonedDateTime at = atendimentoFuturo(3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente, at.toLocalDate(), at.toLocalTime());
        lembreteService.sincronizar(empresa.getId(), ag.getId());
        WhatsAppNotificacaoEntity reminder = notificacaoRepository
                .findByEmpresaIdAndAgendamentoId(empresa.getId(), ag.getId()).get(0);
        quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.LEMBRETE);
        WhatsAppNotificacaoEntity comReserva = recarregar(reminder.getId());
        comReserva.setQuotaReserved(true);
        comReserva.setQuotaCycleStart(LocalDate.now().minusDays(5));
        notificacaoRepository.save(comReserva);
        assertEquals(1, quotaService.consultarUso(empresa.getId()).lembretesReservados());

        CompanyContext.setCompanyId(empresa.getId());
        try {
            var response = clienteService.atualizar(cliente.getId(), new SalvarClienteRequest(
                    "Cli Prot", cliente.getTelefone(), cliente.getEmail(), null, empresa.getId(), false));
            assertFalse(response.receberWhatsapp());
        } finally {
            CompanyContext.clear();
        }

        WhatsAppNotificacaoEntity cancelado = recarregar(reminder.getId());
        assertEquals(WhatsAppStatusNotificacao.CANCELADO, cancelado.getStatus());
        assertEquals("WHATSAPP_OPT_OUT", cancelado.getLastError());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).lembretesReservados());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).lembretesEnviados());
    }

    @Test
    void optOutCancelaEnviandoSemSendELibera() {
        EmpresaEntity empresa = novaEmpresa("wpp-poenv");
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        ZonedDateTime at = atendimentoFuturo(3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente, at.toLocalDate(), at.toLocalTime());
        lembreteService.sincronizar(empresa.getId(), ag.getId());
        WhatsAppNotificacaoEntity reminder = notificacaoRepository
                .findByEmpresaIdAndAgendamentoId(empresa.getId(), ag.getId()).get(0);
        quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.LEMBRETE);
        WhatsAppNotificacaoEntity preso = recarregar(reminder.getId());
        preso.setStatus(WhatsAppStatusNotificacao.ENVIANDO);
        preso.setProcessingStartedAt(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(10));
        preso.setSendStartedAt(null);
        preso.setQuotaReserved(true);
        preso.setQuotaCycleStart(LocalDate.now().minusDays(5));
        notificacaoRepository.save(preso);

        flipParaOptOut(empresa, cliente);

        WhatsAppNotificacaoEntity cancelado = recarregar(reminder.getId());
        assertEquals(WhatsAppStatusNotificacao.CANCELADO, cancelado.getStatus());
        assertEquals("WHATSAPP_OPT_OUT", cancelado.getLastError());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).lembretesReservados());
    }

    @Test
    void optOutNaoInterfereEnviandoComSendIniciado() {
        EmpresaEntity empresa = novaEmpresa("wpp-postart");
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        ZonedDateTime at = atendimentoFuturo(3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente, at.toLocalDate(), at.toLocalTime());
        lembreteService.sincronizar(empresa.getId(), ag.getId());
        WhatsAppNotificacaoEntity reminder = notificacaoRepository
                .findByEmpresaIdAndAgendamentoId(empresa.getId(), ag.getId()).get(0);
        quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.LEMBRETE);
        WhatsAppNotificacaoEntity preso = recarregar(reminder.getId());
        preso.setStatus(WhatsAppStatusNotificacao.ENVIANDO);
        preso.setProcessingStartedAt(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(10));
        preso.setSendStartedAt(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(5));
        preso.setQuotaReserved(true);
        preso.setQuotaCycleStart(LocalDate.now().minusDays(5));
        notificacaoRepository.save(preso);

        flipParaOptOut(empresa, cliente);

        WhatsAppNotificacaoEntity mantido = recarregar(reminder.getId());
        assertEquals(WhatsAppStatusNotificacao.ENVIANDO, mantido.getStatus());
        assertTrue(mantido.isQuotaReserved());
        assertEquals(1, quotaService.consultarUso(empresa.getId()).lembretesReservados());

        // Finaliza para nao deixar resto stale para outros testes.
        worker.finalizar(preso.getId(), WhatsAppSendResult.sent("WAMID"));
        assertEquals(WhatsAppStatusNotificacao.ENVIADO, recarregar(reminder.getId()).getStatus());
    }

    private void flipParaOptOut(EmpresaEntity empresa, ClienteEntity cliente) {
        CompanyContext.setCompanyId(empresa.getId());
        try {
            var response = clienteService.atualizar(cliente.getId(), new SalvarClienteRequest(
                    "Cli Prot", cliente.getTelefone(), cliente.getEmail(), null, empresa.getId(), false));
            assertFalse(response.receberWhatsapp());
        } finally {
            CompanyContext.clear();
        }
    }

    @Test
    void preEnvioComOptOutCancelaSemProvider() {
        EmpresaEntity empresa = novaEmpresa("wpp-ppre");
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        ZonedDateTime at = atendimentoFuturo(3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente, at.toLocalDate(), at.toLocalTime());
        lembreteService.sincronizar(empresa.getId(), ag.getId());
        WhatsAppNotificacaoEntity reminder = notificacaoRepository
                .findByEmpresaIdAndAgendamentoId(empresa.getId(), ag.getId()).get(0);
        // Torna processavel e so entao desativa: pre-send deve barrar.
        WhatsAppNotificacaoEntity vencida = recarregar(reminder.getId());
        vencida.setScheduledAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(30));
        vencida.setNextAttemptAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(30));
        notificacaoRepository.save(vencida);
        definirOptOut(empresa, cliente, false);

        worker.processarLote(10);

        WhatsAppNotificacaoEntity finalizada = recarregar(reminder.getId());
        assertEquals(WhatsAppStatusNotificacao.CANCELADO, finalizada.getStatus());
        assertEquals("WHATSAPP_OPT_OUT", finalizada.getLastError());
        verify(provider, never()).enviarTexto(any(), any(), any(), any());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).lembretesReservados());
    }

    // ---------- upgrade / downgrade ----------

    private void trocarPlano(EmpresaEntity empresa, String planoNome) {
        PlanoEntity plano = planoRepository.findByNome(planoNome).orElseThrow();
        List<AssinaturaEntity> assinaturas =
                assinaturaRepository.findByEmpresaId(empresa.getId());
        assertTrue(!assinaturas.isEmpty());
        for (AssinaturaEntity assinatura : assinaturas) {
            assinatura.setPlano(plano);
            assinaturaRepository.save(assinatura);
        }
    }

    @Test
    void upgradeAplicaNovoLimiteSemZerarUso() {
        EmpresaEntity empresa = novaEmpresa("wpp-pup");
        comAssinatura(empresa, "PRO");
        for (int i = 0; i < 120; i++) {
            quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.LEMBRETE);
            quotaService.confirmarEnvio(empresa.getId(), WhatsAppCategoriaCota.LEMBRETE);
        }
        assertEquals(120, quotaService.consultarUso(empresa.getId()).lembretesEnviados());

        trocarPlano(empresa, "PLUS");

        var uso = quotaService.consultarUso(empresa.getId());
        assertEquals(120, uso.lembretesEnviados());
        assertEquals(300, uso.limiteLembretes());
        assertEquals(180, uso.lembretesDisponiveis());
        assertEquals(WhatsAppReserva.RESERVADA,
                quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.LEMBRETE));
    }

    @Test
    void downgradeBloqueiaAcimaDoNovoLimiteSemNegativar() {
        EmpresaEntity empresa = novaEmpresa("wpp-pdown");
        comAssinatura(empresa, "PLUS");
        for (int i = 0; i < 180; i++) {
            quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.LEMBRETE);
            quotaService.confirmarEnvio(empresa.getId(), WhatsAppCategoriaCota.LEMBRETE);
        }

        trocarPlano(empresa, "PRO");

        var uso = quotaService.consultarUso(empresa.getId());
        assertEquals(180, uso.lembretesEnviados());
        assertEquals(150, uso.limiteLembretes());
        assertEquals(0, uso.lembretesDisponiveis());
        assertEquals(WhatsAppReserva.LIMITE_ATINGIDO,
                quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.LEMBRETE));
        assertEquals(180, quotaService.consultarUso(empresa.getId()).lembretesEnviados());
    }

    @Test
    void downgradeParaBasicoBloqueiaECancelaPendencias() {
        EmpresaEntity empresa = novaEmpresa("wpp-pnobas");
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        ZonedDateTime at = atendimentoFuturo(3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente, at.toLocalDate(), at.toLocalTime());
        lembreteService.sincronizar(empresa.getId(), ag.getId());
        assertEquals(1, notificacaoRepository
                .findByEmpresaIdAndAgendamentoId(empresa.getId(), ag.getId()).size());

        trocarPlano(empresa, "BASICO");

        Map<String, Object> crm = crmService.enviarMensagem(empresa.getId(), cliente.getId(),
                new EnviarMensagemRequest("resgate", "whatsapp", null, "downgrade-req-1"));
        assertEquals(false, crm.get("success"));
        assertEquals("WHATSAPP_NAO_DISPONIVEL_NO_PLANO", crm.get("status"));

        WhatsAppNotificacaoEntity vencida = notificacaoRepository
                .findByEmpresaIdAndAgendamentoId(empresa.getId(), ag.getId()).get(0);
        vencida.setScheduledAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(30));
        vencida.setNextAttemptAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(30));
        notificacaoRepository.save(vencida);

        worker.processarLote(10);
        WhatsAppNotificacaoEntity finalizada = notificacaoRepository
                .findByEmpresaIdAndAgendamentoId(empresa.getId(), ag.getId()).get(0);
        assertEquals(WhatsAppStatusNotificacao.CANCELADO, finalizada.getStatus());
        assertEquals("PLAN_NO_WHATSAPP", finalizada.getLastError());
        verify(provider, never()).enviarTexto(any(), any(), any(), any());
    }

    // ---------- expiracao CRM 24h / backlog ----------

    @Test
    void crmComMenosDe24hEElegivel() {
        EmpresaEntity empresa = novaEmpresa("wpp-pcrm24");
        comAssinatura(empresa, "PRO");
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        when(provider.enviarTexto(any(), any(), any(), any()))
                .thenReturn(WhatsAppSendResult.sent("WAMID"));

        crmService.enviarMensagem(empresa.getId(), cliente.getId(),
                new EnviarMensagemRequest("resgate", "whatsapp", null, "crm24-req-1"));

        WhatsAppNotificacaoEntity criada = notificacoesDaEmpresa(empresa, cliente.getId());
        assertNotNull(criada.getExpiresAt());
        assertTrue(criada.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC).plusHours(23)));

        assertEquals(1, worker.processarLote(10));
        assertEquals(WhatsAppStatusNotificacao.ENVIADO, recarregar(criada.getId()).getStatus());
        verify(provider, times(1)).enviarTexto(any(), any(), any(), any());
    }

    private WhatsAppNotificacaoEntity notificacoesDaEmpresa(EmpresaEntity empresa, Long clienteId) {
        return notificacaoRepository.findAll().stream()
                .filter(n -> empresa.getId().equals(n.getEmpresa().getId())
                        && clienteId.equals(n.getCliente().getId()))
                .findFirst().orElseThrow();
    }

    private List<WhatsAppNotificacaoEntity> notificacoesDaEmpresa(EmpresaEntity empresa) {
        return notificacaoRepository.findAll().stream()
                .filter(n -> empresa.getId().equals(n.getEmpresa().getId()))
                .toList();
    }

    @Test
    void crmComMaisDe24hExpiraSemEnviarELibera() {
        EmpresaEntity empresa = novaEmpresa("wpp-pcrmexp");
        comAssinatura(empresa, "PRO");
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());

        crmService.enviarMensagem(empresa.getId(), cliente.getId(),
                new EnviarMensagemRequest("resgate", "whatsapp", null, "crmexp-req-1"));
        WhatsAppNotificacaoEntity criada = notificacoesDaEmpresa(empresa, cliente.getId());
        quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.CRM);
        WhatsAppNotificacaoEntity velha = recarregar(criada.getId());
        velha.setScheduledAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(30));
        velha.setNextAttemptAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(30));
        velha.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(6));
        velha.setQuotaReserved(true);
        velha.setQuotaCycleStart(LocalDate.now().minusDays(5));
        notificacaoRepository.save(velha);

        worker.processarLote(10);

        WhatsAppNotificacaoEntity finalizada = recarregar(criada.getId());
        assertEquals(WhatsAppStatusNotificacao.CANCELADO, finalizada.getStatus());
        assertEquals("CRM_MESSAGE_EXPIRED", finalizada.getLastError());
        verify(provider, never()).enviarTexto(any(), any(), any(), any());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).crmReservados());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).crmEnviados());
    }

    // ---------- telefone apos enfileirar ----------

    @Test
    void telefoneRemovidoAposEnfileirarCancelaSemProvider() {
        EmpresaEntity empresa = novaEmpresa("wpp-ptel");
        comAssinatura(empresa, "PRO");
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        when(provider.enviarTexto(any(), any(), any(), any()))
                .thenReturn(WhatsAppSendResult.sent("WAMID"));

        crmService.enviarMensagem(empresa.getId(), cliente.getId(),
                new EnviarMensagemRequest("resgate", "whatsapp", null, "tel-req-1"));
        WhatsAppNotificacaoEntity criada = notificacoesDaEmpresa(empresa, cliente.getId());

        ClienteEntity atual = clienteRepository.findById(cliente.getId()).orElseThrow();
        atual.setTelefone("00000001");
        clienteRepository.save(atual);

        worker.processarLote(10);

        WhatsAppNotificacaoEntity finalizada = recarregar(criada.getId());
        assertEquals(WhatsAppStatusNotificacao.CANCELADO, finalizada.getStatus());
        assertEquals("INVALID_RECIPIENT", finalizada.getLastError());
        verify(provider, never()).enviarTexto(any(), any(), any(), any());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).crmReservados());
    }

    // ---------- multiempresa ----------

    @Test
    void isolamentoMultiempresa() {
        EmpresaEntity empresaA = novaEmpresa("wpp-pmulti-a");
        EmpresaEntity empresaB = novaEmpresa("wpp-pmulti-b");
        comAssinatura(empresaA, "PRO");
        comAssinatura(empresaB, "BASICO");
        ClienteEntity clienteA = novoCliente(empresaA, telefoneCanonicoNovo());

        crmService.enviarMensagem(empresaA.getId(), clienteA.getId(),
                new EnviarMensagemRequest("resgate", "whatsapp", null, "multi-req-1"));
        WhatsAppNotificacaoEntity notificacaoA = notificacoesDaEmpresa(empresaA).get(0);

        // A nao consulta/cancela B (inexistente aqui) e B nao toca em A.
        assertTrue(notificacaoService.buscarPorId(empresaB.getId(), notificacaoA.getId()).isEmpty());
        lembreteService.cancelar(empresaB.getId(), 999999L);
        assertEquals(WhatsAppStatusNotificacao.PENDENTE, recarregar(notificacaoA.getId()).getStatus());

        // Uso independente por empresa/plano.
        assertEquals(150, quotaService.consultarUso(empresaA.getId()).limiteLembretes());
        assertEquals(0, quotaService.consultarUso(empresaB.getId()).limiteLembretes());

        // Worker envia cada empresa com seu proprio companyId.
        List<String> companies = new CopyOnWriteArrayList<>();
        when(provider.enviarTexto(any(), any(), any(), any())).thenAnswer(inv -> {
            companies.add(inv.getArgument(0));
            return WhatsAppSendResult.sent("WAMID");
        });
        worker.processarLote(10);
        assertEquals(List.of(String.valueOf(empresaA.getId())), companies);
    }

    // ---------- contadores ----------

    @Test
    void liberacaoDuplicadaNaoNegativa() {
        EmpresaEntity empresa = novaEmpresa("wpp-pneg");
        comAssinatura(empresa, "PRO");

        quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.CRM);
        quotaService.liberarReserva(empresa.getId(), WhatsAppCategoriaCota.CRM);
        quotaService.liberarReserva(empresa.getId(), WhatsAppCategoriaCota.CRM);
        quotaService.confirmarEnvio(empresa.getId(), WhatsAppCategoriaCota.CRM);

        var uso = quotaService.consultarUso(empresa.getId());
        assertEquals(0, uso.crmReservados());
        assertEquals(0, uso.crmEnviados());
    }

    @Test
    void checkConstraintsImpedemNegativoNoBanco() {
        EmpresaEntity empresa = novaEmpresa("wpp-pchk");
        comAssinatura(empresa, "PRO");
        quotaService.consultarUso(empresa.getId());
        Long usoId = usoRepository.findByEmpresaIdAndCicloInicio(
                empresa.getId(), LocalDate.now().minusDays(5)).orElseThrow().getId();

        for (String coluna : List.of("lembretes_reservados", "lembretes_enviados",
                "crm_reservados", "crm_enviados")) {
            try (Connection conexao = dataSource.getConnection();
                    PreparedStatement ps = conexao.prepareStatement(
                            "UPDATE whatsapp_uso_ciclos SET " + coluna + " = -1 WHERE id = ?")) {
                ps.setLong(1, usoId);
                ps.executeUpdate();
                throw new AssertionError("CHECK deveria impedir " + coluna + " negativo");
            } catch (AssertionError e) {
                throw e;
            } catch (Exception esperada) {
                // Constraint violada como esperado; rollback implicito por autocommit.
            }
        }
        var uso = quotaService.consultarUso(empresa.getId());
        assertEquals(0, uso.lembretesReservados());
        assertEquals(0, uso.crmReservados());
    }

    // ---------- worker sem configuracao ----------

    @Test
    void notConfiguredFalhaSemRetryInfinito() {
        EmpresaEntity empresa = novaEmpresa("wpp-pncfg");
        comAssinatura(empresa, "PRO");
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        when(provider.enviarTexto(any(), any(), any(), any()))
                .thenReturn(WhatsAppSendResult.erro(WhatsAppSendStatus.NOT_CONFIGURED));

        crmService.enviarMensagem(empresa.getId(), cliente.getId(),
                new EnviarMensagemRequest("resgate", "whatsapp", null, "ncfg-req-1"));

        worker.processarLote(10);
        WhatsAppNotificacaoEntity falha = notificacoesDaEmpresa(empresa).get(0);
        assertEquals(WhatsAppStatusNotificacao.FALHOU, recarregar(falha.getId()).getStatus());
        assertEquals(0, worker.processarLote(10));
        verify(provider, times(1)).enviarTexto(any(), any(), any(), any());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).crmReservados());
    }

    // ---------- stale com regras ----------

    @Test
    void staleComOptOutCancelaEmVezDeRefileirar() {        EmpresaEntity empresa = novaEmpresa("wpp-pstale");
        comAssinatura(empresa, "PRO");
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());

        crmService.enviarMensagem(empresa.getId(), cliente.getId(),
                new EnviarMensagemRequest("resgate", "whatsapp", null, "stale-req-1"));
        WhatsAppNotificacaoEntity criada = notificacoesDaEmpresa(empresa, cliente.getId());
        quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.CRM);
        WhatsAppNotificacaoEntity presa = recarregar(criada.getId());
        presa.setStatus(WhatsAppStatusNotificacao.ENVIANDO);
        presa.setAttempts(1);
        presa.setProcessingStartedAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10));
        presa.setSendStartedAt(null);
        presa.setQuotaReserved(true);
        presa.setQuotaCycleStart(LocalDate.now().minusDays(5));
        notificacaoRepository.save(presa);
        definirOptOut(empresa, cliente, false);

        // Pode haver outra linha stale de outros testes no banco
        // compartilhado; o que importa e o destino da nossa.
        assertTrue(worker.recuperarPresos(120) >= 1);

        WhatsAppNotificacaoEntity finalizada = recarregar(criada.getId());
        assertEquals(WhatsAppStatusNotificacao.CANCELADO, finalizada.getStatus());
        assertEquals("WHATSAPP_OPT_OUT", finalizada.getLastError());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).crmReservados());
    }

    // ---------- recipient sem cliente vinculado ----------

    @Test
    void crmSemClienteRecipientInvalidoCancelaSemProvider() {
        EmpresaEntity empresa = novaEmpresa("wpp-pnocli");
        comAssinatura(empresa, "PRO");
        when(provider.enviarTexto(any(), any(), any(), any()))
                .thenReturn(WhatsAppSendResult.sent("WAMID"));

        WhatsAppNotificacaoEntity criada = filaService.enfileirar(
                empresa.getId(), WhatsAppTipoNotificacao.CRM_RESGATE, "nocli-bad-" + System.nanoTime(),
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1), null, null,
                "00000001", "Texto");
        quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.CRM);
        WhatsAppNotificacaoEntity comReserva = recarregar(criada.getId());
        comReserva.setQuotaReserved(true);
        comReserva.setQuotaCycleStart(LocalDate.now().minusDays(5));
        notificacaoRepository.save(comReserva);

        worker.processarLote(10);

        WhatsAppNotificacaoEntity finalizada = recarregar(criada.getId());
        assertEquals(WhatsAppStatusNotificacao.CANCELADO, finalizada.getStatus());
        assertEquals("INVALID_RECIPIENT", finalizada.getLastError());
        verify(provider, never()).enviarTexto(any(), any(), any(), any());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).crmReservados());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).crmEnviados());
    }

    @Test
    void crmSemClienteRecipientValidoContinuaElegivel() {
        EmpresaEntity empresa = novaEmpresa("wpp-pnocli");
        comAssinatura(empresa, "PRO");
        when(provider.enviarTexto(any(), any(), any(), any()))
                .thenReturn(WhatsAppSendResult.sent("WAMID"));

        WhatsAppNotificacaoEntity criada = filaService.enfileirar(
                empresa.getId(), WhatsAppTipoNotificacao.CRM_RESGATE, "nocli-" + System.nanoTime(),
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1), null, null,
                "5511999999999", "Texto");

        assertEquals(1, worker.processarLote(10));
        assertEquals(WhatsAppStatusNotificacao.ENVIADO, recarregar(criada.getId()).getStatus());
        verify(provider, times(1)).enviarTexto(any(), any(), any(), any());
        assertEquals(1, quotaService.consultarUso(empresa.getId()).crmEnviados());
    }

    // ---------- opt-out pós-commit ----------

    @Test
    void flipComRollbackNaoCancelaPendencias() {        EmpresaEntity empresa = novaEmpresa("wpp-proll");
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        ZonedDateTime at = atendimentoFuturo(3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente, at.toLocalDate(), at.toLocalTime());
        lembreteService.sincronizar(empresa.getId(), ag.getId());
        WhatsAppNotificacaoEntity reminder = notificacaoRepository
                .findByEmpresaIdAndAgendamentoId(empresa.getId(), ag.getId()).get(0);

        transactionTemplate.execute(status -> {
            CompanyContext.setCompanyId(empresa.getId());
            try {
                clienteService.atualizar(cliente.getId(), new SalvarClienteRequest(
                        "Cli Prot", cliente.getTelefone(), cliente.getEmail(), null, empresa.getId(), false));
            } finally {
                CompanyContext.clear();
            }
            status.setRollbackOnly();
            return null;
        });

        assertEquals(WhatsAppStatusNotificacao.PENDENTE, recarregar(reminder.getId()).getStatus());
        assertTrue(clienteRepository.findById(cliente.getId()).orElseThrow().isReceberWhatsapp());
    }

    // ---------- cliente: flag no GET/criar/atualizar ----------

    @Test
    void getClienteRetornaReceberWhatsapp() {
        EmpresaEntity empresa = novaEmpresa("wpp-pgetcli");
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());

        CompanyContext.setCompanyId(empresa.getId());
        try {
            assertTrue(clienteService.buscarPorId(cliente.getId()).receberWhatsapp());
        } finally {
            CompanyContext.clear();
        }

        definirOptOut(empresa, cliente, false);
        CompanyContext.setCompanyId(empresa.getId());
        try {
            assertFalse(clienteService.buscarPorId(cliente.getId()).receberWhatsapp());
        } finally {
            CompanyContext.clear();
        }
    }

    @Test
    void updateSemCampoPreservaValorExistente() {
        EmpresaEntity empresa = novaEmpresa("wpp-pkeepcli");
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        definirOptOut(empresa, cliente, false);

        CompanyContext.setCompanyId(empresa.getId());
        try {
            var response = clienteService.atualizar(cliente.getId(), new SalvarClienteRequest(
                    "Cli Prot", cliente.getTelefone(), cliente.getEmail(), null, empresa.getId(), null));
            assertFalse(response.receberWhatsapp());
        } finally {
            CompanyContext.clear();
        }

        CompanyContext.setCompanyId(empresa.getId());
        try {
            var response = clienteService.atualizar(cliente.getId(), new SalvarClienteRequest(
                    "Cli Prot", cliente.getTelefone(), cliente.getEmail(), null, empresa.getId(), true));
            assertTrue(response.receberWhatsapp());
        } finally {
            CompanyContext.clear();
        }
    }
}

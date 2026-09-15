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

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AtualizarAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.CriarAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.event.AgendamentoWhatsAppSyncEvent;
import com.minhaempresa.gendaz.agendamento.listener.AgendamentoWhatsAppListener;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.agendamento.service.AgendamentoService;
import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import com.minhaempresa.gendaz.assinatura.repository.AssinaturaRepository;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.horarioatendimento.entity.HorarioAtendimentoEntity;
import com.minhaempresa.gendaz.horarioatendimento.enums.DiaSemanaAtendimento;
import com.minhaempresa.gendaz.horarioatendimento.repository.HorarioAtendimentoRepository;
import com.minhaempresa.gendaz.plano.entity.PlanoEntity;
import com.minhaempresa.gendaz.plano.repository.PlanoRepository;
import com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity;
import com.minhaempresa.gendaz.profissional.enums.DiaSemana;
import com.minhaempresa.gendaz.profissional.repository.ProfissionalRepository;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import com.minhaempresa.gendaz.servico.repository.ServicoRepository;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.PhoneNumberService;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppConfiguracaoEntity;
import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppNotificacaoEntity;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppCategoriaCota;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppStatusNotificacao;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppTipoNotificacao;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppConfiguracaoRepository;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppNotificacaoRepository;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppEnvioWorker;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppFilaService;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppLembreteAgendamentoService;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppQuotaService;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppReserva;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class WhatsAppLembreteAgendamentoTest {

    private static final AtomicLong SEQUENCIA = new AtomicLong();
    private static final String ZONA_SP = "America/Sao_Paulo";
    private static final String ZONA_CUIABA = "America/Cuiaba";

    @MockBean
    private WhatsAppProvider provider;

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private WhatsAppLembreteAgendamentoService lembreteService;
    @Autowired
    private WhatsAppFilaService filaService;
    @Autowired
    private AgendamentoWhatsAppListener listener;
    @Autowired
    private WhatsAppEnvioWorker worker;
    @Autowired
    private WhatsAppQuotaService quotaService;
    @Autowired
    private PhoneNumberService phoneNumberService;
    @Autowired
    private WhatsAppNotificacaoRepository notificacaoRepository;
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
    private HorarioAtendimentoRepository horarioAtendimentoRepository;
    @Autowired
    private AgendamentoService agendamentoService;

    @AfterEach
    void limparContexto() {
        CompanyContext.clear();
    }

    // ---------- fixtures ----------

    private EmpresaEntity novaEmpresa(String prefixo, String timezone) {
        long seq = SEQUENCIA.incrementAndGet();
        return empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia(prefixo + " " + seq)
                .email(prefixo + "-" + seq + "-" + System.nanoTime() + "@teste.com")
                .status(StatusEmpresa.ATIVA)
                .timezone(timezone)
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

    private void ativarLembretesComTemplate(EmpresaEntity empresa, String template) {
        configuracaoRepository.save(WhatsAppConfiguracaoEntity.builder()
                .empresa(empresa)
                .lembretesAtivos(true)
                .lembreteTemplate(template)
                .build());
    }

    private ClienteEntity novoCliente(EmpresaEntity empresa, String telefone) {
        long seq = SEQUENCIA.incrementAndGet();
        return clienteRepository.save(ClienteEntity.builder()
                .nome("Mariana Silva " + seq)
                .telefone(telefone)
                .email("cli" + seq + "-" + System.nanoTime() + "@x.com")
                .empresa(empresa).status(StatusCadastro.ATIVO).build());
    }

    private String telefoneCanonicoNovo() {
        return "55659" + String.format("%08d", (int) (Math.random() * 100000000));
    }

    private ServicoEntity novoServico(EmpresaEntity empresa) {
        return servicoRepository.save(ServicoEntity.builder()
                .nome("Corte Caro 200").duracaoMinutos(30).valor(new BigDecimal("200.00"))
                .status(StatusCadastro.ATIVO).empresa(empresa).build());
    }

    private ProfissionalEntity novoProfissional(EmpresaEntity empresa) {
        return profissionalRepository.save(ProfissionalEntity.builder()
                .nome("Prof").status(StatusCadastro.ATIVO)
                .diasTrabalho(EnumSet.allOf(DiaSemana.class))
                .empresa(empresa).build());
    }

    private AgendamentoEntity novoAgendamento(EmpresaEntity empresa, ClienteEntity cliente,
            LocalDate data, LocalTime hora, StatusAgendamento status) {
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);
        return agendamentoRepository.save(AgendamentoEntity.builder()
                .cliente(cliente).servico(servico).profissional(profissional).empresa(empresa)
                .data(data).horaInicio(hora).horaFim(hora.plusMinutes(30))
                .status(status).observacoes("CUPOM10 valor 100").build());
    }

    private void publicarSync(Long empresaId, Long agendamentoId,
            AgendamentoWhatsAppSyncEvent.Acao acao) {
        transactionTemplate.execute(status -> {
            eventPublisher.publishEvent(
                    new AgendamentoWhatsAppSyncEvent(agendamentoId, empresaId, acao));
            return null;
        });
    }

    private List<WhatsAppNotificacaoEntity> reminders(Long empresaId, Long agendamentoId) {
        return notificacaoRepository.findByEmpresaIdAndAgendamentoId(empresaId, agendamentoId).stream()
                .filter(n -> n.getTipo() == WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO)
                .toList();
    }

    private ZonedDateTime atendimentoFuturo(String zona, int dias, int hora) {
        return ZonedDateTime.now(ZoneId.of(zona)).plusDays(dias)
                .withHour(hora).withMinute(0).withSecond(0).withNano(0);
    }

    // ---------- 1-6: configuracao e plano ----------

    @Test
    void semConfiguracaoNaoCriaReminder() {
        EmpresaEntity empresa = novaEmpresa("wpp-lcfg", ZONA_SP);
        comAssinatura(empresa, "PRO");
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente,
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);

        publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);

        assertTrue(reminders(empresa.getId(), ag.getId()).isEmpty());
    }

    @Test
    void lembretesDesativadosNaoCriam() {
        EmpresaEntity empresa = novaEmpresa("wpp-loff", ZONA_SP);
        comAssinatura(empresa, "PRO");
        configuracaoRepository.save(WhatsAppConfiguracaoEntity.builder()
                .empresa(empresa).lembretesAtivos(false).build());
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente,
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);

        publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);

        assertTrue(reminders(empresa.getId(), ag.getId()).isEmpty());
    }

    @Test
    void basicoAtivoNaoCria() {
        EmpresaEntity empresa = novaEmpresa("wpp-lbas", ZONA_SP);
        comAssinatura(empresa, "BASICO");
        ativarLembretes(empresa);
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente,
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);

        publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);

        assertTrue(reminders(empresa.getId(), ag.getId()).isEmpty());
    }

    @Test
    void proPlusEnterpriseAtivosCriam() {
        for (String plano : List.of("PRO", "PLUS", "ENTERPRISE")) {
            EmpresaEntity empresa = novaEmpresa("wpp-l" + plano.toLowerCase(), ZONA_SP);
            comAssinatura(empresa, plano);
            ativarLembretes(empresa);
            ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
            ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
            AgendamentoEntity ag = novoAgendamento(empresa, cliente,
                    at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);

            publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);

            assertEquals(1, reminders(empresa.getId(), ag.getId()).size(), "plano " + plano);
        }
    }

    @Test
    void fallbackTemplatePadraoSubstituiClienteEmpresaDataEHora() {
        EmpresaEntity empresa = novaEmpresa("wpp-ltpl-default", ZONA_SP);
        empresa.setNomeFantasia("Clínica Bella");
        empresaRepository.save(empresa);
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 4, 14).withMinute(30);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente,
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);

        publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);

        WhatsAppNotificacaoEntity reminder = reminders(empresa.getId(), ag.getId()).get(0);
        assertEquals("Olá, " + cliente.getNome()
                + "! Lembrete: seu atendimento na Clínica Bella está marcado para "
                + at.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " às "
                + at.format(DateTimeFormatter.ofPattern("HH:mm")) + ".",
                reminder.getMessageBody());
    }

    @Test
    void templatePersonalizadoDaEmpresaEUsadoSemAfetarOutraEmpresa() {
        EmpresaEntity empresaA = novaEmpresa("wpp-ltpl-a", ZONA_SP);
        empresaA.setNomeFantasia("Empresa A");
        empresaRepository.save(empresaA);
        EmpresaEntity empresaB = novaEmpresa("wpp-ltpl-b", ZONA_SP);
        empresaB.setNomeFantasia("Empresa B");
        empresaRepository.save(empresaB);
        comAssinatura(empresaA, "PRO");
        comAssinatura(empresaB, "PRO");
        ativarLembretesComTemplate(empresaA, "{cliente}|{empresa}|{data}|{hora}");
        ativarLembretes(empresaB);
        ClienteEntity clienteA = novoCliente(empresaA, telefoneCanonicoNovo());
        ClienteEntity clienteB = novoCliente(empresaB, telefoneCanonicoNovo());
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 4, 16).withMinute(45);
        AgendamentoEntity agA = novoAgendamento(empresaA, clienteA,
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);
        AgendamentoEntity agB = novoAgendamento(empresaB, clienteB,
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);

        publicarSync(empresaA.getId(), agA.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);
        publicarSync(empresaB.getId(), agB.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);

        String data = at.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String hora = at.format(DateTimeFormatter.ofPattern("HH:mm"));
        assertEquals(clienteA.getNome() + "|Empresa A|" + data + "|" + hora,
                reminders(empresaA.getId(), agA.getId()).get(0).getMessageBody());
        assertTrue(reminders(empresaB.getId(), agB.getId()).get(0).getMessageBody()
                .contains("Empresa B"));
        assertFalse(reminders(empresaB.getId(), agB.getId()).get(0).getMessageBody().contains("|"));
    }

    // ---------- 7-9: horario e timezone ----------

    @Test
    void atendimento15hGeraScheduled13hEmUtc() {
        EmpresaEntity empresa = novaEmpresa("wpp-ltz", ZONA_SP);
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente,
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);

        publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);

        WhatsAppNotificacaoEntity reminder = reminders(empresa.getId(), ag.getId()).get(0);
        LocalDateTime esperadoScheduled = at.minusHours(2).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        assertEquals(esperadoScheduled, reminder.getScheduledAt());
        assertEquals(esperadoScheduled.plusMinutes(10), reminder.getExpiresAt());
        assertEquals(esperadoScheduled, reminder.getNextAttemptAt());
        long epoch = at.withZoneSameInstant(ZoneOffset.UTC).toEpochSecond();
        assertEquals("AGENDAMENTO_REMINDER:" + ag.getId() + ":" + epoch + ":" + cliente.getId(),
                reminder.getIdempotencyKey());
    }

    @Test
    void cuiabaConverteCorretamenteEDifereDeSaoPaulo() {
        EmpresaEntity empresa = novaEmpresa("wpp-lcba", ZONA_CUIABA);
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        ZonedDateTime at = atendimentoFuturo(ZONA_CUIABA, 3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente,
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);

        publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);

        WhatsAppNotificacaoEntity reminder = reminders(empresa.getId(), ag.getId()).get(0);
        LocalDateTime esperado = at.minusHours(2).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        assertEquals(esperado, reminder.getScheduledAt());
        LocalDateTime seFosseSp = at.withZoneSameInstant(ZoneId.of(ZONA_SP))
                .withHour(13).withMinute(0).withSecond(0).withNano(0)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        assertTrue(!esperado.equals(seFosseSp) || ZoneId.of(ZONA_SP).equals(ZoneId.of(ZONA_CUIABA)));
    }

    // ---------- 10-12: atraso, duplicata, independencia ----------

    @Test
    void agendamentoComMenosDe2hNaoCria() {
        EmpresaEntity empresa = novaEmpresa("wpp-lsoon", ZONA_SP);
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        ZonedDateTime at = ZonedDateTime.now(ZoneId.of(ZONA_SP)).plusHours(1)
                .withMinute(0).withSecond(0).withNano(0);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente,
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);

        publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);

        assertTrue(reminders(empresa.getId(), ag.getId()).isEmpty());
    }

    @Test
    void eventoDuplicadoNaoDuplica() {
        EmpresaEntity empresa = novaEmpresa("wpp-ldup", ZONA_SP);
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente,
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);

        publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);
        publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);

        List<WhatsAppNotificacaoEntity> lista = reminders(empresa.getId(), ag.getId());
        assertEquals(1, lista.size());
        assertEquals(WhatsAppStatusNotificacao.PENDENTE, lista.get(0).getStatus());
    }

    @Test
    void doisAgendamentosMesmoHorarioGeramDoisIndependentes() {
        EmpresaEntity empresa = novaEmpresa("wpp-ltwo", ZONA_SP);
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        AgendamentoEntity ag1 = novoAgendamento(empresa, novoCliente(empresa, telefoneCanonicoNovo()),
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);
        AgendamentoEntity ag2 = novoAgendamento(empresa, novoCliente(empresa, telefoneCanonicoNovo()),
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);

        publicarSync(empresa.getId(), ag1.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);
        publicarSync(empresa.getId(), ag2.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);

        assertEquals(1, reminders(empresa.getId(), ag1.getId()).size());
        assertEquals(1, reminders(empresa.getId(), ag2.getId()).size());
        assertTrue(!reminders(empresa.getId(), ag1.getId()).get(0).getId()
                .equals(reminders(empresa.getId(), ag2.getId()).get(0).getId()));
    }

    // ---------- 13-17: status ----------

    @Test
    void apenasPendenteEConfirmadoSaoElegiveis() {
        for (StatusAgendamento status : List.of(StatusAgendamento.PENDENTE, StatusAgendamento.CONFIRMADO)) {
            EmpresaEntity empresa = novaEmpresa("wpp-lst-ok", ZONA_SP);
            comAssinatura(empresa, "PRO");
            ativarLembretes(empresa);
            ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
            AgendamentoEntity ag = novoAgendamento(empresa, novoCliente(empresa, telefoneCanonicoNovo()),
                    at.toLocalDate(), at.toLocalTime(), status);

            publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);

            assertEquals(1, reminders(empresa.getId(), ag.getId()).size(), "status " + status);
        }
        for (StatusAgendamento status : List.of(StatusAgendamento.CANCELADO, StatusAgendamento.FINALIZADO,
                StatusAgendamento.EM_ATENDIMENTO, StatusAgendamento.PAUSADO)) {
            EmpresaEntity empresa = novaEmpresa("wpp-lst-no", ZONA_SP);
            comAssinatura(empresa, "PRO");
            ativarLembretes(empresa);
            ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
            AgendamentoEntity ag = novoAgendamento(empresa, novoCliente(empresa, telefoneCanonicoNovo()),
                    at.toLocalDate(), at.toLocalTime(), status);

            publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);

            assertTrue(reminders(empresa.getId(), ag.getId()).isEmpty(), "status " + status);
        }
    }

    @Test
    void excluidoAgendaNaoGera() {
        EmpresaEntity empresa = novaEmpresa("wpp-lexc", ZONA_SP);
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, novoCliente(empresa, telefoneCanonicoNovo()),
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);
        ag.setExcluidoAgenda(true);
        agendamentoRepository.save(ag);

        publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);

        assertTrue(reminders(empresa.getId(), ag.getId()).isEmpty());
    }

    @Test
    void clienteExcluidoNaoGera() {
        EmpresaEntity empresa = novaEmpresa("wpp-lcliex", ZONA_SP);
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        cliente.setStatus(StatusCadastro.EXCLUIDO);
        clienteRepository.save(cliente);
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente,
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);

        publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);

        assertTrue(reminders(empresa.getId(), ag.getId()).isEmpty());
    }

    // ---------- 18-19: telefone ----------

    @Test
    void telefoneCanonicoEUsadoEInvalidoNaoCria() {
        assertTrue(phoneNumberService.canonicoValido("5565999999999"));
        assertFalse(phoneNumberService.canonicoValido("abc"));
        assertFalse(phoneNumberService.canonicoValido("1234567"));
        assertFalse(phoneNumberService.canonicoValido("11988887777"));

        EmpresaEntity empresa = novaEmpresa("wpp-ltel", ZONA_SP);
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        AgendamentoEntity agOk = novoAgendamento(empresa, novoCliente(empresa, "5565999999999"),
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);
        AgendamentoEntity agRuim = novoAgendamento(empresa, novoCliente(empresa, "11988887777"),
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);

        publicarSync(empresa.getId(), agOk.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);
        publicarSync(empresa.getId(), agRuim.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);

        List<WhatsAppNotificacaoEntity> ok = reminders(empresa.getId(), agOk.getId());
        assertEquals(1, ok.size());
        assertEquals("5565999999999", ok.get(0).getRecipient());
        assertTrue(reminders(empresa.getId(), agRuim.getId()).isEmpty());
    }

    // ---------- 20-21: mensagem ----------

    @Test
    void mensagemTemDadosCertosESemDadosInternos() {
        EmpresaEntity empresa = novaEmpresa("wpp-lmsg", ZONA_SP);
        empresa.setNomeFantasia("Clínica Bella");
        empresaRepository.save(empresa);
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, cliente,
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);

        publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);

        String mensagem = reminders(empresa.getId(), ag.getId()).get(0).getMessageBody();
        assertNotNull(mensagem);
        assertTrue(mensagem.contains(cliente.getNome()));
        assertTrue(mensagem.contains("Clínica Bella"));
        assertTrue(mensagem.contains(at.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        assertTrue(mensagem.contains("15:00"));
        assertFalse(mensagem.contains("CUPOM10"));
        assertFalse(mensagem.contains("200"));
        assertFalse(mensagem.contains("Corte Caro"));
        assertTrue(mensagem.startsWith("Olá, "));
    }

    // ---------- 22-25: cancelamento ----------

    private void reservarLembrete(EmpresaEntity empresa, WhatsAppNotificacaoEntity reminder) {
        quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.LEMBRETE);
        WhatsAppNotificacaoEntity atual = notificacaoRepository.findById(reminder.getId()).orElseThrow();
        atual.setQuotaReserved(true);
        atual.setQuotaCycleStart(LocalDate.now().minusDays(5));
        notificacaoRepository.save(atual);
    }

    @Test
    void cancelarPendenteCancela() {
        EmpresaEntity empresa = novaEmpresa("wpp-lcx", ZONA_SP);
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, novoCliente(empresa, telefoneCanonicoNovo()),
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);
        publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);
        WhatsAppNotificacaoEntity reminder = reminders(empresa.getId(), ag.getId()).get(0);

        publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.CANCELAR);

        WhatsAppNotificacaoEntity cancelado = notificacaoRepository.findById(reminder.getId()).orElseThrow();
        assertEquals(WhatsAppStatusNotificacao.CANCELADO, cancelado.getStatus());
    }

    @Test
    void cancelarPendenteComReservaLiberaExatamenteUm() {
        EmpresaEntity empresa = novaEmpresa("wpp-lcres", ZONA_SP);
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, novoCliente(empresa, telefoneCanonicoNovo()),
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);
        publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);
        WhatsAppNotificacaoEntity reminder = reminders(empresa.getId(), ag.getId()).get(0);
        reservarLembrete(empresa, reminder);
        assertEquals(1, quotaService.consultarUso(empresa.getId()).lembretesReservados());

        publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.CANCELAR);

        assertEquals(0, quotaService.consultarUso(empresa.getId()).lembretesReservados());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).lembretesEnviados());
        assertEquals(WhatsAppStatusNotificacao.CANCELADO,
                notificacaoRepository.findById(reminder.getId()).orElseThrow().getStatus());
    }

    @Test
    void cancelarEnviandoSemSendStartedCancelaELibera() {
        EmpresaEntity empresa = novaEmpresa("wpp-lcenv", ZONA_SP);
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, novoCliente(empresa, telefoneCanonicoNovo()),
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);
        publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);
        WhatsAppNotificacaoEntity reminder = reminders(empresa.getId(), ag.getId()).get(0);
        reservarLembrete(empresa, reminder);
        WhatsAppNotificacaoEntity preso = notificacaoRepository.findById(reminder.getId()).orElseThrow();
        preso.setStatus(WhatsAppStatusNotificacao.ENVIANDO);
        preso.setProcessingStartedAt(LocalDateTime.now().minusMinutes(5));
        preso.setSendStartedAt(null);
        notificacaoRepository.save(preso);

        publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.CANCELAR);

        WhatsAppNotificacaoEntity cancelado = notificacaoRepository.findById(reminder.getId()).orElseThrow();
        assertEquals(WhatsAppStatusNotificacao.CANCELADO, cancelado.getStatus());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).lembretesReservados());
    }

    @Test
    void cancelarEnviandoComSendStartedNaoAltera() {
        EmpresaEntity empresa = novaEmpresa("wpp-lcstarted", ZONA_SP);
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, novoCliente(empresa, telefoneCanonicoNovo()),
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);
        publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);
        WhatsAppNotificacaoEntity reminder = reminders(empresa.getId(), ag.getId()).get(0);
        reservarLembrete(empresa, reminder);
        WhatsAppNotificacaoEntity preso = notificacaoRepository.findById(reminder.getId()).orElseThrow();
        preso.setStatus(WhatsAppStatusNotificacao.ENVIANDO);
        // Timestamps recentes de proposito: a linha nao pode ficar stale para
        // outros testes que compartilham o banco (recovery a consumiria).
        preso.setProcessingStartedAt(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(10));
        preso.setSendStartedAt(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(5));
        notificacaoRepository.save(preso);

        publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.CANCELAR);

        WhatsAppNotificacaoEntity mantido = notificacaoRepository.findById(reminder.getId()).orElseThrow();
        assertEquals(WhatsAppStatusNotificacao.ENVIANDO, mantido.getStatus());
        assertTrue(mantido.isQuotaReserved());
        assertEquals(1, quotaService.consultarUso(empresa.getId()).lembretesReservados());

        // Finaliza para nao deixar resto stale para outros testes:
        // sendMessage + messageId = aceito pelo provider, NAO entregue.
        worker.finalizar(preso.getId(), WhatsAppSendResult.sent("WAMID"));
        assertEquals(WhatsAppStatusNotificacao.AGUARDANDO_ENTREGA,
                notificacaoRepository.findById(reminder.getId()).orElseThrow().getStatus());
    }

    // ---------- 26-28: reagendamento ----------

    @Test
    void remarcarCancelaAntigoECriaNovo() {
        EmpresaEntity empresa = novaEmpresa("wpp-lrem", ZONA_SP);
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, novoCliente(empresa, telefoneCanonicoNovo()),
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);
        publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);
        WhatsAppNotificacaoEntity antigo = reminders(empresa.getId(), ag.getId()).get(0);
        reservarLembrete(empresa, antigo);

        ZonedDateTime novo = at.plusHours(3);
        ag.setData(novo.toLocalDate());
        ag.setHoraInicio(novo.toLocalTime());
        ag.setHoraFim(novo.toLocalTime().plusMinutes(30));
        agendamentoRepository.save(ag);
        publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);
        publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);

        List<WhatsAppNotificacaoEntity> todos = reminders(empresa.getId(), ag.getId());
        assertEquals(2, todos.size());
        assertEquals(WhatsAppStatusNotificacao.CANCELADO,
                notificacaoRepository.findById(antigo.getId()).orElseThrow().getStatus());
        List<WhatsAppNotificacaoEntity> pendentes = todos.stream()
                .filter(n -> n.getStatus() == WhatsAppStatusNotificacao.PENDENTE)
                .toList();
        assertEquals(1, pendentes.size());
        LocalDateTime esperado = novo.minusHours(2).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        assertEquals(esperado, pendentes.get(0).getScheduledAt());
        assertEquals(0, pendentes.get(0).getAttempts());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).lembretesReservados());
    }

    // ---------- fluxos via AgendamentoService ----------

    private void garantirHorario(EmpresaEntity empresa, LocalDate data) {
        horarioAtendimentoRepository.save(HorarioAtendimentoEntity.builder()
                .empresa(empresa)
                .diaSemana(DiaSemanaAtendimento.from(data.getDayOfWeek()))
                .horaInicio(LocalTime.of(8, 0))
                .horaFim(LocalTime.of(20, 0))
                .ativo(true)
                .intervaloMinutos(30)
                .build());
    }

    private EmpresaEntity empresaServicoPronta(String prefixo, String plano) {
        EmpresaEntity empresa = novaEmpresa(prefixo, ZONA_SP);
        comAssinatura(empresa, plano);
        ativarLembretes(empresa);
        return empresa;
    }

    @Test
    void criarViaServiceGeraReminder() {
        EmpresaEntity empresa = empresaServicoPronta("wpp-lsvc", "PRO");
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        garantirHorario(empresa, at.toLocalDate());

        CompanyContext.setCompanyId(empresa.getId());
        try {
            var response = agendamentoService.criar(new CriarAgendamentoRequest(
                    cliente.getId(), servico.getId(), profissional.getId(), empresa.getId(),
                    at.toLocalDate(), at.toLocalTime(), null, null));
            assertEquals(1, reminders(empresa.getId(), response.id()).size());
        } finally {
            CompanyContext.clear();
        }
    }

    @Test
    void cancelarEExcluirViaServiceCancelamReminder() {
        for (String operacao : List.of("cancelar", "excluir")) {
            EmpresaEntity empresa = empresaServicoPronta("wpp-l" + operacao, "PRO");
            ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
            ServicoEntity servico = novoServico(empresa);
            ProfissionalEntity profissional = novoProfissional(empresa);
            ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
            garantirHorario(empresa, at.toLocalDate());

            CompanyContext.setCompanyId(empresa.getId());
            try {
                var response = agendamentoService.criar(new CriarAgendamentoRequest(
                        cliente.getId(), servico.getId(), profissional.getId(), empresa.getId(),
                        at.toLocalDate(), at.toLocalTime(), null, null));
                assertEquals(1, reminders(empresa.getId(), response.id()).size());
                if (operacao.equals("cancelar")) {
                    agendamentoService.cancelar(response.id(), empresa.getId());
                } else {
                    agendamentoService.excluir(response.id(), empresa.getId());
                }
                List<WhatsAppNotificacaoEntity> lista = reminders(empresa.getId(), response.id());
                assertEquals(1, lista.size());
                assertEquals(WhatsAppStatusNotificacao.CANCELADO, lista.get(0).getStatus());
            } finally {
                CompanyContext.clear();
            }
        }
    }

    @Test
    void remarcarViaServiceTrocaReminder() {
        EmpresaEntity empresa = empresaServicoPronta("wpp-lremsvc", "PRO");
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        ZonedDateTime novo = at.plusDays(1).withHour(18);
        garantirHorario(empresa, at.toLocalDate());
        garantirHorario(empresa, novo.toLocalDate());

        CompanyContext.setCompanyId(empresa.getId());
        try {
            var response = agendamentoService.criar(new CriarAgendamentoRequest(
                    cliente.getId(), servico.getId(), profissional.getId(), empresa.getId(),
                    at.toLocalDate(), at.toLocalTime(), null, null));
            WhatsAppNotificacaoEntity antigo = reminders(empresa.getId(), response.id()).get(0);
            agendamentoService.remarcar(response.id(),
                    new com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.RemarcarAgendamentoRequest(
                            novo.toLocalDate(), novo.toLocalTime()));

            List<WhatsAppNotificacaoEntity> todos = reminders(empresa.getId(), response.id());
            assertEquals(2, todos.size());
            assertEquals(WhatsAppStatusNotificacao.CANCELADO,
                    notificacaoRepository.findById(antigo.getId()).orElseThrow().getStatus());
            List<WhatsAppNotificacaoEntity> pendentes = todos.stream()
                    .filter(n -> n.getStatus() == WhatsAppStatusNotificacao.PENDENTE)
                    .toList();
            assertEquals(1, pendentes.size());
            assertEquals(novo.minusHours(2).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(),
                    pendentes.get(0).getScheduledAt());
        } finally {
            CompanyContext.clear();
        }
    }

    @Test
    void atualizarViaServiceSincroniza() {
        EmpresaEntity empresa = empresaServicoPronta("wpp-lupd", "PRO");
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        garantirHorario(empresa, at.toLocalDate());

        CompanyContext.setCompanyId(empresa.getId());
        try {
            var response = agendamentoService.criar(new CriarAgendamentoRequest(
                    cliente.getId(), servico.getId(), profissional.getId(), empresa.getId(),
                    at.toLocalDate(), at.toLocalTime(), null, null));
            WhatsAppNotificacaoEntity original = reminders(empresa.getId(), response.id()).get(0);

            // Update sem mudar horario: sem duplicata.
            agendamentoService.atualizar(response.id(), new AtualizarAgendamentoRequest(
                    cliente.getId(), servico.getId(), profissional.getId(), empresa.getId(),
                    at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE, "obs nova"));
            assertEquals(1, reminders(empresa.getId(), response.id()).size());
            assertEquals(original.getId(), reminders(empresa.getId(), response.id()).get(0).getId());

            // Update mudando horario: nova versao.
            ZonedDateTime novo = at.plusHours(2);
            agendamentoService.atualizar(response.id(), new AtualizarAgendamentoRequest(
                    cliente.getId(), servico.getId(), profissional.getId(), empresa.getId(),
                    novo.toLocalDate(), novo.toLocalTime(), StatusAgendamento.PENDENTE, "obs nova"));
            List<WhatsAppNotificacaoEntity> todos = reminders(empresa.getId(), response.id());
            assertEquals(2, todos.size());
            assertEquals(WhatsAppStatusNotificacao.CANCELADO,
                    notificacaoRepository.findById(original.getId()).orElseThrow().getStatus());

            // Update para CANCELADO: cancela.
            agendamentoService.atualizar(response.id(), new AtualizarAgendamentoRequest(
                    cliente.getId(), servico.getId(), profissional.getId(), empresa.getId(),
                    novo.toLocalDate(), novo.toLocalTime(), StatusAgendamento.CANCELADO, "obs nova"));
            assertTrue(reminders(empresa.getId(), response.id()).stream()
                    .noneMatch(n -> n.getStatus() == WhatsAppStatusNotificacao.PENDENTE));
        } finally {
            CompanyContext.clear();
        }
    }

    // ---------- 32-34: expiracao e outdated no worker ----------

    private void envelhecer(WhatsAppNotificacaoEntity reminder, LocalDateTime scheduledAt,
            LocalDateTime nextAttemptAt, LocalDateTime expiresAt) {
        WhatsAppNotificacaoEntity atual =
                notificacaoRepository.findById(reminder.getId()).orElseThrow();
        atual.setScheduledAt(scheduledAt);
        atual.setNextAttemptAt(nextAttemptAt);
        atual.setExpiresAt(expiresAt);
        notificacaoRepository.save(atual);
    }

    @Test
    void reminderExpiradoAntesDoClaimCancelaSemProviderSemReserva() {
        EmpresaEntity empresa = novaEmpresa("wpp-lexp", ZONA_SP);
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, novoCliente(empresa, telefoneCanonicoNovo()),
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);
        publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);
        WhatsAppNotificacaoEntity reminder = reminders(empresa.getId(), ag.getId()).get(0);
        envelhecer(reminder, LocalDateTime.now(ZoneOffset.UTC).minusHours(2),
                LocalDateTime.now(ZoneOffset.UTC).minusHours(2),
                LocalDateTime.now(ZoneOffset.UTC).minusHours(1));

        worker.processarLote(10);

        WhatsAppNotificacaoEntity finalizada =
                notificacaoRepository.findById(reminder.getId()).orElseThrow();
        assertEquals(WhatsAppStatusNotificacao.CANCELADO, finalizada.getStatus());
        assertEquals("REMINDER_EXPIRED", finalizada.getLastError());
        verify(provider, never()).enviarTexto(any(), any(), any(), any());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).lembretesReservados());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).lembretesEnviados());
    }

    @Test
    void retryReservadoQueExpiraLiberaReservaUmaVez() {
        EmpresaEntity empresa = novaEmpresa("wpp-lexpr", ZONA_SP);
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, novoCliente(empresa, telefoneCanonicoNovo()),
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);
        publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);
        WhatsAppNotificacaoEntity reminder = reminders(empresa.getId(), ag.getId()).get(0);
        reservarLembrete(empresa, reminder);
        WhatsAppNotificacaoEntity tentativa = notificacaoRepository.findById(reminder.getId()).orElseThrow();
        tentativa.setAttempts(1);
        notificacaoRepository.save(tentativa);
        envelhecer(reminder, LocalDateTime.now(ZoneOffset.UTC).minusHours(2),
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1),
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));

        worker.processarLote(10);

        WhatsAppNotificacaoEntity finalizada =
                notificacaoRepository.findById(reminder.getId()).orElseThrow();
        assertEquals(WhatsAppStatusNotificacao.CANCELADO, finalizada.getStatus());
        assertEquals("REMINDER_EXPIRED", finalizada.getLastError());
        verify(provider, never()).enviarTexto(any(), any(), any(), any());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).lembretesReservados());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).lembretesEnviados());
    }

    @Test
    void reminderStaleAposReagendamentoViraOutdated() {
        EmpresaEntity empresa = novaEmpresa("wpp-loutd", ZONA_SP);
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, novoCliente(empresa, telefoneCanonicoNovo()),
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);
        publicarSync(empresa.getId(), ag.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR);
        WhatsAppNotificacaoEntity reminder = reminders(empresa.getId(), ag.getId()).get(0);

        // Reagendamento sem evento: versao do reminder fica obsoleta.
        ZonedDateTime novo = at.plusHours(3);
        ag.setData(novo.toLocalDate());
        ag.setHoraInicio(novo.toLocalTime());
        ag.setHoraFim(novo.toLocalTime().plusMinutes(30));
        agendamentoRepository.save(ag);
        // Torna claimavel, mas com validade futura para nao expirar.
        envelhecer(reminder, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(30),
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(30),
                LocalDateTime.now(ZoneOffset.UTC).plusHours(1));

        worker.processarLote(10);

        WhatsAppNotificacaoEntity finalizada =
                notificacaoRepository.findById(reminder.getId()).orElseThrow();
        assertEquals(WhatsAppStatusNotificacao.CANCELADO, finalizada.getStatus());
        assertEquals("REMINDER_OUTDATED", finalizada.getLastError());
        verify(provider, never()).enviarTexto(any(), any(), any(), any());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).lembretesReservados());
    }

    // ---------- concorrencia ----------

    @Test
    void doisSincronizarSimultaneosGeramUmaUnicaNotificacao() throws Exception {
        EmpresaEntity empresa = novaEmpresa("wpp-lconc", ZONA_SP);
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, novoCliente(empresa, telefoneCanonicoNovo()),
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch largada = new CountDownLatch(1);
            List<Throwable> erros = new CopyOnWriteArrayList<>();
            List<WhatsAppNotificacaoEntity> resultados = new CopyOnWriteArrayList<>();
            AtomicInteger duplicadasBenignas = new AtomicInteger();
            Future<?> f1 = executor.submit(() -> {
                try {
                    largada.await();
                    WhatsAppNotificacaoEntity resultado = lembreteServiceProxy(empresa.getId(), ag.getId());
                    if (resultado != null) {
                        resultados.add(resultado);
                    } else {
                        duplicadasBenignas.incrementAndGet();
                    }
                } catch (com.minhaempresa.gendaz.whatsapp.service.WhatsAppNotificacaoDuplicadaException benigna) {
                    // Corrida real: a UNIQUE barrou a segunda insercao antes que
                    // a primeira fosse visivel para releitura. Resultado final
                    // continua sendo uma unica notificacao.
                    duplicadasBenignas.incrementAndGet();
                } catch (Throwable t) {
                    erros.add(t);
                }
            });
            Future<?> f2 = executor.submit(() -> {
                try {
                    largada.await();
                    WhatsAppNotificacaoEntity resultado = lembreteServiceProxy(empresa.getId(), ag.getId());
                    if (resultado != null) {
                        resultados.add(resultado);
                    } else {
                        duplicadasBenignas.incrementAndGet();
                    }
                } catch (com.minhaempresa.gendaz.whatsapp.service.WhatsAppNotificacaoDuplicadaException benigna) {
                    duplicadasBenignas.incrementAndGet();
                } catch (Throwable t) {
                    erros.add(t);
                }
            });
            largada.countDown();
            f1.get(60, TimeUnit.SECONDS);
            f2.get(60, TimeUnit.SECONDS);

            assertTrue(erros.isEmpty());
            assertEquals(2, resultados.size() + duplicadasBenignas.get());
            assertTrue(!resultados.isEmpty());
            for (WhatsAppNotificacaoEntity resultado : resultados) {
                assertEquals(resultados.get(0).getId(), resultado.getId());
            }
            assertEquals(1, reminders(empresa.getId(), ag.getId()).size());
        } finally {
            executor.shutdownNow();
        }
    }

    private WhatsAppNotificacaoEntity lembreteServiceProxy(Long empresaId, Long agendamentoId) {
        lembreteService.sincronizar(empresaId, agendamentoId);
        List<WhatsAppNotificacaoEntity> lista = reminders(empresaId, agendamentoId);
        // Em corrida benigna a linha vencedora pode ainda estar invisivel
        // para esta thread: o chamador trata a duplicata separadamente e a
        // assercao final (uma unica notificacao) vale apos ambas commitarem.
        return lista.isEmpty() ? null : lista.get(0);
    }

    // ---------- 35-36: isolamento de falha e rollback ----------

    @Test
    void falhaDoWhatsAppNaoQuebraAgendamento() {
        EmpresaEntity empresa = novaEmpresa("wpp-liso", ZONA_SP);
        comAssinatura(empresa, "BASICO");
        ativarLembretes(empresa);
        ClienteEntity cliente = novoCliente(empresa, telefoneCanonicoNovo());
        ServicoEntity servico = novoServico(empresa);
        ProfissionalEntity profissional = novoProfissional(empresa);
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        garantirHorario(empresa, at.toLocalDate());

        CompanyContext.setCompanyId(empresa.getId());
        try {
            var response = agendamentoService.criar(new CriarAgendamentoRequest(
                    cliente.getId(), servico.getId(), profissional.getId(), empresa.getId(),
                    at.toLocalDate(), at.toLocalTime(), null, null));
            assertNotNull(response.id());
            assertTrue(reminders(empresa.getId(), response.id()).isEmpty());
        } finally {
            CompanyContext.clear();
        }

        // Listener com evento invalido nunca lanca.
        listener.onAgendamentoWhatsAppSync(
                new AgendamentoWhatsAppSyncEvent(999999L, 888888L,
                        AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR));
        listener.onAgendamentoWhatsAppSync(null);
    }

    @Test
    void eventoEmTransacaoComRollbackNaoGeraReminder() {
        EmpresaEntity empresa = novaEmpresa("wpp-lroll", ZONA_SP);
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, novoCliente(empresa, telefoneCanonicoNovo()),
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);

        transactionTemplate.execute(status -> {
            eventPublisher.publishEvent(new AgendamentoWhatsAppSyncEvent(
                    ag.getId(), empresa.getId(), AgendamentoWhatsAppSyncEvent.Acao.SINCRONIZAR));
            status.setRollbackOnly();
            return null;
        });

        assertTrue(reminders(empresa.getId(), ag.getId()).isEmpty());
    }

    // ---------- lock cancelamento x inicio de envio ----------

    private WhatsAppNotificacaoEntity reminderOperacionalPronto(
            EmpresaEntity empresa, AgendamentoEntity ag, String chave) {
        WhatsAppNotificacaoEntity criado = filaService.enfileirar(
                empresa.getId(), WhatsAppTipoNotificacao.LEMBRETE_AGENDAMENTO, chave,
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1), null, ag.getId(),
                "5511999999999", "Lembrete operacional");
        // Reserva real + estado ENVIANDO montados diretamente: o que esta em
        // teste e a atomicidade cancelar x marcarInicioEnvio, nao o claim
        // (que e global e sensivel a linhas de outros testes). Equivale ao
        // resultado de um claim bem-sucedido.
        assertEquals(WhatsAppReserva.RESERVADA,
                quotaService.reservar(empresa.getId(), WhatsAppCategoriaCota.LEMBRETE));
        WhatsAppNotificacaoEntity reivindicado =
                notificacaoRepository.findById(criado.getId()).orElseThrow();
        reivindicado.setStatus(WhatsAppStatusNotificacao.ENVIANDO);
        reivindicado.setAttempts(reivindicado.getAttempts() + 1);
        reivindicado.setProcessingStartedAt(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(5));
        reivindicado.setSendStartedAt(null);
        reivindicado.setQuotaReserved(true);
        reivindicado.setQuotaCycleStart(LocalDate.now().minusDays(5));
        return notificacaoRepository.save(reivindicado);
    }

    @Test
    void cancelamentoAntesDoMarcoImpedeEnvio() {
        EmpresaEntity empresa = novaEmpresa("wpp-lock-cx", ZONA_SP);
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, novoCliente(empresa, telefoneCanonicoNovo()),
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);
        WhatsAppNotificacaoEntity pronto =
                reminderOperacionalPronto(empresa, ag, "lock-cx-" + System.nanoTime());

        lembreteService.cancelar(empresa.getId(), ag.getId());

        assertFalse(worker.marcarInicioEnvio(pronto.getId()));
        WhatsAppNotificacaoEntity finalizada =
                notificacaoRepository.findById(pronto.getId()).orElseThrow();
        assertEquals(WhatsAppStatusNotificacao.CANCELADO, finalizada.getStatus());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).lembretesReservados());
        verify(provider, never()).enviarTexto(any(), any(), any(), any());
    }

    @Test
    void marcoAntesDoCancelamentoPreservaEnvio() {
        EmpresaEntity empresa = novaEmpresa("wpp-lock-mx", ZONA_SP);
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, novoCliente(empresa, telefoneCanonicoNovo()),
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);
        WhatsAppNotificacaoEntity pronto =
                reminderOperacionalPronto(empresa, ag, "lock-mx-" + System.nanoTime());

        assertTrue(worker.marcarInicioEnvio(pronto.getId()));
        lembreteService.cancelar(empresa.getId(), ag.getId());

        WhatsAppNotificacaoEntity mantido =
                notificacaoRepository.findById(pronto.getId()).orElseThrow();
        assertEquals(WhatsAppStatusNotificacao.ENVIANDO, mantido.getStatus());
        assertNotNull(mantido.getSendStartedAt());
        assertTrue(mantido.isQuotaReserved());

        worker.finalizar(pronto.getId(), WhatsAppSendResult.sent("WAMID-LOCK"));
        // sendMessage + messageId = aceito pelo provider, NAO entregue.
        assertEquals(WhatsAppStatusNotificacao.AGUARDANDO_ENTREGA,
                notificacaoRepository.findById(pronto.getId()).orElseThrow().getStatus());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).lembretesEnviados());
        assertEquals(1, quotaService.consultarUso(empresa.getId()).lembretesReservados());
    }

    @Test
    void cancelVsMarcoInicioConcorrentesSaoAtomicos() throws Exception {
        EmpresaEntity empresa = novaEmpresa("wpp-lock-race", ZONA_SP);
        comAssinatura(empresa, "PRO");
        ativarLembretes(empresa);
        ZonedDateTime at = atendimentoFuturo(ZONA_SP, 3, 15);
        AgendamentoEntity ag = novoAgendamento(empresa, novoCliente(empresa, telefoneCanonicoNovo()),
                at.toLocalDate(), at.toLocalTime(), StatusAgendamento.PENDENTE);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 20; i++) {
                WhatsAppNotificacaoEntity pronto = reminderOperacionalPronto(
                        empresa, ag, "lock-race-" + i + "-" + System.nanoTime());
                int reservadosAntes =
                        quotaService.consultarUso(empresa.getId()).lembretesReservados();
                CountDownLatch largada = new CountDownLatch(1);
                AtomicBoolean marcou = new AtomicBoolean(false);
                List<Throwable> erros = new CopyOnWriteArrayList<>();
                Future<?> f1 = executor.submit(() -> {
                    try {
                        largada.await();
                        lembreteService.cancelar(empresa.getId(), ag.getId());
                    } catch (Throwable t) {
                        erros.add(t);
                    }
                });
                Future<?> f2 = executor.submit(() -> {
                    try {
                        largada.await();
                        marcou.set(worker.marcarInicioEnvio(pronto.getId()));
                    } catch (Throwable t) {
                        erros.add(t);
                    }
                });
                largada.countDown();
                f1.get(60, TimeUnit.SECONDS);
                f2.get(60, TimeUnit.SECONDS);

                assertTrue(erros.isEmpty(), "iteracao " + i);
                WhatsAppNotificacaoEntity atual =
                        notificacaoRepository.findById(pronto.getId()).orElseThrow();
                int reservadosDepois =
                        quotaService.consultarUso(empresa.getId()).lembretesReservados();
                if (marcou.get()) {
                    // Marco venceu: cancelamento posterior preservou tudo
                    // (a reserva do claim continua, sem duplicar).
                    assertEquals(WhatsAppStatusNotificacao.ENVIANDO, atual.getStatus(), "iteracao " + i);
                    assertNotNull(atual.getSendStartedAt(), "iteracao " + i);
                    assertTrue(atual.isQuotaReserved(), "iteracao " + i);
                    assertEquals(reservadosAntes, reservadosDepois, "iteracao " + i);
                } else {
                    // Cancelamento venceu: sem envio, sem reserva presa.
                    assertEquals(WhatsAppStatusNotificacao.CANCELADO, atual.getStatus(), "iteracao " + i);
                    assertFalse(atual.isQuotaReserved(), "iteracao " + i);
                    assertEquals(reservadosAntes, reservadosDepois, "iteracao " + i);
                }
            }
            verify(provider, never()).enviarTexto(any(), any(), any(), any());
        } finally {
            executor.shutdownNow();
        }
    }
}

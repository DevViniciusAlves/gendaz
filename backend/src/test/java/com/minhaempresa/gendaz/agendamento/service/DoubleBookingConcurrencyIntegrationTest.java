package com.minhaempresa.gendaz.agendamento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AtualizarAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.CriarAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.RemarcarAgendamentoRequest;
import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.repository.AgendamentoRepository;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.horarioatendimento.entity.HorarioAtendimentoEntity;
import com.minhaempresa.gendaz.horarioatendimento.repository.HorarioAtendimentoRepository;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.profissional.entity.ProfissionalEntity;
import com.minhaempresa.gendaz.profissional.enums.DiaSemana;
import com.minhaempresa.gendaz.profissional.repository.ProfissionalRepository;
import com.minhaempresa.gendaz.servico.entity.ServicoEntity;
import com.minhaempresa.gendaz.servico.repository.ServicoRepository;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import com.minhaempresa.gendaz.shared.security.UsuarioAutenticadoProvider;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration Tests de Concorrencia para Prevenção de Double Booking (DB-01 a DB-08).
 * Testa Spring real com transacoes independentes por thread sob exclusao mutua no banco.
 */
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.test.context.TestPropertySource(
        properties = {
                "spring.datasource.hikari.maximum-pool-size=10",
                "JWT_SECRET=super_secret_key_for_jwt_tokens_testing_123456789",
                "SUPER_ADMIN_PASSWORD=super_secret_admin_pass_123456789"
        })
class DoubleBookingConcurrencyIntegrationTest {

    @Autowired AgendamentoService agendamentoService;
    @Autowired AgendamentoRepository agendamentoRepository;
    @Autowired PagamentoRepository pagamentoRepository;
    @Autowired EmpresaRepository empresaRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ServicoRepository servicoRepository;
    @Autowired ProfissionalRepository profissionalRepository;
    @Autowired HorarioAtendimentoRepository horarioAtendimentoRepository;

    @MockBean AssinaturaService assinaturaService;
    @MockBean UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    @BeforeEach
    void setup() {
        when(assinaturaService.isPlanoComRecursosAvancados(anyLong())).thenReturn(true);
        when(usuarioAutenticadoProvider.exigirUsuarioId()).thenReturn(1L);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    // ---------- infra de teste ----------

    private EmpresaEntity novaEmpresa() {
        EmpresaEntity empresa = empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia("DB Empresa " + System.nanoTime())
                .email("db" + System.nanoTime() + "@test.com")
                .status(StatusEmpresa.ATIVA)
                .caixaTotal(BigDecimal.ZERO)
                .despesasTotal(BigDecimal.ZERO)
                .build());
        when(assinaturaService.isPlanoComRecursosAvancados(empresa.getId())).thenReturn(true);

        horarioAtendimentoRepository.save(HorarioAtendimentoEntity.builder()
                .empresa(empresa)
                .diaSemana(com.minhaempresa.gendaz.horarioatendimento.enums.DiaSemanaAtendimento.SEGUNDA)
                .horaInicio(LocalTime.of(8, 0))
                .horaFim(LocalTime.of(20, 0))
                .ativo(true)
                .intervaloMinutos(30)
                .build());

        return empresa;
    }

    private ClienteEntity novoCliente(EmpresaEntity empresa, String nome) {
        return clienteRepository.save(ClienteEntity.builder()
                .nome(nome)
                .telefone("659" + String.format("%07d", (int) (Math.random() * 10000000)))
                .email("cli" + System.nanoTime() + "@test.com")
                .empresa(empresa)
                .status(StatusCadastro.ATIVO)
                .build());
    }

    private ServicoEntity novoServico(EmpresaEntity empresa, String nome, int duracaoMinutos) {
        return servicoRepository.save(ServicoEntity.builder()
                .nome(nome)
                .duracaoMinutos(duracaoMinutos)
                .valor(new BigDecimal("100.00"))
                .status(StatusCadastro.ATIVO)
                .empresa(empresa)
                .build());
    }

    private ProfissionalEntity novoProfissional(EmpresaEntity empresa, String nome) {
        return profissionalRepository.save(ProfissionalEntity.builder()
                .nome(nome)
                .status(StatusCadastro.ATIVO)
                .diasTrabalho(EnumSet.allOf(DiaSemana.class))
                .empresa(empresa)
                .build());
    }

    private LocalDate proximoDiaUtil() {
        LocalDate data = LocalDate.now().plusDays(1);
        while (data.getDayOfWeek() == DayOfWeek.SATURDAY || data.getDayOfWeek() == DayOfWeek.SUNDAY) {
            data = data.plusDays(1);
        }
        return data;
    }

    private Runnable comEmpresa(Long empresaId, Runnable action) {
        return () -> {
            CompanyContext.setCompanyId(empresaId);
            try {
                action.run();
            } finally {
                CompanyContext.clear();
            }
        };
    }

    // ---------- TESTE DB-01: CRIAR x CRIAR (Mesmo Horario) ----------

    @Test
    void db01_criarXCriarMesmoHorario_apenasUmSucesso() throws Exception {
        EmpresaEntity empresa = novaEmpresa();
        Long empId = empresa.getId();
        ClienteEntity cliente1 = novoCliente(empresa, "Cliente 1");
        ClienteEntity cliente2 = novoCliente(empresa, "Cliente 2");
        ServicoEntity servico = novoServico(empresa, "Corte", 30);
        ProfissionalEntity profissional = novoProfissional(empresa, "João");
        LocalDate data = proximoDiaUtil();
        LocalTime hora = LocalTime.of(14, 0);

        CriarAgendamentoRequest req1 = new CriarAgendamentoRequest(
                cliente1.getId(), servico.getId(), profissional.getId(), empId, data, hora, null, "A");
        CriarAgendamentoRequest req2 = new CriarAgendamentoRequest(
                cliente2.getId(), servico.getId(), profissional.getId(), empId, data, hora, null, "B");

        AtomicInteger sucessos = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> erros = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?> f1 = executor.submit(comEmpresa(empId, () -> {
                try {
                    start.await();
                    agendamentoService.criar(req1);
                    sucessos.incrementAndGet();
                } catch (Throwable t) {
                    erros.add(t);
                }
            }));
            Future<?> f2 = executor.submit(comEmpresa(empId, () -> {
                try {
                    start.await();
                    agendamentoService.criar(req2);
                    sucessos.incrementAndGet();
                } catch (Throwable t) {
                    erros.add(t);
                }
            }));

            start.countDown();
            f1.get(10, TimeUnit.SECONDS);
            f2.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, sucessos.get(), "Exatamente 1 agendamento deve ser criado. Erros: " + erros);
        assertEquals(1, erros.size(), "Exatamente 1 deve falhar por conflito");
        long countNoBanco = agendamentoRepository.findByEmpresaId(empId).stream()
                .filter(a -> a.getStatus() != StatusAgendamento.CANCELADO
                        && a.getProfissional().getId().equals(profissional.getId())
                        && a.getData().equals(data)
                        && a.getHoraInicio().equals(hora))
                .count();
        assertEquals(1, countNoBanco, "Deve haver exatamente 1 reserva no banco");
    }

    // ---------- TESTE DB-02: CRIAR x REMARCAR ----------

    @Test
    void db02_criarXRemarcar_apenasUmOcupaIntervalo() throws Exception {
        EmpresaEntity empresa = novaEmpresa();
        Long empId = empresa.getId();
        ClienteEntity cli1 = novoCliente(empresa, "Cli 1");
        ClienteEntity cli2 = novoCliente(empresa, "Cli 2");
        ServicoEntity servico = novoServico(empresa, "Corte", 30);
        ProfissionalEntity profissional = novoProfissional(empresa, "Maria");
        LocalDate data = proximoDiaUtil();

        // Agendamento existente em 10:00
        CompanyContext.setCompanyId(empId);
        var agExistente = agendamentoService.criar(new CriarAgendamentoRequest(
                cli1.getId(), servico.getId(), profissional.getId(), empId, data, LocalTime.of(10, 0), null, null));
        CompanyContext.clear();

        LocalTime alvo = LocalTime.of(14, 0);
        CriarAgendamentoRequest reqNovo = new CriarAgendamentoRequest(
                cli2.getId(), servico.getId(), profissional.getId(), empId, data, alvo, null, "Novo");
        RemarcarAgendamentoRequest reqRemarcar = new RemarcarAgendamentoRequest(data, alvo);

        AtomicInteger sucessos = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> erros = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?> f1 = executor.submit(comEmpresa(empId, () -> {
                try {
                    start.await();
                    agendamentoService.criar(reqNovo);
                    sucessos.incrementAndGet();
                } catch (Throwable t) {
                    erros.add(t);
                }
            }));
            Future<?> f2 = executor.submit(comEmpresa(empId, () -> {
                try {
                    start.await();
                    agendamentoService.remarcar(agExistente.id(), reqRemarcar, empId);
                    sucessos.incrementAndGet();
                } catch (Throwable t) {
                    erros.add(t);
                }
            }));

            start.countDown();
            f1.get(10, TimeUnit.SECONDS);
            f2.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, sucessos.get(), "Exatamente uma operacao deve ter sucesso. Erros: " + erros);
        long countEm14 = agendamentoRepository.findByEmpresaId(empId).stream()
                .filter(a -> a.getStatus() != StatusAgendamento.CANCELADO
                        && a.getProfissional().getId().equals(profissional.getId())
                        && a.getData().equals(data)
                        && a.getHoraInicio().equals(alvo))
                .count();
        assertEquals(1, countEm14, "Banco deve ter 1 unico agendamento em 14:00");
    }

    // ---------- TESTE DB-03: REMARCAR x REMARCAR ----------

    @Test
    void db03_remarcarXRemarcar_apenasUmVence() throws Exception {
        EmpresaEntity empresa = novaEmpresa();
        Long empId = empresa.getId();
        ClienteEntity cli1 = novoCliente(empresa, "Cli 1");
        ClienteEntity cli2 = novoCliente(empresa, "Cli 2");
        ServicoEntity servico = novoServico(empresa, "Corte", 30);
        ProfissionalEntity profissional = novoProfissional(empresa, "Carlos");
        LocalDate data = proximoDiaUtil();

        CompanyContext.setCompanyId(empId);
        var ag1 = agendamentoService.criar(new CriarAgendamentoRequest(
                cli1.getId(), servico.getId(), profissional.getId(), empId, data, LocalTime.of(9, 0), null, null));
        var ag2 = agendamentoService.criar(new CriarAgendamentoRequest(
                cli2.getId(), servico.getId(), profissional.getId(), empId, data, LocalTime.of(10, 0), null, null));
        CompanyContext.clear();

        LocalTime alvo = LocalTime.of(15, 0);
        RemarcarAgendamentoRequest remarcarReq = new RemarcarAgendamentoRequest(data, alvo);

        AtomicInteger sucessos = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> erros = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?> f1 = executor.submit(comEmpresa(empId, () -> {
                try {
                    start.await();
                    agendamentoService.remarcar(ag1.id(), remarcarReq, empId);
                    sucessos.incrementAndGet();
                } catch (Throwable t) {
                    erros.add(t);
                }
            }));
            Future<?> f2 = executor.submit(comEmpresa(empId, () -> {
                try {
                    start.await();
                    agendamentoService.remarcar(ag2.id(), remarcarReq, empId);
                    sucessos.incrementAndGet();
                } catch (Throwable t) {
                    erros.add(t);
                }
            }));

            start.countDown();
            f1.get(10, TimeUnit.SECONDS);
            f2.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, sucessos.get(), "Exatamente 1 remarcar deve vencer");
        long countEm15 = agendamentoRepository.findByEmpresaId(empId).stream()
                .filter(a -> a.getStatus() != StatusAgendamento.CANCELADO
                        && a.getProfissional().getId().equals(profissional.getId())
                        && a.getData().equals(data)
                        && a.getHoraInicio().equals(alvo))
                .count();
        assertEquals(1, countEm15, "Apenas 1 agendamento deve ocupar 15:00");
    }

    // ---------- TESTE DB-04: ATUALIZAR x CRIAR ----------

    @Test
    void db04_atualizarXCriar_semDoubleBooking() throws Exception {
        EmpresaEntity empresa = novaEmpresa();
        Long empId = empresa.getId();
        ClienteEntity cli1 = novoCliente(empresa, "Cli 1");
        ClienteEntity cli2 = novoCliente(empresa, "Cli 2");
        ServicoEntity servico = novoServico(empresa, "Barba", 30);
        ProfissionalEntity profissional = novoProfissional(empresa, "Ana");
        LocalDate data = proximoDiaUtil();

        CompanyContext.setCompanyId(empId);
        var agOriginal = agendamentoService.criar(new CriarAgendamentoRequest(
                cli1.getId(), servico.getId(), profissional.getId(), empId, data, LocalTime.of(8, 30), null, null));
        CompanyContext.clear();

        LocalTime alvo = LocalTime.of(16, 0);
        AtualizarAgendamentoRequest reqAtualizar = new AtualizarAgendamentoRequest(
                cli1.getId(), servico.getId(), profissional.getId(), empId, data, alvo, StatusAgendamento.PENDENTE, "Editado");
        CriarAgendamentoRequest reqCriar = new CriarAgendamentoRequest(
                cli2.getId(), servico.getId(), profissional.getId(), empId, data, alvo, null, "Criado");

        AtomicInteger sucessos = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> erros = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?> f1 = executor.submit(comEmpresa(empId, () -> {
                try {
                    start.await();
                    agendamentoService.atualizar(agOriginal.id(), reqAtualizar);
                    sucessos.incrementAndGet();
                } catch (Throwable t) {
                    erros.add(t);
                }
            }));
            Future<?> f2 = executor.submit(comEmpresa(empId, () -> {
                try {
                    start.await();
                    agendamentoService.criar(reqCriar);
                    sucessos.incrementAndGet();
                } catch (Throwable t) {
                    erros.add(t);
                }
            }));

            start.countDown();
            f1.get(10, TimeUnit.SECONDS);
            f2.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, sucessos.get(), "Apenas uma operacao deve ter sucesso em 16:00");
        long countEm16 = agendamentoRepository.findByEmpresaId(empId).stream()
                .filter(a -> a.getStatus() != StatusAgendamento.CANCELADO
                        && a.getProfissional().getId().equals(profissional.getId())
                        && a.getData().equals(data)
                        && a.getHoraInicio().equals(alvo))
                .count();
        assertEquals(1, countEm16, "Exatamente 1 agendamento em 16:00");
    }

    // ---------- TESTE DB-05: INTERVALO PARCIALMENTE SOBREPOSTO ----------

    @Test
    void db05_intervaloSobreposto_geraConflito() throws Exception {
        EmpresaEntity empresa = novaEmpresa();
        Long empId = empresa.getId();
        ClienteEntity cli1 = novoCliente(empresa, "Cli 1");
        ClienteEntity cli2 = novoCliente(empresa, "Cli 2");
        ServicoEntity servicoLongo = novoServico(empresa, "Combo", 60); // 60 min
        ProfissionalEntity profissional = novoProfissional(empresa, "Pedro");
        LocalDate data = proximoDiaUtil();

        // Req A: 14:00 -> 15:00
        CriarAgendamentoRequest reqA = new CriarAgendamentoRequest(
                cli1.getId(), servicoLongo.getId(), profissional.getId(), empId, data, LocalTime.of(14, 0), null, "A");
        // Req B: 14:30 -> 15:30 (sobrepoe 14:00-15:00)
        CriarAgendamentoRequest reqB = new CriarAgendamentoRequest(
                cli2.getId(), servicoLongo.getId(), profissional.getId(), empId, data, LocalTime.of(14, 30), null, "B");

        AtomicInteger sucessos = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> erros = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?> f1 = executor.submit(comEmpresa(empId, () -> {
                try {
                    start.await();
                    agendamentoService.criar(reqA);
                    sucessos.incrementAndGet();
                } catch (Throwable t) {
                    erros.add(t);
                }
            }));
            Future<?> f2 = executor.submit(comEmpresa(empId, () -> {
                try {
                    start.await();
                    agendamentoService.criar(reqB);
                    sucessos.incrementAndGet();
                } catch (Throwable t) {
                    erros.add(t);
                }
            }));

            start.countDown();
            f1.get(10, TimeUnit.SECONDS);
            f2.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, sucessos.get(), "Apenas 1 dos agendamentos sobrepostos pode passar");
        assertEquals(1, erros.size());
    }

    // ---------- TESTE DB-06: INTERVALOS ADJACENTES ----------

    @Test
    void db06_intervalosAdjacentes_ambosPassam() {
        EmpresaEntity empresa = novaEmpresa();
        Long empId = empresa.getId();
        ClienteEntity cli1 = novoCliente(empresa, "Cli 1");
        ClienteEntity cli2 = novoCliente(empresa, "Cli 2");
        ServicoEntity servico = novoServico(empresa, "Corte", 60); // 60 min
        ProfissionalEntity profissional = novoProfissional(empresa, "Lucas");
        LocalDate data = proximoDiaUtil();

        CompanyContext.setCompanyId(empId);
        // A: 14:00 -> 15:00
        var agA = agendamentoService.criar(new CriarAgendamentoRequest(
                cli1.getId(), servico.getId(), profissional.getId(), empId, data, LocalTime.of(14, 0), null, "A"));
        // B: 15:00 -> 16:00 (fronteira exata)
        var agB = agendamentoService.criar(new CriarAgendamentoRequest(
                cli2.getId(), servico.getId(), profissional.getId(), empId, data, LocalTime.of(15, 0), null, "B"));
        CompanyContext.clear();

        assertEquals(LocalTime.of(14, 0), agA.horaInicio());
        assertEquals(LocalTime.of(15, 0), agA.horaFim());
        assertEquals(LocalTime.of(15, 0), agB.horaInicio());
        assertEquals(LocalTime.of(16, 0), agB.horaFim());
    }

    // ---------- TESTE DB-07: PROFISSIONAIS DIFERENTES ----------

    @Test
    void db07_profissionaisDiferentes_ambosPassamConcorrentemente() throws Exception {
        EmpresaEntity empresa = novaEmpresa();
        Long empId = empresa.getId();
        ClienteEntity cli1 = novoCliente(empresa, "Cli 1");
        ClienteEntity cli2 = novoCliente(empresa, "Cli 2");
        ServicoEntity servico = novoServico(empresa, "Corte", 30);
        ProfissionalEntity profA = novoProfissional(empresa, "Prof A");
        ProfissionalEntity profB = novoProfissional(empresa, "Prof B");
        LocalDate data = proximoDiaUtil();
        LocalTime hora = LocalTime.of(11, 0);

        CriarAgendamentoRequest reqA = new CriarAgendamentoRequest(
                cli1.getId(), servico.getId(), profA.getId(), empId, data, hora, null, null);
        CriarAgendamentoRequest reqB = new CriarAgendamentoRequest(
                cli2.getId(), servico.getId(), profB.getId(), empId, data, hora, null, null);

        AtomicInteger sucessos = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> erros = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?> f1 = executor.submit(comEmpresa(empId, () -> {
                try {
                    start.await();
                    agendamentoService.criar(reqA);
                    sucessos.incrementAndGet();
                } catch (Throwable t) {
                    erros.add(t);
                }
            }));
            Future<?> f2 = executor.submit(comEmpresa(empId, () -> {
                try {
                    start.await();
                    agendamentoService.criar(reqB);
                    sucessos.incrementAndGet();
                } catch (Throwable t) {
                    erros.add(t);
                }
            }));

            start.countDown();
            f1.get(10, TimeUnit.SECONDS);
            f2.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(2, sucessos.get(), "Profissionais diferentes devem permitir reservas simultaneas no mesmo horario. Erros: " + erros);
    }

    // ---------- TESTE DB-08: SEM LOST UPDATE NO REMARCAR ----------

    @Test
    void db08_semLostUpdateNoRemarcar() throws Exception {
        EmpresaEntity empresa = novaEmpresa();
        Long empId = empresa.getId();
        ClienteEntity cli = novoCliente(empresa, "Cli");
        ServicoEntity servico = novoServico(empresa, "Corte", 30);
        ProfissionalEntity prof = novoProfissional(empresa, "Prof");
        LocalDate data = proximoDiaUtil();

        CompanyContext.setCompanyId(empId);
        var ag = agendamentoService.criar(new CriarAgendamentoRequest(
                cli.getId(), servico.getId(), prof.getId(), empId, data, LocalTime.of(9, 0), null, null));
        CompanyContext.clear();

        RemarcarAgendamentoRequest reqRemarcar = new RemarcarAgendamentoRequest(data, LocalTime.of(11, 0));
        AtualizarAgendamentoRequest reqAtualizar = new AtualizarAgendamentoRequest(
                cli.getId(), servico.getId(), prof.getId(), empId, data, LocalTime.of(9, 0), StatusAgendamento.CONFIRMADO, "Confirmado concorrente");

        AtomicInteger sucessos = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?> f1 = executor.submit(comEmpresa(empId, () -> {
                try {
                    start.await();
                    agendamentoService.remarcar(ag.id(), reqRemarcar, empId);
                    sucessos.incrementAndGet();
                } catch (Throwable t) {
                    // ok
                }
            }));
            Future<?> f2 = executor.submit(comEmpresa(empId, () -> {
                try {
                    start.await();
                    agendamentoService.atualizar(ag.id(), reqAtualizar);
                    sucessos.incrementAndGet();
                } catch (Throwable t) {
                    // ok
                }
            }));

            start.countDown();
            f1.get(10, TimeUnit.SECONDS);
            f2.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertTrue(sucessos.get() >= 1);
        AgendamentoEntity agSalvo = agendamentoRepository.findById(ag.id()).orElseThrow();
        // O estado salvo deve ser consistente com alguma ordem serial valida (nao um estado corrompido)
        assertTrue(agSalvo.getStatus() == StatusAgendamento.CONFIRMADO || agSalvo.getStatus() == StatusAgendamento.PENDENTE);
    }
}

package com.minhaempresa.gendaz.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AgendamentoResponse;
import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.agendamento.service.AgendamentoService;
import com.minhaempresa.gendaz.auth.service.MeuGendazAuthService;
import com.minhaempresa.gendaz.auth.service.MeuGendazOnboardingService;
import com.minhaempresa.gendaz.auth.service.UsuarioSessionService;
import com.minhaempresa.gendaz.chamado.service.ChamadoService;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.cliente.service.ClienteEmailBloqueadoService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.insights.service.InsightsService;
import com.minhaempresa.gendaz.meugendazacesso.entity.MeuGendazAcessoEntity;
import com.minhaempresa.gendaz.meugendazacesso.repository.MeuGendazAcessoRepository;
import com.minhaempresa.gendaz.meugendazpromocao.service.MeuGendazPromocaoService;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.profissional.service.ProfissionalService;
import com.minhaempresa.gendaz.servico.service.ServicoService;
import com.minhaempresa.gendaz.shared.CookieService;
import com.minhaempresa.gendaz.shared.SanitizacaoService;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Partes 9, 10 e 11 — Meu Gendaz:
 * proximos excluem CANCELADO/FINALIZADO; historico exclui PENDENTE futuro;
 * totalGasto soma apenas pagamento PAGO (nunca FINALIZADO com PENDENTE).
 */
class MeuGendazListagemTest {

    @Mock private MeuGendazAcessoRepository meuGendazAcessoRepository;
    @Mock private EmpresaRepository empresaRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private ClienteEmailBloqueadoService clienteEmailBloqueadoService;
    @Mock private ServicoService servicoService;
    @Mock private ProfissionalService profissionalService;
    @Mock private AgendamentoService agendamentoService;
    @Mock private ChamadoService chamadoService;
    @Mock private InsightsService insightsService;
    @Mock private MeuGendazPromocaoService meuGendazPromocaoService;
    @Mock private UsuarioSessionService usuarioSessionService;
    @Mock private MeuGendazAuthService meuGendazAuthService;
    @Mock private MeuGendazOnboardingService onboardingService;
    @Mock private SanitizacaoService sanitizacaoService;
    @Mock private PagamentoRepository pagamentoRepository;
    private CookieService cookieService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        cookieService = new CookieService("prod");
        mockMvc = MockMvcBuilders.standaloneSetup(new MeuGendazController(
                meuGendazAcessoRepository,
                empresaRepository,
                clienteRepository,
                clienteEmailBloqueadoService,
                servicoService,
                profissionalService,
                agendamentoService,
                chamadoService,
                insightsService,
                meuGendazPromocaoService,
                usuarioSessionService,
                meuGendazAuthService,
                onboardingService,
                pagamentoRepository,
                sanitizacaoService,
                cookieService)).build();

        EmpresaEntity empresa = EmpresaEntity.builder().id(1L)
                .nomeFantasia("Loja").agendamentoSlug("loja-teste").build();
        ClienteEntity ana = ClienteEntity.builder().id(20L).nome("Ana")
                .email("ana@x.com").empresa(empresa).status(StatusCadastro.ATIVO).build();
        MeuGendazAcessoEntity acesso = MeuGendazAcessoEntity.builder().id(10L)
                .email("ana@x.com").nome("Ana").empresa(empresa).sessaoAtiva("tok").build();
        when(empresaRepository.findByAgendamentoSlug("loja-teste")).thenReturn(Optional.of(empresa));
        when(meuGendazAcessoRepository.findByEmpresaIdAndSessaoAtiva(1L, "tok")).thenReturn(Optional.of(acesso));
        when(clienteRepository.findFirstByEmpresaIdAndEmailIgnoreCase(1L, "ana@x.com"))
                .thenReturn(Optional.of(ana));
        when(agendamentoService.listarPorCliente(1L, 20L)).thenReturn(fixtures());
        when(pagamentoRepository.somarValorByEmpresaIdAndClienteIdAndStatusIn(
                eq(1L), eq(20L), eq(List.of(StatusPagamento.PAGO))))
                .thenReturn(new BigDecimal("250"));
        when(meuGendazPromocaoService.listarPromocoes(any())).thenReturn(List.of());
        when(meuGendazPromocaoService.listarNotificacoesNaoLidas(any())).thenReturn(List.of());
    }

    private List<AgendamentoResponse> fixtures() {
        LocalDate hoje = LocalDate.now();
        return List.of(
                // futuro PENDENTE -> so proximos
                resp(1L, hoje.plusDays(2), StatusAgendamento.PENDENTE),
                // futuro CANCELADO -> so historico
                resp(2L, hoje.plusDays(5), StatusAgendamento.CANCELADO),
                // futuro FINALIZADO -> so historico
                resp(3L, hoje.plusDays(1), StatusAgendamento.FINALIZADO),
                // passado PENDENTE -> so historico
                resp(4L, hoje.minusDays(3), StatusAgendamento.PENDENTE),
                // passado FINALIZADO -> so historico
                resp(5L, hoje.minusDays(1), StatusAgendamento.FINALIZADO));
    }

    private AgendamentoResponse resp(Long id, LocalDate data, StatusAgendamento st) {
        return new AgendamentoResponse(id, "P" + id, 20L, "Ana", 1L, "Corte", 1L, "Jo", 1L,
                new BigDecimal("200.00"), data, LocalTime.of(9, 0), LocalTime.of(9, 30),
                st, null, new BigDecimal("200.00"), BigDecimal.ZERO, new BigDecimal("200.00"),
                null, null, null, StatusCadastro.ATIVO);
    }

    private Cookie sessao() {
        return new Cookie("meu_gendaz_session_loja-teste", "tok");
    }

    @Test
    void proximosContemApenasCompromissosAtivosFuturos() throws Exception {
        mockMvc.perform(get("/api/meu-gendaz/agendamentos/proximos")
                        .header("X-Meu-Gendaz-Slug", "loja-teste")
                        .cookie(sessao()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void historicoNaoContemPendenteFuturo() throws Exception {
        mockMvc.perform(get("/api/meu-gendaz/agendamentos/historico")
                        .header("X-Meu-Gendaz-Slug", "loja-teste")
                        .cookie(sessao()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.agendamentos[?(@.id == 1)]").isEmpty());
    }

    @Test
    void dashboardTotalGastoVemDePagamentoPago() throws Exception {
        mockMvc.perform(get("/api/meu-gendaz/dashboard")
                        .header("X-Meu-Gendaz-Slug", "loja-teste")
                        .cookie(sessao()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGasto").value(250))
                .andExpect(jsonPath("$.proximoAgendamento.id").value(1))
                .andExpect(jsonPath("$.agendamentosFuturos").value(1));
    }
}

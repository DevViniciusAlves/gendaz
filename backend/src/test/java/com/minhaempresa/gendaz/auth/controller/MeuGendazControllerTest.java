package com.minhaempresa.gendaz.auth.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.gendaz.agendamento.service.AgendamentoService;
import com.minhaempresa.gendaz.chamado.service.ChamadoService;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.cliente.service.ClienteEmailBloqueadoService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.insights.service.InsightsService;
import com.minhaempresa.gendaz.meugendazacesso.entity.MeuGendazAcessoEntity;
import com.minhaempresa.gendaz.meugendazacesso.repository.MeuGendazAcessoRepository;
import com.minhaempresa.gendaz.meugendazpromocao.service.MeuGendazPromocaoService;
import com.minhaempresa.gendaz.profissional.service.ProfissionalService;
import com.minhaempresa.gendaz.servico.service.ServicoService;
import com.minhaempresa.gendaz.auth.service.UsuarioSessionService;
import com.minhaempresa.gendaz.auth.service.MeuGendazAuthService;
import com.minhaempresa.gendaz.auth.service.MeuGendazOnboardingService;
import com.minhaempresa.gendaz.shared.CookieService;
import com.minhaempresa.gendaz.shared.SanitizacaoService;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MeuGendazControllerTest {

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
                sanitizacaoService,
                cookieService
        )).build();

    }

    @Test
    void deveEncerrarSessaoELimparCookieNoLogout() throws Exception {
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).nomeFantasia("Empresa Teste").agendamentoSlug("gendaz-pro").build();

        MeuGendazAcessoEntity acesso = MeuGendazAcessoEntity.builder().id(10L).email("cliente@teste.com").nome("Cliente").empresa(empresa).sessaoAtiva("sessao-meu-gendaz").build();

        when(empresaRepository.findByAgendamentoSlug(anyString())).thenReturn(Optional.of(empresa));
        when(meuGendazAcessoRepository.findBySessaoAtiva(anyString())).thenReturn(Optional.of(acesso));


        mockMvc.perform(post("/api/meu-gendaz/auth/logout")
                        .header("X-Meu-Gendaz-Slug", "gendaz-pro")
                        .cookie(new jakarta.servlet.http.Cookie("meu_gendaz_session_gendaz-pro", "sessao-meu-gendaz")))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("meu_gendaz_session_gendaz-pro", 0));
    }

    @Test
    void deveLimparCookieERetornarOkQuandoSessaoNaoExisteMais() throws Exception {
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).nomeFantasia("Empresa Teste").agendamentoSlug("gendaz-pro").build();

        when(empresaRepository.findByAgendamentoSlug(anyString())).thenReturn(Optional.of(empresa));
        when(meuGendazAcessoRepository.findBySessaoAtiva(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/meu-gendaz/auth/logout")
                        .header("X-Meu-Gendaz-Slug", "gendaz-pro")
                        .cookie(new jakarta.servlet.http.Cookie("meu_gendaz_session_gendaz-pro", "sessao-meu-gendaz")))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("meu_gendaz_session_gendaz-pro", 0));
    }

    @Test
    void naoDeveEncerrarSessaoDeOutraEmpresaNoLogout() throws Exception {
        EmpresaEntity empresaRequest = EmpresaEntity.builder().id(1L).nomeFantasia("Empresa Teste").agendamentoSlug("gendaz-pro").build();
        EmpresaEntity outraEmpresa = EmpresaEntity.builder().id(2L).nomeFantasia("Outra Empresa").agendamentoSlug("outra-empresa").build();
        MeuGendazAcessoEntity acessoOutraEmpresa = MeuGendazAcessoEntity.builder().id(20L).email("cliente@teste.com").nome("Cliente").empresa(outraEmpresa).sessaoAtiva("sessao-meu-gendaz").build();

        when(empresaRepository.findByAgendamentoSlug(anyString())).thenReturn(Optional.of(empresaRequest));
        when(meuGendazAcessoRepository.findBySessaoAtiva(anyString())).thenReturn(Optional.of(acessoOutraEmpresa));

        mockMvc.perform(post("/api/meu-gendaz/auth/logout")
                        .header("X-Meu-Gendaz-Slug", "gendaz-pro")
                        .cookie(new jakarta.servlet.http.Cookie("meu_gendaz_session_gendaz-pro", "sessao-meu-gendaz")))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("meu_gendaz_session_gendaz-pro", 0));

        verify(usuarioSessionService, never()).encerrarSessaoMeuGendaz(20L, "sessao-meu-gendaz");
    }
}

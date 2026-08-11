package com.minhaempresa.agendapro.auth.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.agendapro.agendamento.service.AgendamentoService;
import com.minhaempresa.agendapro.chamado.service.ChamadoService;
import com.minhaempresa.agendapro.cliente.repository.ClienteRepository;
import com.minhaempresa.agendapro.cliente.service.ClienteEmailBloqueadoService;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.repository.EmpresaRepository;
import com.minhaempresa.agendapro.insights.service.InsightsService;
import com.minhaempresa.agendapro.meugendazacesso.entity.MeuGendazAcessoEntity;
import com.minhaempresa.agendapro.meugendazacesso.repository.MeuGendazAcessoRepository;
import com.minhaempresa.agendapro.meugendazpromocao.service.MeuGendazPromocaoService;
import com.minhaempresa.agendapro.profissional.service.ProfissionalService;
import com.minhaempresa.agendapro.servico.service.ServicoService;
import com.minhaempresa.agendapro.auth.service.UsuarioSessionService;
import com.minhaempresa.agendapro.shared.SanitizacaoService;
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
    @Mock private SanitizacaoService sanitizacaoService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
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
                sanitizacaoService
        )).build();
    }

    @Test
    void deveEncerrarSessaoELimparCookieNoLogout() throws Exception {
        EmpresaEntity empresa = EmpresaEntity.builder().id(1L).nomeFantasia("Empresa Teste").build();
        MeuGendazAcessoEntity acesso = MeuGendazAcessoEntity.builder().id(10L).email("cliente@teste.com").nome("Cliente").empresa(empresa).sessaoAtiva("sessao-meu-gendaz").build();

        when(empresaRepository.findByAgendamentoSlug(anyString())).thenReturn(Optional.of(empresa));
        when(meuGendazAcessoRepository.findByEmpresaIdAndSessaoAtiva(anyLong(), anyString())).thenReturn(Optional.of(acesso));

        mockMvc.perform(post("/api/meu-gendaz/auth/logout")
                        .header("X-Meu-Gendaz-Slug", "gendaz-pro")
                        .cookie(new jakarta.servlet.http.Cookie("meu_gendaz_session_gendaz-pro", "sessao-meu-gendaz")))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("meu_gendaz_session_gendaz-pro", 0));
    }
}

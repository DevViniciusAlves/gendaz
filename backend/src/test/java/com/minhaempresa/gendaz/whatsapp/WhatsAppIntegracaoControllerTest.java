package com.minhaempresa.gendaz.whatsapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import com.minhaempresa.gendaz.assinatura.repository.AssinaturaRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.plano.entity.PlanoEntity;
import com.minhaempresa.gendaz.plano.repository.PlanoRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.GlobalExceptionHandler;
import com.minhaempresa.gendaz.whatsapp.controller.WhatsAppIntegracaoController;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppConfiguracaoRepository;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppConfiguracaoService;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppQuotaService;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SpringBootTest
@ActiveProfiles("test")
class WhatsAppIntegracaoControllerTest {

    private static final AtomicLong SEQUENCIA = new AtomicLong();

    @MockBean
    private WhatsAppProvider provider;

    @Autowired
    private WhatsAppConfiguracaoService configuracaoService;
    @Autowired
    private WhatsAppQuotaService quotaService;
    @Autowired
    private EmpresaRepository empresaRepository;
    @Autowired
    private PlanoRepository planoRepository;
    @Autowired
    private AssinaturaRepository assinaturaRepository;
    @Autowired
    private WhatsAppConfiguracaoRepository configuracaoRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void montar() {
        when(provider.disponivel()).thenReturn(true);
        WhatsAppSessionStatus desconectado = new WhatsAppSessionStatus();
        desconectado.setCompanyId("0");
        desconectado.setState("DISCONNECTED");
        when(provider.consultarStatus(anyString()))
                .thenReturn(WhatsAppResult.success(desconectado));
        mockMvc = MockMvcBuilders.standaloneSetup(new WhatsAppIntegracaoController(
                        provider, configuracaoService, quotaService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void limparContexto() {
        CompanyContext.clear();
    }

    private EmpresaEntity novaEmpresa(String prefixo) {
        long seq = SEQUENCIA.incrementAndGet();
        return empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia(prefixo + " " + seq)
                .email(prefixo + "-" + seq + "-" + System.nanoTime() + "@teste.com")
                .status(StatusEmpresa.ATIVA)
                .build());
    }

    private void comAssinatura(EmpresaEntity empresa, String planoNome) {
        PlanoEntity plano = planoRepository.findByNome(planoNome).orElseThrow();
        LocalDate hoje = LocalDate.now();
        assinaturaRepository.save(AssinaturaEntity.builder()
                .empresa(empresa).plano(plano).status(StatusAssinatura.ATIVA)
                .dataInicio(hoje.minusDays(5)).dataFim(hoje.plusDays(25)).build());
    }

    private WhatsAppSessionStatus statusConectado(String companyId) {
        WhatsAppSessionStatus status = new WhatsAppSessionStatus();
        status.setCompanyId(companyId);
        status.setState("CONNECTED");
        status.setHasQr(false);
        status.setConnectedAt("2026-09-14T10:00:00Z");
        return status;
    }

    private void comoEmpresa(Long empresaId) {
        CompanyContext.setCompanyId(empresaId);
    }

    @Test
    void resumoConectadoTrazLimitesDoBackend() throws Exception {
        EmpresaEntity empresa = novaEmpresa("wpp-ui-sum");
        comAssinatura(empresa, "PRO");
        when(provider.consultarStatus(String.valueOf(empresa.getId())))
                .thenReturn(WhatsAppResult.success(statusConectado(String.valueOf(empresa.getId()))));
        comoEmpresa(empresa.getId());

        mockMvc.perform(get("/api/whatsapp/resumo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponivelNoPlano").value(true))
                .andExpect(jsonPath("$.conexao.estado").value("CONNECTED"))
                .andExpect(jsonPath("$.conexao.hasQr").value(false))
                .andExpect(jsonPath("$.configuracao.lembretesAtivos").value(false))
                .andExpect(jsonPath("$.uso.plano").value("PRO"))
                .andExpect(jsonPath("$.uso.lembretes.limite").value(150))
                .andExpect(jsonPath("$.uso.lembretes.enviados").value(0))
                .andExpect(jsonPath("$.uso.lembretes.reservados").value(0))
                .andExpect(jsonPath("$.uso.lembretes.disponiveis").value(150))
                .andExpect(jsonPath("$.uso.crm.limite").value(10));
    }

    @Test
    void resumoDesconectadoEProvedorIndisponivel() throws Exception {
        EmpresaEntity empresa = novaEmpresa("wpp-ui-disc");
        comAssinatura(empresa, "PLUS");
        comoEmpresa(empresa.getId());

        WhatsAppSessionStatus desconectado = statusConectado(String.valueOf(empresa.getId()));
        desconectado.setState("DISCONNECTED");
        when(provider.consultarStatus(String.valueOf(empresa.getId())))
                .thenReturn(WhatsAppResult.success(desconectado));
        mockMvc.perform(get("/api/whatsapp/resumo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conexao.estado").value("DISCONNECTED"))
                .andExpect(jsonPath("$.uso.lembretes.limite").value(300))
                .andExpect(jsonPath("$.uso.crm.limite").value(15));

        when(provider.consultarStatus(anyString()))
                .thenReturn(WhatsAppResult.erro(WhatsAppOperationStatus.UNAVAILABLE));
        mockMvc.perform(get("/api/whatsapp/resumo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conexao.estado").value("UNAVAILABLE"));
    }

    @Test
    void resumoSemProviderConfigurado() throws Exception {
        EmpresaEntity empresa = novaEmpresa("wpp-ui-ncfg");
        comAssinatura(empresa, "PRO");
        when(provider.disponivel()).thenReturn(false);
        comoEmpresa(empresa.getId());

        mockMvc.perform(get("/api/whatsapp/resumo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conexao.estado").value("NOT_CONFIGURED"));
    }

    @Test
    void resumoBasicoSemWhatsApp() throws Exception {
        EmpresaEntity empresa = novaEmpresa("wpp-ui-bas");
        comAssinatura(empresa, "BASICO");
        comoEmpresa(empresa.getId());

        mockMvc.perform(get("/api/whatsapp/resumo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponivelNoPlano").value(false))
                .andExpect(jsonPath("$.uso.lembretes.limite").value(0))
                .andExpect(jsonPath("$.uso.crm.limite").value(0));
    }

    @Test
    void limitesVemDoBackendPorPlano() throws Exception {
        for (String[] planoLimites : new String[][]{
                {"PRO", "150", "10"}, {"PLUS", "300", "15"}, {"ENTERPRISE", "500", "20"}}) {
            EmpresaEntity empresa = novaEmpresa("wpp-ui-" + planoLimites[0].toLowerCase());
            comAssinatura(empresa, planoLimites[0]);
            comoEmpresa(empresa.getId());
            mockMvc.perform(get("/api/whatsapp/resumo"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uso.lembretes.limite").value(Integer.parseInt(planoLimites[1])))
                    .andExpect(jsonPath("$.uso.crm.limite").value(Integer.parseInt(planoLimites[2])));
        }
    }

    @Test
    void connectProChamaProviderComCompanyIdDaSessao() throws Exception {
        EmpresaEntity empresa = novaEmpresa("wpp-ui-conn");
        comAssinatura(empresa, "PRO");
        when(provider.conectar(String.valueOf(empresa.getId())))
                .thenReturn(WhatsAppResult.success(statusConectado(String.valueOf(empresa.getId()))));
        comoEmpresa(empresa.getId());

        mockMvc.perform(post("/api/whatsapp/conectar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONNECTED"));
        verify(provider, times(1)).conectar(String.valueOf(empresa.getId()));
    }

    @Test
    void connectBasicoBloqueado() throws Exception {
        EmpresaEntity empresa = novaEmpresa("wpp-ui-connb");
        comAssinatura(empresa, "BASICO");
        comoEmpresa(empresa.getId());

        mockMvc.perform(post("/api/whatsapp/conectar"))
                .andExpect(status().isBadRequest());
        verify(provider, never()).conectar(anyString());
    }

    @Test
    void logoutUsaSessaoPropria() throws Exception {        EmpresaEntity empresa = novaEmpresa("wpp-ui-out");
        comAssinatura(empresa, "PRO");
        WhatsAppSessionStatus status = statusConectado(String.valueOf(empresa.getId()));
        status.setState("LOGGED_OUT");
        when(provider.logout(String.valueOf(empresa.getId())))
                .thenReturn(WhatsAppResult.success(status));
        comoEmpresa(empresa.getId());

        mockMvc.perform(post("/api/whatsapp/desconectar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("LOGGED_OUT"));
        verify(provider, times(1)).logout(String.valueOf(empresa.getId()));
    }

    @Test
    void desconectarBasicoComSessaoAtivaPermitido() throws Exception {
        EmpresaEntity empresa = novaEmpresa("wpp-ui-outb");
        comAssinatura(empresa, "BASICO");
        WhatsAppSessionStatus status = statusConectado(String.valueOf(empresa.getId()));
        status.setState("LOGGED_OUT");
        when(provider.logout(String.valueOf(empresa.getId())))
                .thenReturn(WhatsAppResult.success(status));
        comoEmpresa(empresa.getId());

        mockMvc.perform(post("/api/whatsapp/desconectar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("LOGGED_OUT"));
        verify(provider, times(1)).logout(String.valueOf(empresa.getId()));
    }

    @Test
    void qrSomentePropriaEmpresaESanitizado() throws Exception {
        EmpresaEntity empresa = novaEmpresa("wpp-ui-qr");
        comAssinatura(empresa, "PRO");
        WhatsAppQr qr = new WhatsAppQr();
        qr.setQr("QR-CONTEUDO");
        qr.setUpdatedAt("2026-09-14T10:00:00Z");
        when(provider.obterQr(String.valueOf(empresa.getId())))
                .thenReturn(WhatsAppResult.success(qr));
        comoEmpresa(empresa.getId());

        String corpo = mockMvc.perform(get("/api/whatsapp/qr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qr").value("QR-CONTEUDO"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(corpo.contains("token"));
        verify(provider, times(1)).obterQr(String.valueOf(empresa.getId()));
    }

    @Test
    void qrIndisponivelRespostaSanitizada() throws Exception {
        EmpresaEntity empresa = novaEmpresa("wpp-ui-qr404");
        comAssinatura(empresa, "PRO");
        when(provider.obterQr(anyString()))
                .thenReturn(WhatsAppResult.erro(WhatsAppOperationStatus.QR_UNAVAILABLE));
        comoEmpresa(empresa.getId());

        mockMvc.perform(get("/api/whatsapp/qr"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("qr_unavailable"));
    }

    @Test
    void empresaIdDoBrowserEIgnorado() throws Exception {
        EmpresaEntity empresaA = novaEmpresa("wpp-ui-tenant-a");
        EmpresaEntity empresaB = novaEmpresa("wpp-ui-tenant-b");
        comAssinatura(empresaA, "PRO");
        comAssinatura(empresaB, "ENTERPRISE");
        comoEmpresa(empresaA.getId());

        // Mesmo informando B, o resumo e sempre da empresa autenticada (A).
        mockMvc.perform(get("/api/whatsapp/resumo").param("empresaId", String.valueOf(empresaB.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uso.plano").value("PRO"))
                .andExpect(jsonPath("$.uso.lembretes.limite").value(150));
    }

    @Test
    void patchConfiguracaoCriaAlternaENaoMisturaEmpresas() throws Exception {
        EmpresaEntity empresaA = novaEmpresa("wpp-ui-cfg-a");
        EmpresaEntity empresaB = novaEmpresa("wpp-ui-cfg-b");
        comAssinatura(empresaA, "PRO");
        comAssinatura(empresaB, "PRO");

        comoEmpresa(empresaA.getId());
        mockMvc.perform(patch("/api/whatsapp/configuracao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lembretesAtivos\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lembretesAtivos").value(true));

        comoEmpresa(empresaB.getId());
        mockMvc.perform(get("/api/whatsapp/resumo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuracao.lembretesAtivos").value(false));

        mockMvc.perform(patch("/api/whatsapp/configuracao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lembretesAtivos\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/whatsapp/configuracao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lembretesAtivos\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lembretesAtivos").value(false));
    }

    @Test
    void patchTemplateSalvaPorEmpresaComFallbackPadrao() throws Exception {
        EmpresaEntity empresaA = novaEmpresa("wpp-ui-tpl-a");
        EmpresaEntity empresaB = novaEmpresa("wpp-ui-tpl-b");
        comAssinatura(empresaA, "PRO");
        comAssinatura(empresaB, "PRO");

        comoEmpresa(empresaA.getId());
        mockMvc.perform(get("/api/whatsapp/resumo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuracao.lembreteTemplate").doesNotExist())
                .andExpect(jsonPath("$.configuracao.lembreteTemplatePadrao")
                        .value(WhatsAppConfiguracaoService.DEFAULT_LEMBRETE_TEMPLATE));

        String template = "Oi, {cliente}! {empresa} lembra seu horario em {data} as {hora}.";
        mockMvc.perform(patch("/api/whatsapp/configuracao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lembreteTemplate\":\"" + template + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lembreteTemplate").value(template))
                .andExpect(jsonPath("$.lembreteTemplatePadrao")
                        .value(WhatsAppConfiguracaoService.DEFAULT_LEMBRETE_TEMPLATE));

        comoEmpresa(empresaB.getId());
        mockMvc.perform(get("/api/whatsapp/resumo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuracao.lembreteTemplate").doesNotExist());
    }

    @Test
    void patchTemplateRejeitaBlankEVariavelDesconhecida() throws Exception {
        EmpresaEntity empresa = novaEmpresa("wpp-ui-tpl-v");
        comAssinatura(empresa, "PRO");
        comoEmpresa(empresa.getId());

        mockMvc.perform(patch("/api/whatsapp/configuracao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lembreteTemplate\":\"   \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/whatsapp/configuracao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lembreteTemplate\":\"Oi {telefone}\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("A variavel {telefone} nao e suportada."));
    }

    @Test
    void patchConfiguracaoBasicoNaoAtiva() throws Exception {
        EmpresaEntity empresa = novaEmpresa("wpp-ui-cfgb");
        comAssinatura(empresa, "BASICO");
        comoEmpresa(empresa.getId());

        mockMvc.perform(patch("/api/whatsapp/configuracao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lembretesAtivos\":true}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/whatsapp/configuracao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lembretesAtivos\":false}"))
                .andExpect(status().isOk());
    }

    @Test
    void duasAtivacoesConcorrentesCriamUmaConfiguracao() throws Exception {
        EmpresaEntity empresa = novaEmpresa("wpp-ui-cfgr");
        comAssinatura(empresa, "PRO");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch largada = new CountDownLatch(1);
            List<Boolean> resultados = new CopyOnWriteArrayList<>();
            List<Throwable> erros = new CopyOnWriteArrayList<>();
            Future<?> f1 = executor.submit(() -> {
                try {
                    largada.await();
                    resultados.add(configuracaoService.definirLembretesAtivos(empresa.getId(), true));
                } catch (Throwable t) {
                    erros.add(t);
                }
            });
            Future<?> f2 = executor.submit(() -> {
                try {
                    largada.await();
                    resultados.add(configuracaoService.definirLembretesAtivos(empresa.getId(), true));
                } catch (Throwable t) {
                    erros.add(t);
                }
            });
            largada.countDown();
            f1.get(60, TimeUnit.SECONDS);
            f2.get(60, TimeUnit.SECONDS);

            assertTrue(erros.isEmpty());
            assertEquals(2, resultados.size());
            assertEquals(1, configuracaoRepository.findAll().stream()
                    .filter(c -> empresa.getId().equals(c.getEmpresa().getId()))
                    .count());
            assertTrue(configuracaoService.lembretesAtivos(empresa.getId()));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void resumoNaoExpoeToken() throws Exception {
        EmpresaEntity empresa = novaEmpresa("wpp-ui-leak");
        comAssinatura(empresa, "PRO");
        when(provider.consultarStatus(anyString()))
                .thenReturn(WhatsAppResult.success(statusConectado("x")));
        comoEmpresa(empresa.getId());

        String corpo = mockMvc.perform(get("/api/whatsapp/resumo"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()
                .toLowerCase();
        assertFalse(corpo.contains("token"));
        assertFalse(corpo.contains("sessions"));
        assertFalse(corpo.contains("signal"));
    }
}

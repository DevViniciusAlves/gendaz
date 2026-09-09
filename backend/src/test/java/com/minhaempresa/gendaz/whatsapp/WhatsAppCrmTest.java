package com.minhaempresa.gendaz.whatsapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import com.minhaempresa.gendaz.assinatura.repository.AssinaturaRepository;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.crm.dto.CrmDtos.EnviarMensagemRequest;
import com.minhaempresa.gendaz.crm.entity.CrmContatoEntity;
import com.minhaempresa.gendaz.crm.repository.CrmContatoRepository;
import com.minhaempresa.gendaz.crm.service.CrmService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.plano.entity.PlanoEntity;
import com.minhaempresa.gendaz.plano.repository.PlanoRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppNotificacaoEntity;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppStatusNotificacao;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppTipoNotificacao;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppNotificacaoRepository;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppEnvioWorker;
import com.minhaempresa.gendaz.whatsapp.service.WhatsAppQuotaService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WhatsAppCrmTest {

    private static final AtomicLong SEQUENCIA = new AtomicLong();

    @MockBean
    private WhatsAppProvider provider;

    @Autowired
    private CrmService crmService;
    @Autowired
    private WhatsAppEnvioWorker worker;
    @Autowired
    private WhatsAppQuotaService quotaService;
    @Autowired
    private WhatsAppNotificacaoRepository notificacaoRepository;
    @Autowired
    private CrmContatoRepository crmContatoRepository;
    @Autowired
    private EmpresaRepository empresaRepository;
    @Autowired
    private PlanoRepository planoRepository;
    @Autowired
    private AssinaturaRepository assinaturaRepository;
    @Autowired
    private ClienteRepository clienteRepository;

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

    private ClienteEntity novoCliente(EmpresaEntity empresa, String telefone) {
        long seq = SEQUENCIA.incrementAndGet();
        return clienteRepository.save(ClienteEntity.builder()
                .nome("Cliente CRM " + seq)
                .telefone(telefone)
                .email("crm" + seq + "-" + System.nanoTime() + "@x.com")
                .empresa(empresa).status(StatusCadastro.ATIVO).build());
    }

    private String chaveUnica(String prefixo) {
        return prefixo + "-" + SEQUENCIA.incrementAndGet() + "-" + System.nanoTime();
    }

    private EnviarMensagemRequest whatsapp(String template, String requestId) {
        return new EnviarMensagemRequest(template, "whatsapp", null, requestId);
    }

    /**
     * Isolamento da fila global: a tabela e compartilhada com outros testes
     * do mesmo contexto e o worker consome qualquer linha vencida. Limpa
     * antes e depois para nem herdar nem deixar restos.
     */
    @BeforeEach
    @AfterEach
    void limparFila() {
        notificacaoRepository.deleteAll();
    }

    private List<WhatsAppNotificacaoEntity> notificacoesDaEmpresa(Long empresaId) {
        return notificacaoRepository.findAll().stream()
                .filter(n -> empresaId.equals(n.getEmpresa().getId()))
                .toList();
    }

    private List<CrmContatoEntity> historicoDoCliente(Long clienteId) {
        return crmContatoRepository.findByClienteIdOrderByDataCriacaoDesc(clienteId);
    }

    @Test
    void resgateEmailPreservaComportamentoAntigo() {
        EmpresaEntity empresa = novaEmpresa("wpp-crm-mail");
        comAssinatura(empresa, "PRO");
        ClienteEntity cliente = novoCliente(empresa, "5565999999999");

        Map<String, Object> resultado = crmService.enviarMensagem(empresa.getId(), cliente.getId(),
                new EnviarMensagemRequest("resgate", "email", null, null));

        assertEquals("nao_entregue", resultado.get("status"));
        assertTrue(notificacoesDaEmpresa(empresa.getId()).isEmpty());
        List<CrmContatoEntity> historico = historicoDoCliente(cliente.getId());
        assertEquals(1, historico.size());
        assertEquals("email", historico.get(0).getTipo());
        assertEquals("resgate", historico.get(0).getTemplate());
    }

    @Test
    void reconexaoEmailPreservaComportamentoAntigo() {
        EmpresaEntity empresa = novaEmpresa("wpp-crm-mail2");
        comAssinatura(empresa, "PRO");
        ClienteEntity cliente = novoCliente(empresa, "5565999999999");

        Map<String, Object> resultado = crmService.enviarMensagem(empresa.getId(), cliente.getId(),
                new EnviarMensagemRequest("reconexao", null, null, null));

        assertEquals("nao_entregue", resultado.get("status"));
        assertTrue(notificacoesDaEmpresa(empresa.getId()).isEmpty());
        List<CrmContatoEntity> historico = historicoDoCliente(cliente.getId());
        assertEquals(1, historico.size());
        assertEquals("email", historico.get(0).getTipo());
        assertEquals("reconexao", historico.get(0).getTemplate());
    }

    @Test
    void resgateWhatsappCriaCrmResgate() {
        EmpresaEntity empresa = novaEmpresa("wpp-crm-resg");
        comAssinatura(empresa, "PRO");
        ClienteEntity cliente = novoCliente(empresa, "5565999999999");

        Map<String, Object> resultado = crmService.enviarMensagem(empresa.getId(), cliente.getId(),
                whatsapp("resgate", chaveUnica("req")));

        assertEquals(true, resultado.get("success"));
        assertEquals("solicitado", resultado.get("status"));
        List<WhatsAppNotificacaoEntity> notificacoes = notificacoesDaEmpresa(empresa.getId());
        assertEquals(1, notificacoes.size());
        WhatsAppNotificacaoEntity notificacao = notificacoes.get(0);
        assertEquals(WhatsAppTipoNotificacao.CRM_RESGATE, notificacao.getTipo());
        assertEquals(WhatsAppStatusNotificacao.PENDENTE, notificacao.getStatus());
        assertFalse(notificacao.isQuotaReserved());
        assertEquals(String.valueOf(notificacao.getId()), resultado.get("messageId"));
        assertNull(notificacao.getExpiresAt());

        List<CrmContatoEntity> historico = historicoDoCliente(cliente.getId());
        assertEquals(1, historico.size());
        assertEquals("whatsapp", historico.get(0).getTipo());
        assertEquals("resgate", historico.get(0).getTemplate());
        assertEquals("solicitado", historico.get(0).getStatus());
        assertEquals(notificacao.getMessageBody(), historico.get(0).getMensagem());
    }

    @Test
    void reconexaoWhatsappCriaCrmReconexao() {
        EmpresaEntity empresa = novaEmpresa("wpp-crm-rec");
        comAssinatura(empresa, "PLUS");
        ClienteEntity cliente = novoCliente(empresa, "5565999999999");

        Map<String, Object> resultado = crmService.enviarMensagem(empresa.getId(), cliente.getId(),
                whatsapp("reconexao", chaveUnica("req")));

        assertEquals(true, resultado.get("success"));
        List<WhatsAppNotificacaoEntity> notificacoes = notificacoesDaEmpresa(empresa.getId());
        assertEquals(1, notificacoes.size());
        assertEquals(WhatsAppTipoNotificacao.CRM_RECONEXAO, notificacoes.get(0).getTipo());
        List<CrmContatoEntity> historico = historicoDoCliente(cliente.getId());
        assertEquals(1, historico.size());
        assertEquals("whatsapp", historico.get(0).getTipo());
        assertEquals("reconexao", historico.get(0).getTemplate());
    }

    @Test
    void planosComESemWhatsApp() {
        for (String plano : List.of("PRO", "PLUS", "ENTERPRISE")) {
            EmpresaEntity empresa = novaEmpresa("wpp-crm-" + plano.toLowerCase());
            comAssinatura(empresa, plano);
            ClienteEntity cliente = novoCliente(empresa, "5565999999999");

            Map<String, Object> resultado = crmService.enviarMensagem(empresa.getId(), cliente.getId(),
                    whatsapp("resgate", chaveUnica("req")));

            assertEquals(true, resultado.get("success"), "plano " + plano);
            assertEquals(1, notificacoesDaEmpresa(empresa.getId()).size());
        }

        EmpresaEntity basica = novaEmpresa("wpp-crm-bas");
        comAssinatura(basica, "BASICO");
        ClienteEntity clienteBas = novoCliente(basica, "5565999999999");

        Map<String, Object> rejeitado = crmService.enviarMensagem(basica.getId(), clienteBas.getId(),
                whatsapp("resgate", chaveUnica("req")));

        assertEquals(false, rejeitado.get("success"));
        assertEquals("WHATSAPP_NAO_DISPONIVEL_NO_PLANO", rejeitado.get("status"));
        assertTrue(notificacoesDaEmpresa(basica.getId()).isEmpty());
        assertTrue(historicoDoCliente(clienteBas.getId()).isEmpty());
    }

    @Test
    void telefoneInvalidoOuAusenteNaoEnfileira() {
        // Nulo/blank nao persistem via entidade (@NotBlank na criacao);
        // o service rejeita qualquer nao-canonico pelo mesmo ramo, coberto
        // pelos casos invalidos abaixo.
        for (String telefone : new String[]{"abc", "1234567", "11988887777"}) {
            EmpresaEntity empresa = novaEmpresa("wpp-crm-tel");
            comAssinatura(empresa, "PRO");
            ClienteEntity cliente = novoCliente(empresa, telefone);

            Map<String, Object> resultado = crmService.enviarMensagem(empresa.getId(), cliente.getId(),
                    whatsapp("resgate", chaveUnica("req")));

            assertEquals(false, resultado.get("success"), "telefone=" + telefone);
            assertEquals("WHATSAPP_TELEFONE_INVALIDO", resultado.get("status"));
            assertTrue(notificacoesDaEmpresa(empresa.getId()).isEmpty());
        }
    }

    @Test
    void empresaANaoEnviaParaClienteDeOutraEmpresa() {
        EmpresaEntity empresaA = novaEmpresa("wpp-crm-ta");
        EmpresaEntity empresaB = novaEmpresa("wpp-crm-tb");
        comAssinatura(empresaA, "PRO");
        comAssinatura(empresaB, "PRO");
        ClienteEntity clienteB = novoCliente(empresaB, "5565999999999");

        assertThrows(BusinessException.class, () -> crmService.enviarMensagem(
                empresaA.getId(), clienteB.getId(), whatsapp("resgate", chaveUnica("req"))));
        assertTrue(notificacoesDaEmpresa(empresaA.getId()).isEmpty());
    }

    @Test
    void mesmoRequestIdNaoDuplicaERequestDiferentePermite() {
        EmpresaEntity empresa = novaEmpresa("wpp-crm-idem");
        comAssinatura(empresa, "PRO");
        ClienteEntity cliente = novoCliente(empresa, "5565999999999");
        String requestId = chaveUnica("req");

        Map<String, Object> primeira = crmService.enviarMensagem(empresa.getId(), cliente.getId(),
                whatsapp("resgate", requestId));
        Map<String, Object> segunda = crmService.enviarMensagem(empresa.getId(), cliente.getId(),
                whatsapp("resgate", requestId));

        assertEquals(primeira.get("messageId"), segunda.get("messageId"));
        assertEquals(1, notificacoesDaEmpresa(empresa.getId()).size());
        assertEquals(1, historicoDoCliente(cliente.getId()).size());

        crmService.enviarMensagem(empresa.getId(), cliente.getId(), whatsapp("resgate", chaveUnica("req")));
        assertEquals(2, notificacoesDaEmpresa(empresa.getId()).size());
    }

    @Test
    void mesmoRequestIdEmEmpresasDiferentesEIndependente() {
        EmpresaEntity empresaA = novaEmpresa("wpp-crm-ia");
        EmpresaEntity empresaB = novaEmpresa("wpp-crm-ib");
        comAssinatura(empresaA, "PRO");
        comAssinatura(empresaB, "PRO");
        ClienteEntity clienteA = novoCliente(empresaA, "5565999999999");
        ClienteEntity clienteB = novoCliente(empresaB, "5565999999999");

        crmService.enviarMensagem(empresaA.getId(), clienteA.getId(), whatsapp("resgate", "req-compartilhado"));
        crmService.enviarMensagem(empresaB.getId(), clienteB.getId(), whatsapp("resgate", "req-compartilhado"));

        assertEquals(1, notificacoesDaEmpresa(empresaA.getId()).size());
        assertEquals(1, notificacoesDaEmpresa(empresaB.getId()).size());
    }

    @Test
    void mesmoRequestIdResgateVsReconexaoSaoIndependentes() {
        EmpresaEntity empresa = novaEmpresa("wpp-crm-tipos");
        comAssinatura(empresa, "PRO");
        ClienteEntity cliente = novoCliente(empresa, "5565999999999");

        crmService.enviarMensagem(empresa.getId(), cliente.getId(), whatsapp("resgate", "req-mesmo"));
        crmService.enviarMensagem(empresa.getId(), cliente.getId(), whatsapp("reconexao", "req-mesmo"));

        List<WhatsAppNotificacaoEntity> notificacoes = notificacoesDaEmpresa(empresa.getId());
        assertEquals(2, notificacoes.size());
    }

    @Test
    void requestIdObrigatorioSomenteParaWhatsApp() {
        EmpresaEntity empresa = novaEmpresa("wpp-crm-rid");
        comAssinatura(empresa, "PRO");
        ClienteEntity cliente = novoCliente(empresa, "5565999999999");

        for (String requestId : new String[]{null, "   ", "com espaço", "x".repeat(65)}) {
            Map<String, Object> resultado = crmService.enviarMensagem(empresa.getId(), cliente.getId(),
                    whatsapp("resgate", requestId));
            assertEquals(false, resultado.get("success"));
            assertEquals("WHATSAPP_REQUEST_ID_INVALIDO", resultado.get("status"));
        }
        assertTrue(notificacoesDaEmpresa(empresa.getId()).isEmpty());

        Map<String, Object> email = crmService.enviarMensagem(empresa.getId(), cliente.getId(),
                new EnviarMensagemRequest("resgate", "email", null, null));
        assertEquals("nao_entregue", email.get("status"));
    }

    @Test
    void templateNaoSuportadoNoWhatsAppERejeitado() {
        EmpresaEntity empresa = novaEmpresa("wpp-crm-tpl");
        comAssinatura(empresa, "PRO");
        ClienteEntity cliente = novoCliente(empresa, "5565999999999");

        Map<String, Object> resultado = crmService.enviarMensagem(empresa.getId(), cliente.getId(),
                whatsapp("promocao", chaveUnica("req")));

        assertEquals(false, resultado.get("success"));
        assertEquals("WHATSAPP_TIPO_NAO_SUPORTADO", resultado.get("status"));
        assertTrue(notificacoesDaEmpresa(empresa.getId()).isEmpty());
    }

    @Test
    void mensagemTemClienteEEmpresaSemDadosInternos() {
        EmpresaEntity empresa = novaEmpresa("wpp-crm-msg");
        empresa.setNomeFantasia("Clínica Bella");
        empresaRepository.save(empresa);
        comAssinatura(empresa, "PRO");
        ClienteEntity cliente = novoCliente(empresa, "5565999999999");

        crmService.enviarMensagem(empresa.getId(), cliente.getId(), whatsapp("resgate", chaveUnica("req")));
        crmService.enviarMensagem(empresa.getId(), cliente.getId(), whatsapp("reconexao", chaveUnica("req")));

        List<WhatsAppNotificacaoEntity> notificacoes = notificacoesDaEmpresa(empresa.getId());
        assertEquals(2, notificacoes.size());
        for (WhatsAppNotificacaoEntity notificacao : notificacoes) {
            assertTrue(notificacao.getMessageBody().contains(cliente.getNome()));
            assertTrue(notificacao.getMessageBody().contains("Clínica Bella"));
            String lower = notificacao.getMessageBody().toLowerCase();
            assertFalse(lower.contains("risco"));
            assertFalse(lower.contains("cupom"));
            assertFalse(lower.contains("desconto"));
            assertFalse(lower.contains("pagamento"));
            assertFalse(lower.contains("observ"));
        }
    }

    @Test
    void horarioAgendadoEmUtcSemExpiracao() {
        EmpresaEntity empresa = novaEmpresa("wpp-crm-hora");
        comAssinatura(empresa, "PRO");
        ClienteEntity cliente = novoCliente(empresa, "5565999999999");
        LocalDateTime antes = LocalDateTime.now(ZoneOffset.UTC);

        crmService.enviarMensagem(empresa.getId(), cliente.getId(), whatsapp("resgate", chaveUnica("req")));

        WhatsAppNotificacaoEntity notificacao = notificacoesDaEmpresa(empresa.getId()).get(0);
        assertTrue(!notificacao.getScheduledAt().isBefore(antes));
        assertTrue(!notificacao.getScheduledAt().isAfter(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(2)));
        assertNull(notificacao.getExpiresAt());
    }

    @Test
    void enfileirarNaoConsomeWorkerReservaESucessoConsome() {
        EmpresaEntity empresa = novaEmpresa("wpp-crm-cota");
        comAssinatura(empresa, "PRO");
        ClienteEntity cliente = novoCliente(empresa, "5565999999999");
        when(provider.enviarTexto(any(), any(), any(), any()))
                .thenReturn(WhatsAppSendResult.sent("WAMID-CRM"));

        crmService.enviarMensagem(empresa.getId(), cliente.getId(), whatsapp("resgate", chaveUnica("req")));

        WhatsAppNotificacaoEntity enfileirada = notificacoesDaEmpresa(empresa.getId()).get(0);
        assertFalse(enfileirada.isQuotaReserved());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).crmReservados());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).crmEnviados());

        assertEquals(1, worker.processarLote(10));

        assertEquals(1, quotaService.consultarUso(empresa.getId()).crmEnviados());
        assertEquals(WhatsAppStatusNotificacao.ENVIADO,
                notificacaoRepository.findById(enfileirada.getId()).orElseThrow().getStatus());
        verify(provider, times(1)).enviarTexto(any(), any(), any(), any());
    }

    @Test
    void seisResgatesMaisQuatroReconexoesEsgotamCotaPro() {
        EmpresaEntity empresa = novaEmpresa("wpp-crm-shared");
        comAssinatura(empresa, "PRO");
        ClienteEntity cliente = novoCliente(empresa, "5565999999999");
        when(provider.enviarTexto(any(), any(), any(), any()))
                .thenReturn(WhatsAppSendResult.sent("WAMID-CRM"));

        for (int i = 0; i < 6; i++) {
            Map<String, Object> resultado = crmService.enviarMensagem(empresa.getId(), cliente.getId(),
                    whatsapp("resgate", chaveUnica("resgate")));
            assertEquals(true, resultado.get("success"));
        }
        for (int i = 0; i < 4; i++) {
            Map<String, Object> resultado = crmService.enviarMensagem(empresa.getId(), cliente.getId(),
                    whatsapp("reconexao", chaveUnica("reconexao")));
            assertEquals(true, resultado.get("success"));
        }
        assertEquals(10, worker.processarLote(10));
        assertEquals(10, quotaService.consultarUso(empresa.getId()).crmEnviados());

        crmService.enviarMensagem(empresa.getId(), cliente.getId(), whatsapp("resgate", chaveUnica("extra")));
        assertEquals(0, worker.processarLote(10));
        assertEquals(10, quotaService.consultarUso(empresa.getId()).crmEnviados());
        verify(provider, times(10)).enviarTexto(any(), any(), any(), any());

        WhatsAppNotificacaoEntity excedente = notificacoesDaEmpresa(empresa.getId()).stream()
                .filter(n -> n.getStatus() == WhatsAppStatusNotificacao.CANCELADO)
                .findFirst().orElseThrow();
        assertEquals("QUOTA_LIMIT_EXCEEDED", excedente.getLastError());
    }

    @Test
    void dezEmailsNaoConsomemCotaWhatsApp() {
        EmpresaEntity empresa = novaEmpresa("wpp-crm-mail10");
        comAssinatura(empresa, "PRO");
        ClienteEntity cliente = novoCliente(empresa, "5565999999999");

        for (int i = 0; i < 10; i++) {
            crmService.enviarMensagem(empresa.getId(), cliente.getId(),
                    new EnviarMensagemRequest("resgate", "email", null, null));
        }

        assertEquals(0, quotaService.consultarUso(empresa.getId()).crmEnviados());
        assertEquals(0, quotaService.consultarUso(empresa.getId()).crmReservados());
        assertEquals(10, quotaService.consultarUso(empresa.getId()).crmDisponiveis());
        assertTrue(notificacoesDaEmpresa(empresa.getId()).isEmpty());
    }
}

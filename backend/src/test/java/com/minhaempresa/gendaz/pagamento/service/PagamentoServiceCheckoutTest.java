package com.minhaempresa.gendaz.pagamento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhaempresa.gendaz.admin.service.AdminAuditService;
import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
import com.minhaempresa.gendaz.agendamento.service.AgendamentoService;
import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import com.minhaempresa.gendaz.assinatura.repository.AssinaturaRepository;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.service.ClienteService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.empresa.service.EmpresaService;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.MarcarPagamentoPagoRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.PagamentoPlanoResponse;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoPlanoEntity;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.gateway.PaymentGateway;
import com.minhaempresa.gendaz.pagamento.gateway.PaymentGatewayProperties;
import com.minhaempresa.gendaz.pagamento.gateway.PaymentGatewayResponse;
import com.minhaempresa.gendaz.pagamento.gateway.PaymentGatewayWebhook;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoPlanoCobrancaRepository;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoPlanoRepository;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.plano.entity.PlanoEntity;
import com.minhaempresa.gendaz.plano.service.PlanoService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PagamentoServiceCheckoutTest {

    @Mock
    private PagamentoRepository pagamentoRepository;
    @Mock
    private AgendamentoService agendamentoService;
    @Mock
    private ClienteService clienteService;
    @Mock
    private EmpresaService empresaService;
    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private PlanoService planoService;
    @Mock
    private AssinaturaService assinaturaService;
    @Mock
    private AssinaturaRepository assinaturaRepository;
    @Mock
    private PagamentoPlanoRepository pagamentoPlanoRepository;
    @Mock
    private PagamentoPlanoCobrancaRepository pagamentoPlanoCobrancaRepository;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private PaymentGatewayProperties paymentGatewayProperties;
    @Mock
    private AdminAuditService auditService;
    @Mock
    private FormaPagamentoEmpresaService formaPagamentoEmpresaService;
    @Mock
    private LogAtividadeService logAtividadeService;

    @InjectMocks
    private PagamentoService pagamentoService;

    @AfterEach
    void limparContexto() {
        CompanyContext.clear();
    }

    private EmpresaEntity empresa(Long id, StatusEmpresa status) {
        return EmpresaEntity.builder().id(id).nomeFantasia("Empresa " + id).status(status).build();
    }

    private PlanoEntity plano(Long id, String nome) {
        return PlanoEntity.builder().id(id).nome(nome).valorMensal(new BigDecimal("89.00")).build();
    }

    private PagamentoPlanoEntity pagamentoPendente(EmpresaEntity empresa, PlanoEntity plano, LocalDateTime expiracao, String sessionId, String checkoutUrl) {
        return PagamentoPlanoEntity.builder()
                .id(100L)
                .empresa(empresa)
                .plano(plano)
                .valor(plano.getValorMensal())
                .metodoPagamento(MetodoPagamento.CREDIT_CARD)
                .status(StatusPagamento.PAYMENT_PENDING)
                .provider("STRIPE")
                .providerPaymentId("pending-1")
                .paymentReference("AGE-PRO-ABC")
                .externalReference("AGE-PRO-ABC")
                .stripeSessionId(sessionId)
                .checkoutUrl(checkoutUrl)
                .dataExpiracao(expiracao)
                .build();
    }

    @Test
    void checkoutPendenteValidoDentroDoPrazoDeveSerReutilizadoSemCriarNovoCheckoutStripe() {
        CompanyContext.setCompanyId(1L);
        EmpresaEntity empresa = empresa(1L, StatusEmpresa.ATIVA);
        PlanoEntity plano = plano(2L, "PRO");
        PagamentoPlanoEntity pendente = pagamentoPendente(
                empresa, plano, LocalDateTime.now().plusMinutes(10), "cs_test_1", "https://checkout.stripe.com/c/pay_1");

        when(empresaRepository.findByIdWithLock(1L)).thenReturn(Optional.of(empresa));
        when(planoService.buscarPorNomePermitido("PRO")).thenReturn(plano);
        when(assinaturaService.buscarFilaAtiva(1L)).thenReturn(List.of());
        when(pagamentoPlanoRepository.findFirstByEmpresaIdAndPlanoIdAndStatusOrderByDataCriacaoDesc(
                1L, 2L, StatusPagamento.PAYMENT_PENDING)).thenReturn(Optional.of(pendente));

        PagamentoPlanoResponse resultado = pagamentoService.iniciarPagamentoPlano(1L, "PRO", MetodoPagamento.CREDIT_CARD);

        assertNotNull(resultado);
        assertEquals(100L, resultado.id());
        assertEquals("cs_test_1", resultado.stripeSessionId());
        verify(paymentGateway, never()).criarPagamentoPlano(any(PagamentoPlanoEntity.class));
        verify(paymentGateway, never()).criarPagamentoPlano(any(PagamentoPlanoEntity.class), any());
        verify(pagamentoPlanoRepository, never()).save(any(PagamentoPlanoEntity.class));
    }

    @Test
    void checkoutPendenteVencidoDeveExpirarESomenteNovoPedidoCriaNovaSession() {
        CompanyContext.setCompanyId(1L);
        EmpresaEntity empresa = empresa(1L, StatusEmpresa.ATIVA);
        PlanoEntity plano = plano(2L, "PRO");
        PagamentoPlanoEntity vencido = pagamentoPendente(
                empresa, plano, LocalDateTime.now().minusMinutes(1), "cs_test_expired", "https://checkout.stripe.com/c/pay_old");

        when(empresaRepository.findByIdWithLock(1L)).thenReturn(Optional.of(empresa));
        when(planoService.buscarPorNomePermitido("PRO")).thenReturn(plano);
        when(assinaturaService.buscarFilaAtiva(1L)).thenReturn(List.of());
        when(pagamentoPlanoRepository.findFirstByEmpresaIdAndPlanoIdAndStatusOrderByDataCriacaoDesc(
                1L, 2L, StatusPagamento.PAYMENT_PENDING)).thenReturn(Optional.of(vencido));
        when(pagamentoPlanoRepository.findById(100L)).thenReturn(Optional.of(vencido));
        when(paymentGateway.consultarPagamentoPlano(vencido)).thenReturn(Optional.empty());
        when(paymentGatewayProperties.getCheckout()).thenReturn(new PaymentGatewayProperties.CheckoutProperties());
        when(pagamentoPlanoRepository.save(any(PagamentoPlanoEntity.class))).thenAnswer(i -> i.getArguments()[0]);
        when(paymentGateway.criarPagamentoPlano(any(PagamentoPlanoEntity.class))).thenAnswer(i -> new PaymentGatewayResponse(
                "STRIPE", "cs_new", "AGE-PRO-NEW", "AGE-PRO-NEW", "https://checkout.stripe.com/c/pay_new", LocalDateTime.now().plusMinutes(15)));

        PagamentoPlanoResponse resultado = pagamentoService.iniciarPagamentoPlano(1L, "PRO", MetodoPagamento.CREDIT_CARD);

        // O checkout anterior foi expirado com seguranca...
        verify(paymentGateway).expirarCheckoutSession("cs_test_expired");
        // ... e so um novo pedido criou um novo checkout/session.
        verify(paymentGateway).criarPagamentoPlano(any(PagamentoPlanoEntity.class));
        assertNotNull(resultado);
        assertEquals("https://checkout.stripe.com/c/pay_new", resultado.checkoutUrl());
        assertEquals("cs_new", resultado.providerPaymentId());
    }

    @Test
    void corridaComTimeoutNaoDeveExpirarPagamentoJaConfirmadoNaStripe() {
        EmpresaEntity empresa = empresa(1L, StatusEmpresa.ATIVA);
        PlanoEntity plano = plano(2L, "PRO");
        PagamentoPlanoEntity pagamento = pagamentoPendente(
                empresa, plano, LocalDateTime.now().minusMinutes(1), "cs_test_ok", "https://checkout.stripe.com/c/pay_ok");
        AssinaturaEntity assinatura = AssinaturaEntity.builder().id(5L).empresa(empresa).plano(plano).status(StatusAssinatura.ATIVA).build();

        when(pagamentoPlanoRepository.findById(100L)).thenReturn(Optional.of(pagamento));
        when(paymentGateway.consultarPagamentoPlano(pagamento)).thenReturn(Optional.of(new PaymentGatewayWebhook(
                "evt_1", "cs_test_ok", "AGE-PRO-ABC", "AGE-PRO-ABC", StatusPagamento.PAYMENT_APPROVED, new BigDecimal("89.00"))));
        when(assinaturaService.ativarPlanoPago(empresa, plano, null)).thenReturn(assinatura);
        when(pagamentoPlanoRepository.save(any(PagamentoPlanoEntity.class))).thenAnswer(i -> i.getArguments()[0]);

        pagamentoService.expirarCheckoutPorTimeout(pagamento);

        assertEquals(StatusPagamento.PAYMENT_APPROVED, pagamento.getStatus());
        assertEquals(StatusEmpresa.ATIVA, pagamento.getEmpresa().getStatus());
        verify(paymentGateway, never()).expirarCheckoutSession("cs_test_ok");
    }

    @Test
    void pagamentoExpiradoNaoSincronizaComGatewayNoVerificarAutenticado() {
        CompanyContext.setCompanyId(1L);
        EmpresaEntity empresa = empresa(1L, StatusEmpresa.PENDENTE_PAGAMENTO);
        PlanoEntity plano = plano(2L, "PRO");
        PagamentoPlanoEntity expirado = pagamentoPendente(
                empresa, plano, LocalDateTime.now().minusDays(1), "cs_test_x", "https://checkout.stripe.com/c/pay_x");
        expirado.setStatus(StatusPagamento.PAYMENT_EXPIRED);
        expirado.setDataExpiracao(LocalDateTime.now().minusDays(1));

        when(pagamentoPlanoRepository.findByIdAndEmpresaId(100L, 1L)).thenReturn(Optional.of(expirado));

        var resposta = pagamentoService.verificarPagamentoPlano(1L, 100L);

        assertEquals("EXPIRED", resposta.statusVerificacao());
        // Evidencia da lacuna: o caminho autenticado nao re-consulta a Stripe para estados EXPIRED,
        // portanto um pagamento confirmado no limite e que tenha sido expirado nao e recuperado por aqui.
        verify(paymentGateway, never()).consultarPagamentoPlano(any(PagamentoPlanoEntity.class));
    }

    @Test
    void marcarPagoDeOutraEmpresaDeveFalharSemAlterarPagamento() {
        CompanyContext.setCompanyId(1L);
        when(pagamentoRepository.findByIdAndEmpresaId(100L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> pagamentoService.marcarPago(100L,
                        new MarcarPagamentoPagoRequest(MetodoPagamento.PIX, null)));

        verify(pagamentoRepository).findByIdAndEmpresaId(100L, 1L);
        verify(pagamentoRepository, never()).save(any(PagamentoEntity.class));
        verify(formaPagamentoEmpresaService, never()).validarPagamentoManual(anyLong(), any(), any());
    }

    @Test
    void buscarPagamentoDeveFalharSemCompanyContext() {
        assertThrows(RuntimeException.class, () -> pagamentoService.buscarEntidade(100L));
        verify(pagamentoRepository, never()).findByIdAndEmpresaId(anyLong(), anyLong());
    }

    @Test
    void consultaPendenteParaLoginUsaEmpresaValidadaSemExigirCompanyContext() {
        EmpresaEntity empresa = empresa(1L, StatusEmpresa.ATIVA);
        when(pagamentoPlanoRepository.findByEmpresaIdAndStatusOrderByDataCriacaoDesc(
                1L, StatusPagamento.PAYMENT_PENDING)).thenReturn(List.of());

        var resultado = pagamentoService.buscarUltimoPagamentoPlanoPendenteParaLogin(empresa);

        assertEquals(Optional.empty(), resultado);
        verify(pagamentoPlanoRepository).findByEmpresaIdAndStatusOrderByDataCriacaoDesc(
                1L, StatusPagamento.PAYMENT_PENDING);
    }

    @Test
    void consultaPendenteParaLoginRejeitaEmpresaSemIdentidadePersistida() {
        assertThrows(BusinessException.class,
                () -> pagamentoService.buscarUltimoPagamentoPlanoPendenteParaLogin(EmpresaEntity.builder().build()));
        verify(pagamentoPlanoRepository, never())
                .findByEmpresaIdAndStatusOrderByDataCriacaoDesc(anyLong(), any());
    }
}

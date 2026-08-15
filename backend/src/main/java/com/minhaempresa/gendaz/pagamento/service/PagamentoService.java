package com.minhaempresa.gendaz.pagamento.service;

import com.minhaempresa.gendaz.admin.service.AdminAuditService;
import com.minhaempresa.gendaz.agendamento.entity.AgendamentoEntity;
import com.minhaempresa.gendaz.agendamento.service.AgendamentoService;
import com.minhaempresa.gendaz.assinatura.dto.AssinaturaDtos.AssinaturaResponse;
import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.service.ClienteService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.empresa.service.EmpresaService;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.AtualizarStatusPagamentoRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.CriarPagamentoRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.IniciarPagamentoPlanoRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.MarcarPagamentoPagoRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.PagamentoPlanoResponse;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.PagamentoResponse;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.VerificarPagamentoPlanoResponse;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoPlanoCobrancaEntity;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoPlanoEntity;
import com.minhaempresa.gendaz.pagamento.entity.StripeWebhookEventEntity;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.gateway.PaymentGateway;
import com.minhaempresa.gendaz.pagamento.gateway.PaymentGatewayResponse;
import com.minhaempresa.gendaz.pagamento.gateway.PaymentGatewayWebhook;
import com.minhaempresa.gendaz.pagamento.mapper.PagamentoMapper;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoPlanoCobrancaRepository;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoPlanoRepository;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.pagamento.repository.StripeWebhookEventRepository;
import com.minhaempresa.gendaz.plano.entity.PlanoEntity;
import com.minhaempresa.gendaz.plano.service.PlanoService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PagamentoService {
    private final PagamentoRepository pagamentoRepository;
    private final AgendamentoService agendamentoService;
    private final ClienteService clienteService;
    private final EmpresaService empresaService;
    private final EmpresaRepository empresaRepository;
    private final PlanoService planoService;
    private final AssinaturaService assinaturaService;
    private final PagamentoPlanoRepository pagamentoPlanoRepository;
    private final PagamentoPlanoCobrancaRepository pagamentoPlanoCobrancaRepository;
    private final PaymentGateway paymentGateway;
    private final AdminAuditService auditService;
    private final FormaPagamentoEmpresaService formaPagamentoEmpresaService;
    private final PagamentoMapper mapper = new PagamentoMapper();


    @Transactional
    public PagamentoResponse criar(CriarPagamentoRequest request) {
        validarValor(request.valor());
        AgendamentoEntity agendamento = request.agendamentoId() == null ? null : agendamentoService.buscarEntidade(request.agendamentoId());
        ClienteEntity cliente = clienteService.buscarEntidade(request.clienteId());
        EmpresaEntity empresa = empresaService.buscarEntidade(request.empresaId());
        PagamentoEntity pagamento = PagamentoEntity.builder()
                .agendamento(agendamento)
                .cliente(cliente)
                .empresa(empresa)
                .valor(request.valor())
                .metodoPagamento(request.metodoPagamento())
                .status(StatusPagamento.PENDENTE)
                .build();
        return mapper.toResponse(pagamentoRepository.save(pagamento));
    }

    @Transactional(readOnly = true)
    public List<PagamentoResponse> listarPorEmpresa(Long empresaId) {
        return pagamentoRepository.findByEmpresaId(empresaId).stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public PagamentoResponse marcarPago(Long id, MarcarPagamentoPagoRequest request) {
        PagamentoEntity pagamento = buscarEntidade(id);
        formaPagamentoEmpresaService.validarPagamentoManual(pagamento.getEmpresa().getId(), request.metodoPagamento(), request.parcelas());
        MetodoPagamento metodo = formaPagamentoEmpresaService.normalizarMetodoManual(request.metodoPagamento());
        pagamento.setStatus(StatusPagamento.PAGO);
        pagamento.setMetodoPagamento(metodo);
        pagamento.setParcelas(formaPagamentoEmpresaService.normalizarParcelas(metodo, request.parcelas()));
        pagamento.setDataPagamento(LocalDateTime.now());
        return mapper.toResponse(pagamentoRepository.save(pagamento));
    }

    @Transactional
    public PagamentoResponse atualizarStatus(Long id, AtualizarStatusPagamentoRequest request) {
        PagamentoEntity pagamento = buscarEntidade(id);
        pagamento.setStatus(request.status());
        if (request.status() == StatusPagamento.PAGO) {
            if (pagamento.getMetodoPagamento() == null) {
                throw new BusinessException("Informe a forma de pagamento para marcar como pago.");
            }
            pagamento.setDataPagamento(LocalDateTime.now());
        } else {
            pagamento.setDataPagamento(null);
            pagamento.setMetodoPagamento(null);
            pagamento.setParcelas(null);
        }
        return mapper.toResponse(pagamentoRepository.save(pagamento));
    }

    @Transactional
    public PagamentoPlanoResponse iniciarPagamentoPlanoPro(IniciarPagamentoPlanoRequest request) {
        return iniciarPagamentoPlano(
                request.empresaId(),
                request.plano() == null ? "PRO" : request.plano(),
                request.metodoPagamento(),
                request.customerName(),
                request.customerEmail(),
                request.customerPhone(),
                request.customerDocType(),
                request.customerDocNumber(),
                request.antifraudProfilingAttemptReference()
        );
    }

    @Transactional
    public PagamentoPlanoResponse iniciarPagamentoPlanoPro(Long empresaId, MetodoPagamento metodoPagamento) {
        return iniciarPagamentoPlanoOnboarding(empresaId, "PRO", metodoPagamento, null, null, null, null, null, null);
    }

    /**
     * Caminho interno para onboarding que não exige CompanyContext.
     * Valida empresa/plano criados pelo próprio servidor.
     */
    @Transactional
    public PagamentoPlanoResponse iniciarPagamentoPlanoOnboarding(
            Long empresaId,
            String planoNome,
            MetodoPagamento metodoPagamento,
            String customerName,
            String customerEmail,
            String customerPhone,
            String customerDocType,
            String customerDocNumber,
            String antifraudProfilingAttemptReference
    ) {
        validarMetodoPagamentoPlano(metodoPagamento);
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa não encontrada."));
        PlanoEntity plano = planoService.buscarPorNomePermitido(normalizarPlano(planoNome));

        if (assinaturaService.buscarFilaAtiva(empresaId).size() >= 2) {
            throw new BusinessException("Você já possui 2 planos ativos. Aguarde um deles expirar para contratar novamente.");
        }

        PagamentoPlanoEntity pagamento = novoPagamentoPlano(
                empresa, plano, metodoPagamento, customerName, customerEmail, customerPhone,
                customerDocType, customerDocNumber, antifraudProfilingAttemptReference
        );
        pagamento = pagamentoPlanoRepository.save(pagamento);

        PaymentGatewayResponse gatewayResponse = paymentGateway.criarPagamentoPlano(pagamento);
        pagamento.setProvider(gatewayResponse.provider());
        pagamento.setProviderPaymentId(gatewayResponse.providerPaymentId());
        pagamento.setExternalReference(preferir(gatewayResponse.externalReference(), pagamento.getExternalReference()));
        pagamento.setPaymentReference(preferir(gatewayResponse.paymentReference(), pagamento.getPaymentReference()));
        pagamento.setCheckoutUrl(gatewayResponse.checkoutUrl());
        pagamento.setDataExpiracao(gatewayResponse.dataExpiracao());

        log.info("Checkout Stripe gerado: pagamento={}, empresa={}, plano={}, session={}",
                pagamento.getId(), empresaId, plano.getNome(), pagamento.getStripeSessionId());
        return mapper.toPlanoResponse(pagamentoPlanoRepository.save(pagamento));
    }

    @Transactional
    public PagamentoPlanoResponse iniciarPagamentoPlano(Long empresaId, String planoNome, MetodoPagamento metodoPagamento) {
        return iniciarPagamentoPlano(empresaId, planoNome, metodoPagamento, null, null, null, null, null, null);
    }

    @Transactional
    public PagamentoPlanoResponse iniciarPagamentoPlano(
            Long empresaId,
            String planoNome,
            MetodoPagamento metodoPagamento,
            String customerName,
            String customerEmail,
            String customerPhone,
            String customerDocType,
            String customerDocNumber,
            String antifraudProfilingAttemptReference
    ) {
        validarEmpresaAtual(empresaId);
        validarMetodoPagamentoPlano(metodoPagamento);
        EmpresaEntity empresa = empresaService.buscarEntidade(empresaId);
        PlanoEntity plano = planoService.buscarPorNomePermitido(normalizarPlano(planoNome));

        if (assinaturaService.buscarFilaAtiva(empresaId).size() >= 2) {
            throw new BusinessException("Voce ja possui 2 planos ativos. Aguarde um deles expirar para contratar novamente.");
        }

        PagamentoPlanoEntity pagamento = novoPagamentoPlano(
                empresa, plano, metodoPagamento, customerName, customerEmail, customerPhone,
                customerDocType, customerDocNumber, antifraudProfilingAttemptReference
        );
        pagamento = pagamentoPlanoRepository.save(pagamento);

        PaymentGatewayResponse gatewayResponse = paymentGateway.criarPagamentoPlano(pagamento);
        pagamento.setProvider(gatewayResponse.provider());
        pagamento.setProviderPaymentId(gatewayResponse.providerPaymentId());
        pagamento.setExternalReference(preferir(gatewayResponse.externalReference(), pagamento.getExternalReference()));
        pagamento.setPaymentReference(preferir(gatewayResponse.paymentReference(), pagamento.getPaymentReference()));
        pagamento.setCheckoutUrl(gatewayResponse.checkoutUrl());
        pagamento.setDataExpiracao(gatewayResponse.dataExpiracao());

        log.info("Checkout Stripe gerado: pagamento={}, empresa={}, plano={}, session={}",
                pagamento.getId(), empresaId, plano.getNome(), pagamento.getStripeSessionId());
        return mapper.toPlanoResponse(pagamentoPlanoRepository.save(pagamento));
    }

    @Transactional
    public PagamentoPlanoResponse criarPagamentoPlanoProPendente(Long empresaId, MetodoPagamento metodoPagamento) {
        return criarPagamentoPlanoPendente(empresaId, "PRO", metodoPagamento);
    }

    @Transactional
    public PagamentoPlanoResponse criarPagamentoPlanoPendente(Long empresaId, String planoNome, MetodoPagamento metodoPagamento) {
        validarEmpresaAtual(empresaId);
        validarMetodoPagamentoPlano(metodoPagamento);
        EmpresaEntity empresa = empresaService.buscarEntidade(empresaId);
        PlanoEntity plano = planoService.buscarPorNomePermitido(normalizarPlano(planoNome));
        PagamentoPlanoEntity pagamento = novoPagamentoPlano(empresa, plano, metodoPagamento, null, null, null, null, null, null);
        return mapper.toPlanoResponse(pagamentoPlanoRepository.save(pagamento));
    }

    @Transactional(readOnly = true)
    public List<PagamentoPlanoResponse> listarPagamentosPlano(Long empresaId) {
        validarEmpresaAtual(empresaId);
        return pagamentoPlanoRepository.findByEmpresaIdOrderByDataCriacaoDesc(empresaId).stream()
                .map(mapper::toPlanoResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<PagamentoPlanoResponse> buscarUltimoPagamentoPlanoPendente(Long empresaId) {
        validarEmpresaAtual(empresaId);
        return pagamentoPlanoRepository.findByEmpresaIdAndStatusOrderByDataCriacaoDesc(empresaId, StatusPagamento.PAYMENT_PENDING)
                .stream()
                .findFirst()
                .map(mapper::toPlanoResponse);
    }

    @Transactional(readOnly = true)
    public PagamentoPlanoResponse consultarPagamentoPlano(Long empresaId, Long pagamentoId) {
        validarEmpresaAtual(empresaId);
        return mapper.toPlanoResponse(pagamentoPlanoRepository.findByIdAndEmpresaId(pagamentoId, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento do plano nao encontrado.")));
    }

    @Transactional
    public VerificarPagamentoPlanoResponse verificarPagamentoPlano(Long empresaId, Long pagamentoId) {
        validarEmpresaAtual(empresaId);
        PagamentoPlanoEntity pagamento = pagamentoPlanoRepository.findByIdAndEmpresaId(pagamentoId, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Não encontramos um pagamento para esta conta."));
        if (pagamento.getStatus() == StatusPagamento.PAYMENT_PENDING) {
            pagamento = sincronizarPagamentoComGateway(pagamento);
        }
        if (pagamento.getStatus() == StatusPagamento.PAYMENT_APPROVED) {
            pagamento = liberarContaPorPagamentoAprovado(pagamento, "VERIFICACAO");
            pagamento = pagamentoPlanoRepository.save(pagamento);
        }
        AssinaturaEntity assinatura = pagamento.getAssinatura();
        PagamentoPlanoResponse pagamentoResponse = mapper.toPlanoResponse(pagamento);
        return switch (pagamento.getStatus()) {
            case PAYMENT_APPROVED -> new VerificarPagamentoPlanoResponse("APPROVED", "Pagamento aprovado! Sua conta foi liberada.", pagamento.getEmpresa().getStatus(), assinatura == null ? null : assinatura.getStatus(), pagamentoResponse);
            case PAYMENT_REJECTED -> new VerificarPagamentoPlanoResponse("REJECTED", "Pagamento recusado. Gere uma nova cobranca e tente novamente.", pagamento.getEmpresa().getStatus(), assinatura == null ? null : assinatura.getStatus(), pagamentoResponse);
            case PAYMENT_CANCELED -> new VerificarPagamentoPlanoResponse("CANCELED", "Pagamento cancelado. Gere uma nova cobranca para continuar.", pagamento.getEmpresa().getStatus(), assinatura == null ? null : assinatura.getStatus(), pagamentoResponse);
            case PAYMENT_EXPIRED -> new VerificarPagamentoPlanoResponse("EXPIRED", "Pagamento expirado. Gere uma nova cobranca para continuar.", pagamento.getEmpresa().getStatus(), assinatura == null ? null : assinatura.getStatus(), pagamentoResponse);
            default -> new VerificarPagamentoPlanoResponse("PENDING", "Pagamento ainda nao foi confirmado. Aguarde alguns minutos e tente novamente.", pagamento.getEmpresa().getStatus(), assinatura == null ? null : assinatura.getStatus(), pagamentoResponse);
        };
    }

    @Transactional
    public PagamentoPlanoEntity registrarCheckoutStripeConcluido(String stripeSessionId, String subscriptionId, String stripeCustomerId, Long pagamentoPlanoId, String paymentReference) {
        PagamentoPlanoEntity pagamento = localizarPagamentoStripe(stripeSessionId, pagamentoPlanoId, paymentReference)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento do plano nao encontrado."));
        pagamento.setProvider("STRIPE");
        pagamento.setProviderPaymentId(stripeSessionId);
        pagamento.setStripeSessionId(stripeSessionId);
        pagamento.setSubscriptionId(subscriptionId);
        pagamento.setStripeCustomerId(stripeCustomerId);
        aplicarStatusPagamentoPlano(pagamento, StatusPagamento.PAYMENT_APPROVED);
        return pagamentoPlanoRepository.save(pagamento);
    }

    @Transactional
    public Optional<PagamentoPlanoEntity> aplicarStatusPorSubscriptionStripe(String subscriptionId, StatusPagamento status) {
        if (subscriptionId == null || subscriptionId.isBlank()) {
            return Optional.empty();
        }
        return pagamentoPlanoRepository.findBySubscriptionId(subscriptionId)
                .map(pagamento -> {
                    if (pagamento.getStatus() == status) {
                        return pagamento;
                    }
                    aplicarStatusPagamentoPlano(pagamento, status);
                    return pagamentoPlanoRepository.save(pagamento);
                });
    }

    @Transactional
    public boolean eventoJaProcessado(String eventId) {
        return pagamentoPlanoRepository.existsByStripeEventId(eventId);
    }

    @Transactional
    public void processarInvoiceStripe(String eventId, String invoiceId, String subscriptionId, StatusPagamento status) {
        if (eventId == null || invoiceId == null || subscriptionId == null) {
            throw new BusinessException("Dados do evento Stripe inválidos.");
        }
        
        // Idempotência: verificar se o evento já foi processado
        if (pagamentoPlanoRepository.existsByStripeEventId(eventId)) {
            log.info("Evento Stripe já processado: eventId={}", eventId);
            return;
        }
        
        // Registrar evento para idempotência
        PagamentoPlanoEntity pagamento = pagamentoPlanoRepository.findBySubscriptionId(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento do plano não encontrado para subscriptionId: " + subscriptionId));
        
        pagamento.setStripeEventId(eventId);
        pagamento.setStripeInvoiceId(invoiceId);
        aplicarStatusPagamentoPlano(pagamento, status);
        pagamentoPlanoRepository.save(pagamento);
    }

    @Transactional
    public PagamentoPlanoResponse aprovarPagamentoManual(Long pagamentoId, String transacaoId) {
        PagamentoPlanoEntity pagamento = pagamentoPlanoRepository.findById(pagamentoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento do plano nao encontrado."));
        if (transacaoId != null && !transacaoId.isBlank()) {
            pagamento.setProviderPaymentId(transacaoId.trim());
        }
        aplicarStatusPagamentoPlano(pagamento, StatusPagamento.PAYMENT_APPROVED);
        return mapper.toPlanoResponse(pagamentoPlanoRepository.save(pagamento));
    }

    @Transactional
    public PagamentoPlanoResponse desaprovarPagamentoManual(Long pagamentoId, String transacaoId) {
        PagamentoPlanoEntity pagamento = pagamentoPlanoRepository.findById(pagamentoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento do plano nao encontrado."));
        if (transacaoId != null && !transacaoId.isBlank()) {
            pagamento.setProviderPaymentId(transacaoId.trim());
        }
        aplicarStatusPagamentoPlano(pagamento, StatusPagamento.PAYMENT_REJECTED);
        return mapper.toPlanoResponse(pagamentoPlanoRepository.save(pagamento));
    }

    @Transactional
    public AssinaturaResponse consultarPlanoAtual(Long empresaId) {
        validarEmpresaAtual(empresaId);
        return assinaturaService.buscarAtualResponsePorEmpresa(empresaId);
    }

    @Transactional(readOnly = true)
    public PagamentoEntity buscarEntidade(Long id) {
        return pagamentoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento nao encontrado."));
    }

    @Transactional(readOnly = true)
    public long contarPendentes(Long empresaId) {
        validarEmpresaAtual(empresaId);
        return pagamentoRepository.countByEmpresaIdAndStatus(empresaId, StatusPagamento.PENDENTE);
    }

    private Optional<PagamentoPlanoEntity> localizarPagamentoStripe(String stripeSessionId, Long pagamentoPlanoId, String paymentReference) {
        if (stripeSessionId != null && !stripeSessionId.isBlank()) {
            Optional<PagamentoPlanoEntity> porSession = pagamentoPlanoRepository.findByStripeSessionId(stripeSessionId)
                    .or(() -> pagamentoPlanoRepository.findByProviderPaymentId(stripeSessionId));
            if (porSession.isPresent()) return porSession;
        }
        if (pagamentoPlanoId != null) {
            Optional<PagamentoPlanoEntity> porId = pagamentoPlanoRepository.findById(pagamentoPlanoId);
            if (porId.isPresent()) return porId;
        }
        if (paymentReference != null && !paymentReference.isBlank()) {
            return pagamentoPlanoRepository.findByPaymentReference(paymentReference);
        }
        return Optional.empty();
    }

    private void validarEmpresaAtual(Long empresaId) {
        Long empresaContexto = CompanyContext.getCompanyId();
        if (empresaContexto != null && empresaId != null && !empresaContexto.equals(empresaId)) {
            throw new BusinessException("Empresa da sessão não corresponde ao recurso solicitado.");
        }
    }

    private void validarEmpresaOnboarding(Long empresaId) {
        // Bypass para onboarding: valida apenas se a empresa existe e foi criada recentemente
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa não encontrada."));
        if (empresa.getStatus() != StatusEmpresa.PENDENTE_PAGAMENTO && empresa.getStatus() != StatusEmpresa.ATIVA) {
            throw new BusinessException("Empresa não está em estado válido para onboarding.");
        }
    }

    private PagamentoPlanoEntity novoPagamentoPlano(
            EmpresaEntity empresa,
            PlanoEntity plano,
            MetodoPagamento metodoPagamento,
            String customerName,
            String customerEmail,
            String customerPhone,
            String customerDocType,
            String customerDocNumber,
            String antifraudProfilingAttemptReference
    ) {
        String paymentReference = gerarPaymentReference();
        return PagamentoPlanoEntity.builder()
                .empresa(empresa)
                .plano(plano)
                .valor(plano.getValorMensal())
                .metodoPagamento(metodoPagamento)
                .status(StatusPagamento.PAYMENT_PENDING)
                .provider("STRIPE")
                .providerPaymentId("pending-" + System.nanoTime())
                .paymentReference(paymentReference)
                .externalReference(paymentReference)
                .customerName(normalizarTextoOpcional(customerName))
                .customerEmail(normalizarTextoOpcional(customerEmail))
                .customerPhone(normalizarTextoOpcional(customerPhone))
                .customerDocType(normalizarTextoOpcional(customerDocType))
                .customerDocNumber(normalizarTextoOpcional(customerDocNumber))
                .antifraudReference(normalizarTextoOpcional(antifraudProfilingAttemptReference))
                .build();
    }

    private void validarValor(BigDecimal valor) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("O valor do pagamento deve ser maior que zero.");
        }
    }

    private void validarMetodoPagamentoPlano(MetodoPagamento metodoPagamento) {
        if (metodoPagamento != MetodoPagamento.CREDIT_CARD) {
            throw new BusinessException("Planos sao pagos pelo checkout seguro da Stripe com cartao de credito.");
        }
    }

    private String normalizarPlano(String planoNome) {
        String plano = planoNome == null ? "PRO" : planoNome.trim().toUpperCase(Locale.ROOT);
        if (!plano.equals("BASICO") && !plano.equals("PRO")) {
            throw new BusinessException("Plano invalido. Escolha BASICO ou PRO.");
        }
        return plano;
    }

    private void aplicarStatusPagamentoPlano(PagamentoPlanoEntity pagamento, StatusPagamento status) {
        if (status == StatusPagamento.PAYMENT_APPROVED) {
            liberarContaPorPagamentoAprovado(pagamento, "AUTOMATICO");
            return;
        }
        pagamento.setStatus(status);
        pagamento.setDataPagamento(null);
        rebaixarContaPorPagamento(pagamento, status);
    }

    private PagamentoPlanoEntity liberarContaPorPagamentoAprovado(PagamentoPlanoEntity pagamento, String origem) {
        EmpresaEntity empresa = pagamento.getEmpresa();
        AssinaturaEntity assinatura = pagamento.getAssinatura();
        
        // Regra absoluta: BLOQUEADA é soberana. Nenhum processo automático pode alterar StatusEmpresa.
        if (empresa.getStatus() == StatusEmpresa.BLOQUEADA) {
            log.warn("Conta BLOQUEADA não pode ser liberada automaticamente por pagamento aprovado. empresa={}, pagamento={}", empresa.getId(), pagamento.getId());
            return pagamento;
        }
        
        // Regra: conta ENCERRADA (LGPD) não pode ser reativada por webhook ou pagamento.
        if (empresa.getStatus() == StatusEmpresa.ENCERRADA) {
            log.warn("Conta ENCERRADA não pode ser reativada por pagamento aprovado. empresa={}, pagamento={}", empresa.getId(), pagamento.getId());
            return pagamento;
        }
        
        boolean mudou = pagamento.getStatus() != StatusPagamento.PAYMENT_APPROVED
                || pagamento.getDataPagamento() == null
                || empresa.getStatus() != StatusEmpresa.ATIVA
                || assinatura == null
                || assinatura.getStatus() != StatusAssinatura.ATIVA;

        assinatura = assinaturaService.ativarPlanoPago(empresa, pagamento.getPlano(), assinatura);
        pagamento.setAssinatura(assinatura);
        pagamento.setStatus(StatusPagamento.PAYMENT_APPROVED);
        if (pagamento.getDataPagamento() == null) {
            pagamento.setDataPagamento(LocalDateTime.now());
        }
        empresa.setStatus(StatusEmpresa.ATIVA);

        if (mudou) {
            registrarAuditoriaAutomatica(
                    "PAYMENT_APPROVED_" + origem,
                    empresa,
                    "Conta liberada apos pagamento aprovado. pagamento=" + pagamento.getId() + "; plano=" + pagamento.getPlano().getNome()
            );
            log.info("Conta liberada por pagamento aprovado: pagamento={}, empresa={}, plano={}, origem={}",
                    pagamento.getId(), empresa.getId(), pagamento.getPlano().getNome(), origem);
        }
        return pagamento;
    }

    private void rebaixarContaPorPagamento(PagamentoPlanoEntity pagamento, StatusPagamento status) {
        LocalDate hoje = LocalDate.now();
        AssinaturaEntity assinaturaRelacionada = pagamento.getAssinatura();
        if (assinaturaRelacionada != null
                && (status == StatusPagamento.PAYMENT_REJECTED
                || status == StatusPagamento.PAYMENT_CANCELED
                || status == StatusPagamento.PAYMENT_EXPIRED)) {
            assinaturaRelacionada.setStatus(StatusAssinatura.PENDENTE_PAGAMENTO);
            assinaturaRelacionada.setDataFim(hoje);
            pagamento.setAssinatura(assinaturaRelacionada);
        }

        boolean possuiVigenciaFutura = !assinaturaService.buscarFilaAtiva(pagamento.getEmpresa().getId()).isEmpty();
        pagamento.getEmpresa().setStatus(possuiVigenciaFutura ? StatusEmpresa.ATIVA : StatusEmpresa.PENDENTE_PAGAMENTO);
    }

    private PagamentoPlanoEntity sincronizarPagamentoComGateway(PagamentoPlanoEntity pagamento) {
        try {
            Optional<PaymentGatewayWebhook> webhook = paymentGateway.consultarPagamentoPlano(pagamento);
            if (webhook.isEmpty()) {
                return pagamento;
            }
            PaymentGatewayWebhook confirmado = webhook.get();
            aplicarStatusPagamentoPlano(pagamento, confirmado.status());
            return pagamentoPlanoRepository.save(pagamento);
        } catch (BusinessException ex) {
            log.warn("Consulta direta ao gateway nao confirmou pagamento {}: {}", pagamento.getId(), ex.getMessage());
            return pagamento;
        }
    }

    private void registrarAuditoriaAutomatica(String tipo, EmpresaEntity empresa, String descricao) {
        auditService.registrar(tipo, "INFO", null, null, empresa, descricao, null, null, null);
    }

    private String gerarPaymentReference() {
        return "AGE-PRO-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
    }

    private String preferir(String valorNovo, String valorAtual) {
        return valorNovo == null || valorNovo.isBlank() ? valorAtual : valorNovo;
    }

    private String normalizarTextoOpcional(String valor) {
        return valor == null ? null : valor.trim();
    }
}

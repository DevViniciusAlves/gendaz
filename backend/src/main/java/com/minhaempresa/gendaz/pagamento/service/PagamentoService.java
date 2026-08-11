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
import com.minhaempresa.gendaz.empresa.service.EmpresaService;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.AtualizarStatusPagamentoRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.CriarPagamentoRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.IniciarPagamentoPlanoRequest;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.PagamentoPlanoResponse;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.PagamentoResponse;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.VerificarPagamentoPlanoResponse;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.WebhookPagamentoPlanoRequest;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoEntity;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoPlanoEntity;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.gateway.PaymentGateway;
import com.minhaempresa.gendaz.pagamento.gateway.PaymentGatewayProperties;
import com.minhaempresa.gendaz.pagamento.gateway.PaymentGatewayResponse;
import com.minhaempresa.gendaz.pagamento.gateway.PaymentGatewayWebhook;
import com.minhaempresa.gendaz.pagamento.mapper.PagamentoMapper;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoPlanoRepository;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.plano.entity.PlanoEntity;
import com.minhaempresa.gendaz.plano.service.PlanoService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class PagamentoService {
    private final PagamentoRepository pagamentoRepository;
    private final AgendamentoService agendamentoService;
    private final ClienteService clienteService;
    private final EmpresaService empresaService;
    private final PlanoService planoService;
    private final AssinaturaService assinaturaService;
    private final PagamentoPlanoRepository pagamentoPlanoRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentGatewayProperties paymentGatewayProperties;
    private final AdminAuditService auditService;
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
    public PagamentoResponse marcarPago(Long id) {
        return atualizarStatus(id, new AtualizarStatusPagamentoRequest(StatusPagamento.PAGO));
    }

    @Transactional
    public PagamentoResponse atualizarStatus(Long id, AtualizarStatusPagamentoRequest request) {
        PagamentoEntity pagamento = buscarEntidade(id);
        pagamento.setStatus(request.status());
        pagamento.setDataPagamento(request.status() == StatusPagamento.PAGO ? LocalDateTime.now() : null);
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
        return iniciarPagamentoPlano(empresaId, "PRO", metodoPagamento, null, null, null, null, null, null);
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
        validarMetodoPagamentoPlano(metodoPagamento);
        EmpresaEntity empresa = empresaService.buscarEntidade(empresaId);
        PlanoEntity plano = planoService.buscarPorNomePermitido(normalizarPlano(planoNome));

        // Nova regra: permite ate 2 planos ativos simultaneos (fila de vigencia
        // futura). Quando ja existem 2, bloqueia nova cobranca ate um expirar.
        if (assinaturaService.buscarFilaAtiva(empresaId).size() >= 2) {
            throw new BusinessException("Voce ja possui 2 planos ativos. Aguarde um deles expirar para contratar novamente.");
        }

        PagamentoPlanoEntity pagamento = novoPagamentoPlano(
                empresa,
                plano,
                metodoPagamento,
                customerName,
                customerEmail,
                customerPhone,
                customerDocType,
                customerDocNumber,
                antifraudProfilingAttemptReference
        );
        pagamento = pagamentoPlanoRepository.save(pagamento);
        log.info("Pagamento de plano criado internamente: id={}, empresa={}, referencia={}",
                pagamento.getId(), empresaId, pagamento.getPaymentReference());

        PaymentGatewayResponse gatewayResponse = paymentGateway.criarPagamentoPlano(pagamento);
        pagamento.setProvider(gatewayResponse.provider());
        pagamento.setProviderPaymentId(gatewayResponse.providerPaymentId());
        pagamento.setExternalReference(preferir(gatewayResponse.externalReference(), pagamento.getExternalReference()));
        pagamento.setPaymentReference(preferir(gatewayResponse.paymentReference(), pagamento.getPaymentReference()));
        pagamento.setCheckoutUrl(gatewayResponse.checkoutUrl());
        pagamento.setPixCopiaECola(gatewayResponse.pixCopiaECola());
        pagamento.setPixQrCodeBase64(gatewayResponse.pixQrCodeBase64());
        pagamento.setDataExpiracao(gatewayResponse.dataExpiracao());
        log.info("Checkout gerado: pagamento={}, referencia={}, provider={}, checkoutPresente={}",
                pagamento.getId(), pagamento.getPaymentReference(), pagamento.getProvider(), pagamento.getCheckoutUrl() != null);

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
        pagamento = pagamentoPlanoRepository.save(pagamento);
        log.info("Pagamento pendente criado sem checkout: id={}, empresa={}, referencia={}",
                pagamento.getId(), empresaId, pagamento.getPaymentReference());
        return mapper.toPlanoResponse(pagamento);
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
        log.info("Verificacao de pagamento acionada: empresa={}, pagamento={}", empresaId, pagamentoId);
        PagamentoPlanoEntity pagamento = pagamentoPlanoRepository.findByIdAndEmpresaId(pagamentoId, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Nao encontramos um pagamento para esta conta."));
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
            case PAYMENT_APPROVED -> new VerificarPagamentoPlanoResponse(
                    "APPROVED",
                    "Pagamento aprovado! Sua conta Pro foi liberada.",
                    pagamento.getEmpresa().getStatus(),
                    assinatura == null ? null : assinatura.getStatus(),
                    pagamentoResponse
            );
            case PAYMENT_REJECTED -> new VerificarPagamentoPlanoResponse(
                    "REJECTED",
                    "Pagamento recusado. Gere uma nova cobranca e tente novamente.",
                    pagamento.getEmpresa().getStatus(),
                    assinatura == null ? null : assinatura.getStatus(),
                    pagamentoResponse
            );
            case PAYMENT_CANCELED -> new VerificarPagamentoPlanoResponse(
                    "CANCELED",
                    "Pagamento cancelado. Gere uma nova cobranca para continuar.",
                    pagamento.getEmpresa().getStatus(),
                    assinatura == null ? null : assinatura.getStatus(),
                    pagamentoResponse
            );
            case PAYMENT_EXPIRED -> new VerificarPagamentoPlanoResponse(
                    "EXPIRED",
                    "Pagamento expirado. Gere uma nova cobranca para continuar.",
                    pagamento.getEmpresa().getStatus(),
                    assinatura == null ? null : assinatura.getStatus(),
                    pagamentoResponse
            );
            default -> new VerificarPagamentoPlanoResponse(
                    "PENDING",
                    mensagemPagamentoPendente(pagamento),
                    pagamento.getEmpresa().getStatus(),
                    assinatura == null ? null : assinatura.getStatus(),
                    pagamentoResponse
            );
        };
    }

    @Transactional
    public PagamentoPlanoResponse processarWebhookPlano(WebhookPagamentoPlanoRequest request, String assinatura) {
        PaymentGatewayWebhook webhook = new PaymentGatewayWebhook(
                request.eventId(),
                request.providerPaymentId(),
                null,
                null,
                normalizarStatusGateway(request.status()),
                request.valor()
        );
        if (!paymentGateway.validarWebhook(assinatura, webhook)) {
            throw new BusinessException("Webhook de pagamento invalido.");
        }

        PagamentoPlanoEntity pagamento = pagamentoPlanoRepository.findByProviderPaymentId(webhook.providerPaymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento do plano nao encontrado."));
        validarValorWebhook(pagamento, webhook.valor());

        aplicarStatusPagamentoPlano(pagamento, webhook.status());
        return mapper.toPlanoResponse(pagamentoPlanoRepository.save(pagamento));
    }

    @Transactional
    public PagamentoPlanoResponse processarWebhookMercadoPago(String providerPaymentId, String assinatura, String requestId) {
        PaymentGatewayWebhook webhook = paymentGateway.consultarPagamentoWebhook(providerPaymentId, assinatura, requestId);
        PagamentoPlanoEntity pagamento = pagamentoPlanoRepository.findByPaymentReference(webhook.paymentReference())
                .or(() -> pagamentoPlanoRepository.findByExternalReference(webhook.externalReference()))
                .or(() -> pagamentoPlanoRepository.findByProviderPaymentId(webhook.providerPaymentId()))
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento do plano nao encontrado."));
        validarValorWebhook(pagamento, webhook.valor());

        pagamento.setProviderPaymentId(webhook.providerPaymentId());
        aplicarStatusPagamentoPlano(pagamento, webhook.status());
        return mapper.toPlanoResponse(pagamentoPlanoRepository.save(pagamento));
    }

    @Transactional
    public PagamentoPlanoResponse processarWebhookCakto(Map<String, Object> payload, String assinatura) {
        try {
            if (payload == null || payload.isEmpty()) {
                throw new BusinessException("Webhook da Cakto sem payload valido.");
            }
            String evento = texto(payload, "event", "event_id", "eventId", "type", "status");
            if (evento == null || evento.isBlank()) {
                throw new BusinessException("Webhook da Cakto sem evento valido.");
            }
            String paymentReference = texto(payload,
                    "payment_reference", "paymentReference", "reference", "reference_id", "referenceId",
                    "metadata.payment_reference", "metadata.paymentReference", "metadata.reference",
                    "data.payment_reference", "data.paymentReference", "data.reference");
            String externalReference = texto(payload,
                    "external_reference", "externalReference", "order_id", "orderId",
                    "metadata.external_reference", "metadata.externalReference",
                    "data.external_reference", "data.externalReference", "data.order_id", "data.orderId");
            String caktoRefId = texto(payload, "refId", "ref_id", "data.refId", "data.ref_id");
            String providerPaymentId = texto(payload,
                    "payment_id", "paymentId", "transaction_id", "transactionId", "transaction", "sale_id", "saleId",
                    "payment.id", "transaction.id", "sale.id", "order.id", "data.payment.id", "data.transaction.id", "data.sale.id", "data.id", "id");
            String statusTexto = texto(payload,
                    "status", "payment_status", "paymentStatus", "sale_status", "saleStatus",
                    "payment.status", "transaction.status", "sale.status", "data.status", "data.payment.status", "data.sale.status");
            String paymentMethodTexto = texto(payload,
                    "paymentMethod", "payment_method", "method", "data.paymentMethod", "data.payment_method", "data.method");
            String paidAtTexto = texto(payload, "paidAt", "paid_at", "approvedAt", "approved_at", "data.paidAt", "data.paid_at", "data.approvedAt", "data.approved_at");
            String emailComprador = texto(payload,
                    "customer.email", "customerEmail", "customer_email", "buyer.email", "buyerEmail", "buyer_email", "email",
                    "data.customer.email", "data.customerEmail", "data.customer_email", "data.buyer.email", "data.buyerEmail", "data.buyer_email");
            String customerName = texto(payload, "customer.name", "customerName", "data.customer.name", "data.customerName");
            String customerPhone = texto(payload, "customer.phone", "customerPhone", "data.customer.phone", "data.customerPhone");
            String customerDocType = texto(payload, "customer.docType", "customerDocType", "data.customer.docType", "data.customerDocType");
            String customerDocNumber = texto(payload, "customer.docNumber", "customerDocNumber", "data.customer.docNumber", "data.customerDocNumber");
            String pixCopiaECola = texto(payload, "pix.qrCode", "pixCopiaECola", "pix_copia_e_cola", "qrCode", "qr_code", "copyPaste", "copy_paste", "data.pix.qrCode", "data.pixCopiaECola", "data.qrCode", "data.qr_code");
            String checkoutUrl = texto(payload, "checkoutUrl", "checkout_url", "data.checkoutUrl", "data.checkout_url");
            String subscriptionId = texto(payload, "subscription.id", "subscriptionId", "subscription_id", "data.subscription.id", "data.subscriptionId", "data.subscription_id");
            BigDecimal valorBase = valorBaseWebhookCakto(payload);
            String eventoNormalizado = evento.trim().toLowerCase(Locale.ROOT);
            boolean eventoAprovado = "purchase_approved".equals(eventoNormalizado);
            boolean eventoPendente = "pix_gerado".equals(eventoNormalizado);
            log.info("Webhook Cakto recebido: evento={}, providerPaymentId={}, paymentReference={}, externalReference={}, refId={}, subscriptionId={}, emailFallback={}",
                    eventoNormalizado, providerPaymentId, paymentReference, externalReference, caktoRefId, subscriptionId, mascararEmail(emailComprador));

            if (!validarWebhookCakto(assinatura)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook da Cakto invalido.");
            }

            PaymentGatewayWebhook webhook = new PaymentGatewayWebhook(
                    evento,
                    providerPaymentId,
                    externalReference,
                    paymentReference,
                    normalizarStatusCakto(statusTexto),
                    valorBase
            );

            PagamentoPlanoEntity pagamento = localizarPagamentoCakto(webhook, payload, subscriptionId, caktoRefId).orElse(null);
            if (pagamento == null) {
                String motivo = motivoRejeicaoWebhookCakto(webhook, emailComprador, null);
                log.warn("Webhook Cakto ignorado: evento={}, motivo={}, providerPaymentId={}, paymentReference={}, externalReference={}, refId={}, subscriptionId={}, email={}",
                        evento, motivo, providerPaymentId, paymentReference, externalReference, caktoRefId, subscriptionId, mascararEmail(emailComprador));
                registrarAuditoriaAutomatica("PAYMENT_WEBHOOK_IGNORED", null, "Webhook da Cakto ignorado. Evento=" + evento + "; motivo=" + motivo);
                return null;
            }

            if (webhook.providerPaymentId() != null && !webhook.providerPaymentId().isBlank()) {
                pagamento.setProviderPaymentId(webhook.providerPaymentId());
            }
            if (webhook.externalReference() != null && !webhook.externalReference().isBlank()) {
                pagamento.setExternalReference(webhook.externalReference());
            }
            if (webhook.paymentReference() != null && !webhook.paymentReference().isBlank()) {
                pagamento.setPaymentReference(webhook.paymentReference());
            }
            if (caktoRefId != null && !caktoRefId.isBlank()) {
                pagamento.setCaktoRefId(caktoRefId);
            }
            if (subscriptionId != null && !subscriptionId.isBlank()) {
                pagamento.setSubscriptionId(subscriptionId);
            }
            if (customerName != null && !customerName.isBlank()) {
                pagamento.setCustomerName(customerName);
            }
            if (emailComprador != null && !emailComprador.isBlank()) {
                pagamento.setCustomerEmail(emailComprador);
            }
            if (customerPhone != null && !customerPhone.isBlank()) {
                pagamento.setCustomerPhone(customerPhone);
            }
            if (customerDocType != null && !customerDocType.isBlank()) {
                pagamento.setCustomerDocType(customerDocType);
            }
            if (customerDocNumber != null && !customerDocNumber.isBlank()) {
                pagamento.setCustomerDocNumber(customerDocNumber);
            }
            if (pixCopiaECola != null && !pixCopiaECola.isBlank()) {
                pagamento.setPixCopiaECola(pixCopiaECola);
            }
            String pixQrCodeBase64 = texto(payload, "pix.qrCodeBase64", "pix.qr_code_base64", "data.pix.qrCodeBase64", "data.pix.qr_code_base64");
            if (pixQrCodeBase64 != null && !pixQrCodeBase64.isBlank()) {
                pagamento.setPixQrCodeBase64(pixQrCodeBase64);
            }
            if (checkoutUrl != null && !checkoutUrl.isBlank()) {
                pagamento.setCheckoutUrl(checkoutUrl);
            }
            if (paymentMethodTexto != null && !paymentMethodTexto.isBlank()) {
                pagamento.setMetodoPagamento(normalizarMetodoCakto(paymentMethodTexto));
            } else if ("pix_gerado".equalsIgnoreCase(evento)) {
                pagamento.setMetodoPagamento(MetodoPagamento.PIX_AUTO);
            }
            pagamento.setProvider("CAKTO");

            if (eventoAprovado) {
                log.info("purchase_approved recebido");
                log.info("provider_payment_id recebido: {}", providerPaymentId);
                if (valorBase != null && !valorBaseWebhookConfere(pagamento, valorBase)) {
                    pagamento.setStatus(StatusPagamento.PAYMENT_PENDING);
                    pagamento.setDataPagamento(null);
                    log.warn("Webhook Cakto aprovado com valor divergente, mantendo pendente para revisao. pagamento={}, referencia={}, evento={}, esperado={}, recebido={}",
                            pagamento.getId(), pagamento.getPaymentReference(), evento, pagamento.getValor(), valorBase);
                    registrarAuditoriaAutomatica("PAYMENT_VALUE_MISMATCH", pagamento.getEmpresa(),
                            "Webhook da Cakto aprovado com valor divergente. pagamento=" + pagamento.getId()
                                    + "; esperado=" + pagamento.getValor()
                                    + "; recebido=" + valorBase);
                    return mapper.toPlanoResponse(persistirWebhookCaktoSeguro(pagamento));
                }
                StatusPagamento statusAntigo = pagamento.getStatus();
                pagamento.setStatus(StatusPagamento.PAYMENT_APPROVED);
                pagamento.setDataPagamento(parseDataPagamentoCakto(paidAtTexto));
                aplicarStatusPagamentoPlano(pagamento, StatusPagamento.PAYMENT_APPROVED);
                log.info("Status antigo: {}", statusAntigo);
                log.info("Status novo: {}", pagamento.getStatus());
                log.info("Data pagamento preenchida: {}", pagamento.getDataPagamento());
                log.info("Empresa liberada: {}", pagamento.getEmpresa().getId());
            } else {
                pagamento.setStatus(StatusPagamento.PAYMENT_PENDING);
                log.info("Webhook Cakto recebido e mantido pendente: pagamento={}, referencia={}, evento={}, status={}",
                        pagamento.getId(), pagamento.getPaymentReference(), evento, webhook.status());
                if (eventoPendente) {
                    registrarAuditoriaAutomatica("PAYMENT_WEBHOOK_PENDING", pagamento.getEmpresa(), "Webhook da Cakto recebido como pendente. Evento=" + evento);
                }
            }
            return mapper.toPlanoResponse(persistirWebhookCaktoSeguro(pagamento));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (BusinessException ex) {
            log.warn("Webhook Cakto ignorado por regra de negocio: {}", ex.getMessage());
            return null;
        } catch (RuntimeException ex) {
            log.error("Falha inesperada ao processar webhook Cakto", ex);
            return null;
        }
    }

    private BigDecimal valorBaseWebhookCakto(Map<String, Object> payload) {
        BigDecimal baseAmount = decimal(payload, "baseAmount", "base_amount", "data.baseAmount", "data.base_amount");
        if (baseAmount != null) {
            return baseAmount;
        }
        BigDecimal valor = decimal(payload, "amount", "value", "total", "price",
                "payment.amount", "transaction.amount", "sale.amount", "order.amount", "data.amount", "data.value", "data.total", "data.price");
        return valor;
    }

    private boolean valorBaseWebhookConfere(PagamentoPlanoEntity pagamento, BigDecimal valorWebhook) {
        if (valorWebhook == null) {
            log.warn("Webhook Cakto aprovado sem valor explicito. pagamento={}", pagamento.getId());
            return true;
        }
        if (pagamento.getValor().compareTo(valorWebhook) == 0) {
            return true;
        }
        BigDecimal valorEmCentavos = valorWebhook.movePointLeft(2);
        if (pagamento.getValor().compareTo(valorEmCentavos) == 0) {
            return true;
        }
        return false;
    }

    private boolean validarWebhookCakto(String assinatura) {
        String segredoEsperado = paymentGatewayProperties.getCaktoWebhookSecret();
        if (segredoEsperado == null || segredoEsperado.isBlank()) {
            log.warn("CAKTO_WEBHOOK_SECRET nao configurado. Webhook da Cakto sera rejeitado por seguranca.");
            return false;
        }
        if (assinatura == null || assinatura.isBlank()) {
            return false;
        }
        String recebido = normalizarWebhook(assinatura);
        return MessageDigest.isEqual(
                recebido.getBytes(StandardCharsets.UTF_8),
                segredoEsperado.getBytes(StandardCharsets.UTF_8)
        );
    }

    private MetodoPagamento normalizarMetodoCakto(String metodo) {
        String texto = metodo.trim().toLowerCase(Locale.ROOT);
        return switch (texto) {
            case "pix", "pix_auto", "pixauto" -> MetodoPagamento.PIX_AUTO;
            case "credit_card", "creditcard", "cartao", "cartÃ£o" -> MetodoPagamento.CREDIT_CARD;
            case "boleto" -> MetodoPagamento.BOLETO;
            default -> MetodoPagamento.OUTRO;
        };
    }

    private String normalizarWebhook(String valor) {
        String texto = valor.trim();
        if (texto.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return texto.substring(7).trim();
        }
        return texto;
    }

    @Transactional
    public PagamentoPlanoResponse aprovarPagamentoManual(Long pagamentoId, String transacaoId) {
        PagamentoPlanoEntity pagamento = pagamentoPlanoRepository.findById(pagamentoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento do plano nao encontrado."));
        if (transacaoId != null && !transacaoId.isBlank()) {
            pagamento.setProviderPaymentId(transacaoId.trim());
        }
        aplicarStatusPagamentoPlano(pagamento, StatusPagamento.PAYMENT_APPROVED);
        PagamentoPlanoEntity salvo = pagamentoPlanoRepository.save(pagamento);
        return mapper.toPlanoResponse(salvo);
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

    private void validarEmpresaAtual(Long empresaId) {
        Long empresaContexto = CompanyContext.getCompanyId();
        if (empresaContexto != null && empresaId != null && !empresaContexto.equals(empresaId)) {
            throw new BusinessException("Empresa da sessao nao corresponde ao recurso solicitado.");
        }
    }

    private PagamentoPlanoEntity novoPagamentoPlano(EmpresaEntity empresa, PlanoEntity plano, MetodoPagamento metodoPagamento) {
        return novoPagamentoPlano(empresa, plano, metodoPagamento, null, null, null, null, null, null);
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
                .provider("pending")
                .providerPaymentId("pending-" + System.nanoTime())
                .paymentReference(paymentReference)
                .externalReference(paymentReference)
                .customerName(normalizarTextoOpcional(customerName))
                .customerEmail(normalizarTextoOpcional(customerEmail))
                .customerPhone(normalizarTextoOpcional(customerPhone))
                .customerDocType(normalizarTextoOpcional(customerDocType))
                .customerDocNumber(normalizarTextoOpcional(customerDocNumber))
                .antifraudReference(normalizarTextoOpcional(antifraudProfilingAttemptReference))
                .caktoOfferId("PRO".equalsIgnoreCase(plano.getNome()) ? paymentGatewayProperties.getCaktoOfferProId() : null)
                .build();
    }

    private void validarValor(BigDecimal valor) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("O valor do pagamento deve ser maior que zero.");
        }
    }

    private void validarMetodoPagamentoPlano(MetodoPagamento metodoPagamento) {
        if (metodoPagamento != MetodoPagamento.PIX
                && metodoPagamento != MetodoPagamento.PIX_AUTO
                && metodoPagamento != MetodoPagamento.CREDIT_CARD) {
            throw new BusinessException("Plano PRO pode ser pago apenas por PIX automatico, PIX ou cartao de credito.");
        }
    }

    private String normalizarPlano(String planoNome) {
        String plano = planoNome == null ? "PRO" : planoNome.trim().toUpperCase();
        if (!plano.equals("BASICO") && !plano.equals("PRO")) {
            throw new BusinessException("Plano invalido. Escolha BASICO ou PRO.");
        }
        return plano;
    }

    private StatusPagamento normalizarStatusGateway(StatusPagamento status) {
        return switch (status) {
            case PAYMENT_PENDING, PAYMENT_APPROVED, PAYMENT_REJECTED, PAYMENT_CANCELED, PAYMENT_EXPIRED -> status;
            default -> throw new BusinessException("Status de pagamento do gateway invalido.");
        };
    }

    private void validarValorWebhook(PagamentoPlanoEntity pagamento, BigDecimal valorWebhook) {
        if (valorWebhook == null) {
            throw new BusinessException("Valor do pagamento nao confere.");
        }
        if (pagamento.getValor().compareTo(valorWebhook) == 0) {
            return;
        }
        BigDecimal valorEmCentavos = valorWebhook.movePointLeft(2);
        if (pagamento.getValor().compareTo(valorEmCentavos) == 0) {
            return;
        }
        throw new BusinessException("Valor do pagamento nao confere.");
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
        boolean mudou = pagamento.getStatus() != StatusPagamento.PAYMENT_APPROVED
                || pagamento.getDataPagamento() == null
                || empresa.getStatus() != StatusEmpresa.ATIVA
                || assinatura == null
                || assinatura.getStatus() != StatusAssinatura.ATIVA;

        // Nova regra: a assinatura e encadeada na fila de planos (ate 2 ativos),
        // sem cancelar o plano em vigor. Se a assinatura vinculada ao pagamento
        // for do mesmo plano, ela e reativada e reposicionada na fila.
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
            validarValorWebhook(pagamento, confirmado.valor());
            if (confirmado.providerPaymentId() != null && !confirmado.providerPaymentId().isBlank()) {
                pagamento.setProviderPaymentId(confirmado.providerPaymentId());
            }
            if (confirmado.externalReference() != null && !confirmado.externalReference().isBlank()) {
                pagamento.setExternalReference(confirmado.externalReference());
            }
            if (confirmado.paymentReference() != null && !confirmado.paymentReference().isBlank()) {
                pagamento.setPaymentReference(confirmado.paymentReference());
            }
            aplicarStatusPagamentoPlano(pagamento, confirmado.status());
            log.info("Pagamento sincronizado por consulta direta ao gateway: pagamento={}, status={}",
                    pagamento.getId(), confirmado.status());
            return persistirWebhookCaktoSeguro(pagamento);
        } catch (BusinessException ex) {
            log.warn("Consulta direta ao gateway nao confirmou pagamento {}: {}", pagamento.getId(), ex.getMessage());
            return pagamento;
        } catch (RuntimeException ex) {
            log.warn("Falha inesperada na consulta direta do pagamento {}: {}", pagamento.getId(), ex.getMessage());
            return pagamento;
        }
    }

    private Optional<PagamentoPlanoEntity> localizarPagamentoCakto(PaymentGatewayWebhook webhook, Map<String, Object> payload, String subscriptionId, String caktoRefId) {
        if (webhook.paymentReference() != null && !webhook.paymentReference().isBlank()) {
            Optional<PagamentoPlanoEntity> porPaymentReference = pagamentoPlanoRepository.findByPaymentReference(webhook.paymentReference());
            if (porPaymentReference.isPresent()) {
                log.info("Webhook Cakto vinculado por paymentReference={}", webhook.paymentReference());
                return porPaymentReference;
            }
        }
        if (webhook.externalReference() != null && !webhook.externalReference().isBlank()) {
            Optional<PagamentoPlanoEntity> porReferencia = pagamentoPlanoRepository.findByExternalReference(webhook.externalReference());
            if (porReferencia.isPresent()) {
                log.info("Webhook Cakto vinculado por externalReference={}", webhook.externalReference());
                return porReferencia;
            }
        }
        if (caktoRefId != null && !caktoRefId.isBlank()) {
            Optional<PagamentoPlanoEntity> porCaktoRef = pagamentoPlanoRepository.findByCaktoRefId(caktoRefId)
                    .or(() -> pagamentoPlanoRepository.findByPaymentReference(caktoRefId))
                    .or(() -> pagamentoPlanoRepository.findByExternalReference(caktoRefId));
            if (porCaktoRef.isPresent()) {
                log.info("Webhook Cakto vinculado por refId={}", caktoRefId);
                return porCaktoRef;
            }
        }
        if (subscriptionId != null && !subscriptionId.isBlank()) {
            Optional<PagamentoPlanoEntity> porSubscription = pagamentoPlanoRepository.findBySubscriptionId(subscriptionId);
            if (porSubscription.isPresent()) {
                log.info("Webhook Cakto vinculado por subscriptionId={}", subscriptionId);
                return porSubscription;
            }
        }
        if (webhook.providerPaymentId() != null && !webhook.providerPaymentId().isBlank()) {
            Optional<PagamentoPlanoEntity> porProvider = pagamentoPlanoRepository.findByProviderPaymentId(webhook.providerPaymentId());
            if (porProvider.isPresent()) {
                log.info("Webhook Cakto vinculado por providerPaymentId={}", webhook.providerPaymentId());
                return porProvider;
            }
        }
        return Optional.empty();
    }

    private String motivoRejeicaoWebhookCakto(PaymentGatewayWebhook webhook, String email, String productId) {
        boolean semReferencia = (webhook.paymentReference() == null || webhook.paymentReference().isBlank())
                && (webhook.externalReference() == null || webhook.externalReference().isBlank())
                && (webhook.providerPaymentId() == null || webhook.providerPaymentId().isBlank());
        if (semReferencia) {
            return "referencia ausente, nao foi possivel vincular pagamento pendente";
        }
        return "nenhum pagamento pendente encontrado para as referencias recebidas";
    }

    private String planoPorProduto(String productId) {
        if (productId == null || productId.isBlank()) return null;
        if (productId.equals(paymentGatewayProperties.getCaktoProductBasicoId())) return "BASICO";
        if (productId.equals(paymentGatewayProperties.getCaktoProductProId())) return "PRO";
        if (productId.equals(paymentGatewayProperties.getCaktoOfferProId())) return "PRO";
        String texto = productId.toUpperCase(Locale.ROOT);
        if (texto.contains("BASICO")) return "BASICO";
        if (texto.contains("PRO")) return "PRO";
        return null;
    }

    @SuppressWarnings("unchecked")
    private String texto(Map<String, Object> payload, String... chaves) {
        if (payload == null) return null;
        for (String chave : chaves) {
            if (chave.contains(".")) {
                String[] partes = chave.split("\\.");
                Object atual = payload;
                for (String parte : partes) {
                    if (!(atual instanceof Map<?, ?> map)) {
                        atual = null;
                        break;
                    }
                    atual = ((Map<String, Object>) map).get(parte);
                }
                if (atual != null) return String.valueOf(atual);
                continue;
            }
            Object valor = payload.get(chave);
            if (valor != null) return String.valueOf(valor);
        }
        for (String nested : List.of("data", "customer", "payment", "transaction", "sale", "product", "offer", "metadata")) {
            Object valor = payload.get(nested);
            if (valor instanceof Map<?, ?> map) {
                String encontrado = texto((Map<String, Object>) map, chaves);
                if (encontrado != null) return encontrado;
            }
        }
        return null;
    }

    private BigDecimal decimal(Map<String, Object> payload, String... chaves) {
        String valor = texto(payload, chaves);
        if (valor == null || valor.isBlank()) return null;
        try {
            return new BigDecimal(valor.replace(",", "."));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private StatusPagamento normalizarStatusCakto(String status) {
        return switch (status == null ? "" : status.trim().toLowerCase(Locale.ROOT)) {
            case "approved", "paid", "completed", "active", "aprovado", "pago", "purchase_approved" -> StatusPagamento.PAYMENT_APPROVED;
            case "rejected", "refused", "declined", "recusado" -> StatusPagamento.PAYMENT_REJECTED;
            case "cancelled", "canceled", "cancelado" -> StatusPagamento.PAYMENT_CANCELED;
            case "expired", "expirado" -> StatusPagamento.PAYMENT_EXPIRED;
            default -> StatusPagamento.PAYMENT_PENDING;
        };
    }

    private String gerarPaymentReference() {
        return "AGE-PRO-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
    }

    private String mensagemPagamentoPendente(PagamentoPlanoEntity pagamento) {
        return "Pagamento ainda nao foi confirmado. Aguarde alguns minutos e tente novamente.";
    }

    private LocalDateTime parseDataPagamentoCakto(String valor) {
        if (valor == null || valor.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return OffsetDateTime.parse(valor).toLocalDateTime();
        } catch (RuntimeException ex) {
            try {
                return LocalDateTime.parse(valor);
            } catch (RuntimeException ignored) {
                return LocalDateTime.now();
            }
        }
    }

    private PagamentoPlanoEntity persistirWebhookCaktoSeguro(PagamentoPlanoEntity pagamento) {
        try {
            PagamentoPlanoEntity salvo = pagamentoPlanoRepository.saveAndFlush(pagamento);
            log.info("Webhook Cakto persistido com sucesso: pagamento={}, status={}, providerPaymentId={}, refId={}, subscriptionId={}",
                    salvo.getId(), salvo.getStatus(), salvo.getProviderPaymentId(), salvo.getCaktoRefId(), salvo.getSubscriptionId());
            return salvo;
        } catch (RuntimeException ex) {
            log.error("Falha ao persistir webhook Cakto para pagamento {}: {}", pagamento.getId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    private void registrarAuditoriaAutomatica(String tipo, EmpresaEntity empresa, String descricao) {
        auditService.registrar(tipo, "INFO", null, null, empresa, descricao, null, null, null);
    }

    private String preferir(String valorNovo, String valorAtual) {
        return valorNovo == null || valorNovo.isBlank() ? valorAtual : valorNovo;
    }

    private String normalizarTextoOpcional(String valor) {
        return valor == null ? null : valor.trim();
    }

    private String mascararEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return "***";
        }
        String[] partes = email.split("@", 2);
        String local = partes[0];
        String dominio = partes[1];
        String visivel = local.isBlank() ? "***" : local.length() <= 2 ? local.charAt(0) + "*" : local.substring(0, 2) + "***";
        return visivel + "@" + dominio;
    }
}


package com.minhaempresa.gendaz.pagamento.service;

import com.minhaempresa.gendaz.admin.service.AdminAuditService;
import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
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
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.gateway.PaymentGateway;
import com.minhaempresa.gendaz.pagamento.gateway.PaymentGatewayProperties;
import com.minhaempresa.gendaz.pagamento.gateway.PaymentGatewayResponse;

import com.minhaempresa.gendaz.pagamento.gateway.PaymentGatewayWebhook;
import com.minhaempresa.gendaz.pagamento.mapper.PagamentoMapper;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoPlanoCobrancaRepository;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoPlanoRepository;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoRepository;
import com.minhaempresa.gendaz.plano.entity.PlanoEntity;
import com.minhaempresa.gendaz.plano.service.PlanoService;
import com.minhaempresa.gendaz.financeiro.caixadespesas.service.CaixaDespesasService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.shared.security.UsuarioAutenticadoProvider;
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
    private final PaymentGatewayProperties paymentGatewayProperties;
    private final AdminAuditService auditService;
    private final FormaPagamentoEmpresaService formaPagamentoEmpresaService;
    private final CaixaDespesasService caixaDespesasService;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;
    private final LogAtividadeService logAtividadeService;
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
        PagamentoEntity pagamentoSalvo = pagamentoRepository.save(pagamento);
        logAtividadeService.registrar("PAGAMENTO", pagamentoSalvo.getId(),
                "Registrou pagamento de " + nomeClientePagamento(pagamentoSalvo)
                        + " de R$ " + (pagamentoSalvo.getValor() != null ? pagamentoSalvo.getValor().toPlainString() : "0"));
        return mapper.toResponse(pagamentoSalvo);
    }

    @Transactional(readOnly = true)
    public List<PagamentoResponse> listarPorEmpresa(Long empresaId) {
        validarEmpresaAtual(empresaId);
        return pagamentoRepository.findByEmpresaId(empresaId).stream().map(mapper::toResponse).toList();
    }


    @Transactional
    public PagamentoResponse marcarPago(Long id, MarcarPagamentoPagoRequest request) {
        PagamentoEntity pagamento = buscarEntidade(id);
        StatusPagamento statusAnterior = pagamento.getStatus();
        formaPagamentoEmpresaService.validarPagamentoManual(pagamento.getEmpresa().getId(), request.metodoPagamento(), request.parcelas());
        MetodoPagamento metodo = formaPagamentoEmpresaService.normalizarMetodoManual(request.metodoPagamento());
        pagamento.setStatus(StatusPagamento.PAGO);
        pagamento.setMetodoPagamento(metodo);
        pagamento.setParcelas(formaPagamentoEmpresaService.normalizarParcelas(metodo, request.parcelas()));
        pagamento.setDataPagamento(LocalDateTime.now());
        PagamentoResponse response = mapper.toResponse(pagamentoRepository.save(pagamento));
        logAtividadeService.registrar("PAGAMENTO", pagamento.getId(),
                "Confirmou pagamento de " + nomeClientePagamento(pagamento)
                        + " de R$ " + (pagamento.getValor() != null ? pagamento.getValor().toPlainString() : "0"));
        if (statusAnterior != StatusPagamento.PAGO) {
            caixaDespesasService.registrarPagamentoAprovado(pagamento);
        }
        return response;
    }

    @Transactional
    public PagamentoResponse atualizarStatus(Long id, AtualizarStatusPagamentoRequest request) {
        PagamentoEntity pagamento = buscarEntidade(id);
        StatusPagamento statusAnterior = pagamento.getStatus();
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
        PagamentoResponse response = mapper.toResponse(pagamentoRepository.save(pagamento));
        String descricaoAuditoria;
        if (statusAnterior != StatusPagamento.PAGO && request.status() == StatusPagamento.PAGO) {
            descricaoAuditoria = "Confirmou pagamento de " + nomeClientePagamento(pagamento)
                    + " de R$ " + (pagamento.getValor() != null ? pagamento.getValor().toPlainString() : "0");
        } else if (statusAnterior == StatusPagamento.PAGO && request.status() == StatusPagamento.PENDENTE) {
            descricaoAuditoria = "Desfez pagamento de " + nomeClientePagamento(pagamento)
                    + " de R$ " + (pagamento.getValor() != null ? pagamento.getValor().toPlainString() : "0");
        } else if (request.status() == StatusPagamento.CANCELADO) {
            descricaoAuditoria = "Cancelou pagamento de " + nomeClientePagamento(pagamento);
        } else {
            descricaoAuditoria = "Alterou status do pagamento de " + nomeClientePagamento(pagamento) + " para " + request.status();
        }
        logAtividadeService.registrar("PAGAMENTO", pagamento.getId(), descricaoAuditoria);
        if (statusAnterior != StatusPagamento.PAGO && request.status() == StatusPagamento.PAGO) {
            caixaDespesasService.registrarPagamentoAprovado(pagamento);
        } else if (statusAnterior == StatusPagamento.PAGO && request.status() == StatusPagamento.PENDENTE) {
            caixaDespesasService.registrarPagamentoRemovido(pagamento, usuarioAutenticadoProvider.exigirUsuarioId());
        } else if (request.status() == StatusPagamento.CANCELADO) {
            caixaDespesasService.registrarPagamentoCancelado(pagamento, usuarioAutenticadoProvider.exigirUsuarioId(), statusAnterior);
        }
        return response;
    }

    @Transactional
    public void expirarCheckoutPorTimeout(PagamentoPlanoEntity pagamento) {
        if (pagamento == null) return;
        
        // Recarregar o PagamentoPlano atual para confirmar estado
        pagamento = pagamentoPlanoRepository.findById(pagamento.getId()).orElse(pagamento);
        
        if (pagamento.getStatus() != StatusPagamento.PAYMENT_PENDING) {
            log.info("Tentativa de expirar checkout id={} ignorada pois status já é terminal: {}", pagamento.getId(), pagamento.getStatus());
            return;
        }

        // Verificar o estado real da Stripe antes de expirar
        try {
            Optional<PaymentGatewayWebhook> stripeInfo = paymentGateway.consultarPagamentoPlano(pagamento);
            if (stripeInfo.isPresent()) {
                PaymentGatewayWebhook info = stripeInfo.get();
                if (info.status() == StatusPagamento.PAYMENT_APPROVED) {
                    log.info("Evitando expiração do checkout id={} pois o pagamento foi concluído na Stripe.", pagamento.getId());
                    liberarContaPorPagamentoAprovado(pagamento, "CORRIDA_TIMEOUT");
                    pagamento = pagamentoPlanoRepository.save(pagamento);
                    logAtividadeService.registrar("PAGAMENTO_PLANO", pagamento.getId(),
                            "Confirmou pagamento do plano por timeout " + (pagamento.getPlano() != null ? pagamento.getPlano().getNome() : ""));
                    return;
                }
            }
        } catch (Exception ex) {
            log.warn("Erro ao consultar Stripe para checkout id={} antes de expirar. erroTipo={}", pagamento.getId(), ex.getClass().getSimpleName());
        }

        // Invalidar a Session Stripe se ainda estiver aberta
        if (pagamento.getStripeSessionId() != null) {
            try {
                paymentGateway.expirarCheckoutSession(pagamento.getStripeSessionId());
            } catch (Exception ex) {
                log.warn("Falha ao invalidar Session Stripe para checkout id={}. erroTipo={}", pagamento.getId(), ex.getClass().getSimpleName());
            }
        }

        // Marcar PAYMENT_EXPIRED pelo caminho isolado de timeout
        pagamento.setStatus(StatusPagamento.PAYMENT_EXPIRED);
        PagamentoPlanoEntity pagamentoSalvo = pagamentoPlanoRepository.save(pagamento);
        logAtividadeService.registrar("PAGAMENTO_PLANO", pagamentoSalvo.getId(),
                "Expirou checkout do plano " + (pagamentoSalvo.getPlano() != null ? pagamentoSalvo.getPlano().getNome() : ""));

        log.info("Checkout expirado por timeout: pagamentoId={}, empresaId={}, plano={}", 
                 pagamento.getId(), pagamento.getEmpresa().getId(), pagamento.getPlano().getNome());
    }

    @Transactional
    public PagamentoPlanoEntity obterOuCriarCheckoutCentralizado(
            Long empresaId,
            String planoNome,
            MetodoPagamento metodoPagamento,
            String customerName,
            String customerEmail,
            String customerPhone,
            String antifraudProfilingAttemptReference,
            boolean isOnboarding,
            boolean forceNew
    ) {
        // 1. Adquirir proteção de concorrência usando pessimistic lock
        EmpresaEntity empresa = empresaRepository.findByIdWithLock(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa não encontrada."));

        if (!isOnboarding) {
            validarEmpresaAtual(empresaId);
        } else {
            validarEmpresaOnboarding(empresaId);
        }
        validarMetodoPagamentoPlano(metodoPagamento);

        PlanoEntity plano = planoService.buscarPorNomePermitido(normalizarPlano(planoNome));

        if (assinaturaService.buscarFilaAtiva(empresaId).size() >= 2) {
            throw new BusinessException("Você já possui 2 planos ativos. Aguarde um deles expirar para contratar novamente.");
        }

        // 2. Procurar PAYMENT_PENDING do mesmo plano
        if (!forceNew) {
            Optional<PagamentoPlanoEntity> pendenteOpt = pagamentoPlanoRepository
                    .findFirstByEmpresaIdAndPlanoIdAndStatusOrderByDataCriacaoDesc(empresaId, plano.getId(), StatusPagamento.PAYMENT_PENDING);

            if (pendenteOpt.isPresent()) {
                PagamentoPlanoEntity pendente = pendenteOpt.get();
                
                // 3. Validar prazo do backend
                if (pendente.getDataExpiracao() != null && pendente.getDataExpiracao().isAfter(LocalDateTime.now())) {
                    // Validar que existe checkoutUrl e stripeSessionId utilizáveis
                    if (pendente.getCheckoutUrl() != null && !pendente.getCheckoutUrl().isBlank()
                            && pendente.getStripeSessionId() != null && !pendente.getStripeSessionId().isBlank()) {
                        log.info("Checkout reutilizado: pagamentoId={}, empresaId={}, plano={}",
                                pendente.getId(), empresaId, plano.getNome());
                        return pendente;
                    }
                } else {
                    // Se o prazo venceu, finalizar a expiração com segurança
                    log.info("Checkout pendente encontrado, porém vencido. Expirando com segurança: pagamentoId={}", pendente.getId());
                    expirarCheckoutPorTimeout(pendente);
                }
            }
        }

        // 4. Se não existir ou estava vencido, criar um novo checkout
        PagamentoPlanoEntity pagamento = novoPagamentoPlano(
                empresa, plano, metodoPagamento, customerName, customerEmail, customerPhone,
                antifraudProfilingAttemptReference
        );
        
        // TTL de 15 minutos definido em um único lugar no backend
        int ttlMinutes = paymentGatewayProperties.getCheckout().getTtlMinutes();
        pagamento.setDataExpiracao(LocalDateTime.now(java.time.ZoneId.of("America/Sao_Paulo")).plusMinutes(ttlMinutes));
        pagamento = pagamentoPlanoRepository.save(pagamento);

        PaymentGatewayResponse gatewayResponse;
        if (isOnboarding) {
            gatewayResponse = paymentGateway.criarPagamentoPlano(pagamento, empresa.getStripeCustomerId());
        } else {
            gatewayResponse = paymentGateway.criarPagamentoPlano(pagamento);
        }

        pagamento.setProvider(gatewayResponse.provider());
        pagamento.setProviderPaymentId(gatewayResponse.providerPaymentId());
        pagamento.setExternalReference(preferir(gatewayResponse.externalReference(), pagamento.getExternalReference()));
        pagamento.setPaymentReference(preferir(gatewayResponse.paymentReference(), pagamento.getPaymentReference()));
        pagamento.setCheckoutUrl(gatewayResponse.checkoutUrl());
        
        pagamento = pagamentoPlanoRepository.save(pagamento);
        logAtividadeService.registrar("PAGAMENTO_PLANO", pagamento.getId(),
                "Criou checkout do plano " + (pagamento.getPlano() != null ? pagamento.getPlano().getNome() : ""));
        log.info("Checkout criado: pagamentoId={}, empresaId={}, plano={}",
                pagamento.getId(), empresaId, plano.getNome());
        return pagamento;
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
            String antifraudProfilingAttemptReference
    ) {
        PagamentoPlanoEntity pagamento = obterOuCriarCheckoutCentralizado(
                empresaId, planoNome, metodoPagamento, customerName, customerEmail, customerPhone,
                antifraudProfilingAttemptReference, true, false
        );
        return mapper.toPlanoResponse(pagamento);
    }

    @Transactional
    public PagamentoPlanoResponse iniciarPagamentoPlano(Long empresaId, String planoNome, MetodoPagamento metodoPagamento) {
        return iniciarPagamentoPlano(empresaId, planoNome, metodoPagamento, null, null, null, null, false);
    }

    @Transactional
    public PagamentoPlanoResponse iniciarPagamentoPlanoPro(IniciarPagamentoPlanoRequest request) {
        return iniciarPagamentoPlano(
                request.empresaId(),
                "PRO",
                request.metodoPagamento(),
                request.customerName(),
                request.customerEmail(),
                request.customerPhone(),
                request.antifraudProfilingAttemptReference(),
                request.forceNew() != null && request.forceNew()
        );
    }

    @Transactional
    public PagamentoPlanoResponse iniciarPagamentoPlano(
            Long empresaId,
            String planoNome,
            MetodoPagamento metodoPagamento,
            String customerName,
            String customerEmail,
            String customerPhone,
            String antifraudProfilingAttemptReference,
            boolean forceNew
    ) {
        PagamentoPlanoEntity pagamento = obterOuCriarCheckoutCentralizado(
                empresaId, planoNome, metodoPagamento, customerName, customerEmail, customerPhone,
                antifraudProfilingAttemptReference, false, forceNew
        );
        return mapper.toPlanoResponse(pagamento);
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
        PagamentoPlanoEntity pagamento = novoPagamentoPlano(empresa, plano, metodoPagamento, null, null, null, null);
        PagamentoPlanoEntity pagamentoSalvo = pagamentoPlanoRepository.save(pagamento);
        logAtividadeService.registrar("PAGAMENTO_PLANO", pagamentoSalvo.getId(),
                "Criou pagamento de plano pendente " + (pagamentoSalvo.getPlano() != null ? pagamentoSalvo.getPlano().getNome() : ""));
        return mapper.toPlanoResponse(pagamentoSalvo);
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

    /**
     * Consulta pre-autenticacao usada exclusivamente depois que o login validou
     * as credenciais e resolveu a empresa a partir do usuario persistido.
     */
    @Transactional(readOnly = true)
    public Optional<PagamentoPlanoResponse> buscarUltimoPagamentoPlanoPendenteParaLogin(EmpresaEntity empresaValidada) {
        if (empresaValidada == null || empresaValidada.getId() == null) {
            throw new BusinessException("Empresa valida obrigatoria para concluir o login.");
        }
        return pagamentoPlanoRepository
                .findByEmpresaIdAndStatusOrderByDataCriacaoDesc(
                        empresaValidada.getId(), StatusPagamento.PAYMENT_PENDING)
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
            logAtividadeService.registrar("PAGAMENTO_PLANO", pagamento.getId(),
                    "Confirmou pagamento do plano " + (pagamento.getPlano() != null ? pagamento.getPlano().getNome() : ""));
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
    public VerificarPagamentoPlanoResponse verificarStatusPagamentoPublico(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException("Session ID inválido.");
        }

        PagamentoPlanoEntity pagamento = pagamentoPlanoRepository.findByStripeSessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado."));

        // Se já está aprovado, retorna status atual
        if (pagamento.getStatus() == StatusPagamento.PAYMENT_APPROVED) {
            return criarRespostaVerificacao(pagamento, "APPROVED", "Pagamento aprovado.");
        }

        // Caso contrário, sincroniza com Stripe
        sincronizarPagamentoComGateway(pagamento);

        if (pagamento.getStatus() == StatusPagamento.PAYMENT_APPROVED) {
            liberarContaPorPagamentoAprovado(pagamento, "VERIFICACAO_PUBLICA");
            pagamento = pagamentoPlanoRepository.save(pagamento);
            logAtividadeService.registrar("PAGAMENTO_PLANO", pagamento.getId(),
                    "Confirmou pagamento do plano " + (pagamento.getPlano() != null ? pagamento.getPlano().getNome() : ""));
            return criarRespostaVerificacao(pagamento, "APPROVED", "Pagamento aprovado! Sua conta foi liberada.");
        }

        return switch (pagamento.getStatus()) {
            case PAYMENT_REJECTED -> criarRespostaVerificacao(pagamento, "REJECTED", "Pagamento recusado.");
            case PAYMENT_CANCELED -> criarRespostaVerificacao(pagamento, "CANCELED", "Pagamento cancelado.");
            case PAYMENT_EXPIRED -> criarRespostaVerificacao(pagamento, "EXPIRED", "Pagamento expirado.");
            default -> criarRespostaVerificacao(pagamento, "PENDING", "Aguardando confirmação.");
        };
    }

    private VerificarPagamentoPlanoResponse criarRespostaVerificacao(PagamentoPlanoEntity pagamento, String statusVerificacao, String mensagem) {
        PagamentoPlanoResponse response = mapper.toPlanoResponse(pagamento);
        return new VerificarPagamentoPlanoResponse(
                statusVerificacao,
                mensagem,
                pagamento.getEmpresa().getStatus(),
                pagamento.getAssinatura() == null ? null : pagamento.getAssinatura().getStatus(),
                response
        );
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
        PagamentoPlanoEntity pagamentoSalvo = pagamentoPlanoRepository.save(pagamento);
        logAtividadeService.registrar("PAGAMENTO_PLANO", pagamentoSalvo.getId(),
                "Confirmou pagamento do plano via Stripe " + (pagamentoSalvo.getPlano() != null ? pagamentoSalvo.getPlano().getNome() : ""));
        return pagamentoSalvo;
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
                    PagamentoPlanoEntity pagamentoSalvo = pagamentoPlanoRepository.save(pagamento);
                    logAtividadeService.registrar("PAGAMENTO_PLANO", pagamentoSalvo.getId(),
                            "Alterou status do plano " + (pagamentoSalvo.getPlano() != null ? pagamentoSalvo.getPlano().getNome() : "") + " para " + status);
                    return pagamentoSalvo;
                });
    }

    @Transactional
    public void expirarCheckoutPorSessionStripe(String stripeSessionId) {
        if (stripeSessionId == null || stripeSessionId.isBlank()) {
            return;
        }
        pagamentoPlanoRepository.findByStripeSessionId(stripeSessionId)
                .ifPresent(pagamento -> {
                    pagamentoPlanoRepository.save(pagamento);
                    expirarCheckoutPorTimeout(pagamento);
                });
    }

    @Transactional
    public void processarInvoiceStripe(String invoiceId, String subscriptionId, StatusPagamento status) {
        if (invoiceId == null || subscriptionId == null) {
            throw new BusinessException("Dados do evento Stripe inválidos.");
        }

        PagamentoPlanoEntity pagamento = pagamentoPlanoRepository.findBySubscriptionId(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento do plano não encontrado para subscriptionId: " + subscriptionId));

        pagamento.setStripeInvoiceId(invoiceId);
        aplicarStatusPagamentoPlano(pagamento, status);
        PagamentoPlanoEntity pagamentoSalvo = pagamentoPlanoRepository.save(pagamento);
        logAtividadeService.registrar("PAGAMENTO_PLANO", pagamentoSalvo.getId(),
                "Processou invoice do plano " + (pagamentoSalvo.getPlano() != null ? pagamentoSalvo.getPlano().getNome() : "") + " - " + status);
    }

    @Transactional
    public PagamentoPlanoResponse aprovarPagamentoManual(Long pagamentoId, String transacaoId) {
        PagamentoPlanoEntity pagamento = pagamentoPlanoRepository.findById(pagamentoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento do plano nao encontrado."));
        if (transacaoId != null && !transacaoId.isBlank()) {
            pagamento.setProviderPaymentId(transacaoId.trim());
        }
        aplicarStatusPagamentoPlano(pagamento, StatusPagamento.PAYMENT_APPROVED);
        PagamentoPlanoEntity pagamentoSalvo = pagamentoPlanoRepository.save(pagamento);
        logAtividadeService.registrar("PAGAMENTO_PLANO", pagamentoSalvo.getId(),
                "Aprovou pagamento do plano " + (pagamentoSalvo.getPlano() != null ? pagamentoSalvo.getPlano().getNome() : ""));
        return mapper.toPlanoResponse(pagamentoSalvo);
    }

    @Transactional
    public PagamentoPlanoResponse desaprovarPagamentoManual(Long pagamentoId, String transacaoId) {
        PagamentoPlanoEntity pagamento = pagamentoPlanoRepository.findById(pagamentoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento do plano nao encontrado."));
        if (transacaoId != null && !transacaoId.isBlank()) {
            pagamento.setProviderPaymentId(transacaoId.trim());
        }
        aplicarStatusPagamentoPlano(pagamento, StatusPagamento.PAYMENT_REJECTED);
        PagamentoPlanoEntity pagamentoSalvo = pagamentoPlanoRepository.saveAndFlush(pagamento);
        logAtividadeService.registrar("PAGAMENTO_PLANO", pagamentoSalvo.getId(),
                "Rejeitou pagamento do plano " + (pagamentoSalvo.getPlano() != null ? pagamentoSalvo.getPlano().getNome() : ""));
        return mapper.toPlanoResponse(pagamentoSalvo);
    }

    @Transactional
    public AssinaturaResponse consultarPlanoAtual(Long empresaId) {
        validarEmpresaAtual(empresaId);
        return assinaturaService.buscarAtualResponsePorEmpresa(empresaId);
    }

    @Transactional(readOnly = true)
    public PagamentoEntity buscarEntidade(Long id) {
        Long empresaId = CompanyContext.requireCompanyId();
        return pagamentoRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento nao encontrado."));
    }

    @Transactional(readOnly = true)
    public long contarPendentes(Long empresaId) {
        validarEmpresaAtual(empresaId);
        return pagamentoRepository.countByEmpresaIdAndStatus(empresaId, StatusPagamento.PENDENTE);
    }

    private String nomeClientePagamento(PagamentoEntity pagamento) {
        return pagamento.getCliente() != null ? pagamento.getCliente().getNome() : "Cliente";
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
        Long empresaContexto = CompanyContext.requireCompanyId();
        if (empresaId == null || !empresaContexto.equals(empresaId)) {
            throw new BusinessException("Empresa da sessao não corresponde ao recurso solicitado.");
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
        EmpresaEntity empresa = pagamento.getEmpresa();
        if (empresa.getStatus() == StatusEmpresa.BLOQUEADA || empresa.getStatus() == StatusEmpresa.ENCERRADA) {
            return;
        }

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

        boolean possuiVigenciaFutura = !assinaturaService.buscarFilaAtiva(empresa.getId()).isEmpty();
        empresa.setStatus(possuiVigenciaFutura ? StatusEmpresa.ATIVA : StatusEmpresa.PENDENTE_PAGAMENTO);
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
            log.warn("Consulta direta ao gateway nao confirmou pagamento {}. erroTipo={}", pagamento.getId(), ex.getClass().getSimpleName());
            return pagamento;
        }
    }

    private void registrarAuditoriaAutomatica(String tipo, EmpresaEntity empresa, String descricao) {
        auditService.registrar(tipo, "Pagamento", null, descricao);
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

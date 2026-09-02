package com.minhaempresa.gendaz.admin.service;

import com.minhaempresa.gendaz.admin.dto.AdminAssinaturaDtos.AdminAssinaturaOperacaoRequest;
import com.minhaempresa.gendaz.admin.dto.AdminAssinaturaDtos.CriarAssinaturaRequest;
import com.minhaempresa.gendaz.admin.dto.AdminAssinaturaDtos.EditarAssinaturaRequest;
import com.minhaempresa.gendaz.admin.dto.AdminDtos.*;
import com.minhaempresa.gendaz.admin.entity.AdminImpersonationSessionEntity;
import com.minhaempresa.gendaz.admin.repository.AdminImpersonationSessionRepository;
import com.minhaempresa.gendaz.chamado.dto.ChamadoDtos.AtualizarChamadoRequest;
import com.minhaempresa.gendaz.chamado.dto.ChamadoDtos.ChamadoResponse;
import com.minhaempresa.gendaz.chamado.entity.ChamadoEntity;
import com.minhaempresa.gendaz.chamado.repository.ChamadoRepository;
import com.minhaempresa.gendaz.chamado.repository.AdminChamadoProjection;
import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import com.minhaempresa.gendaz.assinatura.repository.AssinaturaRepository;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.auditoria.entity.LogAtividadeEntity;
import com.minhaempresa.gendaz.auditoria.repository.LogAtividadeRepository;
import com.minhaempresa.gendaz.auth.service.PasswordService;
import com.minhaempresa.gendaz.auth.service.UsuarioSessionService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.PagamentoPlanoResponse;
import com.minhaempresa.gendaz.pagamento.entity.PagamentoPlanoEntity;
import com.minhaempresa.gendaz.pagamento.enums.StatusPagamento;
import com.minhaempresa.gendaz.pagamento.repository.PagamentoPlanoRepository;
import com.minhaempresa.gendaz.pagamento.service.PagamentoService;
import com.minhaempresa.gendaz.plano.entity.PlanoEntity;
import com.minhaempresa.gendaz.plano.service.PlanoService;
import com.minhaempresa.gendaz.profissional.dto.ProfissionalDtos.ProfissionalResponse;
import com.minhaempresa.gendaz.profissional.dto.ProfissionalDtos.SalvarProfissionalRequest;
import com.minhaempresa.gendaz.profissional.service.ProfissionalService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.ConflictException;
import com.minhaempresa.gendaz.shared.PhoneNumberService;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.shared.SessaoExpiradaException;
import com.minhaempresa.gendaz.shared.security.SecurityMonitoringService;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AdminService {
    private static final List<StatusPagamento> STATUS_PAGAMENTO_CONFIRMADO = List.of(
            StatusPagamento.PAGO,
            StatusPagamento.PAYMENT_APPROVED
    );
    private static final List<StatusPagamento> STATUS_PAGAMENTO_PENDENTE = List.of(
            StatusPagamento.PENDENTE,
            StatusPagamento.PAYMENT_PENDING
    );
    private static final List<String> PLANOS_OFICIAIS = List.of("BASICO", "PRO", "PLUS", "ENTERPRISE");
    private static final DateTimeFormatter FORMATO_MES_DASHBOARD = DateTimeFormatter.ofPattern("uuuu-MM");
    private static final DateTimeFormatter DATA_LABEL = DateTimeFormatter.ofPattern("dd/MM", Locale.forLanguageTag("pt-BR"));

    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final PagamentoPlanoRepository pagamentoPlanoRepository;
    private final AssinaturaRepository assinaturaRepository;
    private final AdminImpersonationSessionRepository impersonationSessionRepository;
    private final ChamadoRepository chamadoRepository;
    private final AssinaturaService assinaturaService;
    private final PlanoService planoService;
    private final AdminAuditService auditService;
    private final PasswordService passwordService;
    private final UsuarioSessionService usuarioSessionService;
    private final SecurityMonitoringService securityMonitoringService;
    private final AdminSessionService adminSessionService;
    private final PagamentoService pagamentoService;
    private final ProfissionalService profissionalService;

    @Autowired
    private PhoneNumberService phoneNumberService;

    @Autowired
    private SubscriptionAdminService subscriptionAdminService;

    @Autowired
    private LogAtividadeRepository logAtividadeRepository;

    @Value("${app.frontend-url:https://gendaz.site}")
    private String frontendUrl;

    @Value("${PAYMENT_PROVIDER:local}")
    private String paymentProvider;

    @Autowired
    public AdminService(
            UsuarioRepository usuarioRepository,
            EmpresaRepository empresaRepository,
            PagamentoPlanoRepository pagamentoPlanoRepository,
            AssinaturaRepository assinaturaRepository,
            AdminImpersonationSessionRepository impersonationSessionRepository,
            ChamadoRepository chamadoRepository,
            AssinaturaService assinaturaService,
            PlanoService planoService,
            AdminAuditService auditService,
            PasswordService passwordService,
            UsuarioSessionService usuarioSessionService,
            SecurityMonitoringService securityMonitoringService,
            PagamentoService pagamentoService,
            ProfissionalService profissionalService,
            AdminSessionService adminSessionService,
            LogAtividadeRepository logAtividadeRepository
    ) {
        this.usuarioRepository = usuarioRepository;
        this.empresaRepository = empresaRepository;
        this.pagamentoPlanoRepository = pagamentoPlanoRepository;
        this.assinaturaRepository = assinaturaRepository;
        this.impersonationSessionRepository = impersonationSessionRepository;
        this.chamadoRepository = chamadoRepository;
        this.assinaturaService = assinaturaService;
        this.planoService = planoService;
        this.auditService = auditService;
        this.passwordService = passwordService;
        this.usuarioSessionService = usuarioSessionService;
        this.securityMonitoringService = securityMonitoringService;
        this.pagamentoService = pagamentoService;
        this.profissionalService = profissionalService;
        this.adminSessionService = adminSessionService;
        this.logAtividadeRepository = logAtividadeRepository;
    }

    public AdminService(
            UsuarioRepository usuarioRepository,
            EmpresaRepository empresaRepository,
            PagamentoPlanoRepository pagamentoPlanoRepository,
            AssinaturaRepository assinaturaRepository,
            AdminImpersonationSessionRepository impersonationSessionRepository,
            ChamadoRepository chamadoRepository,
            AssinaturaService assinaturaService,
            PlanoService planoService,
            AdminAuditService auditService,
            PasswordService passwordService,
            UsuarioSessionService usuarioSessionService,
            PagamentoService pagamentoService,
            ProfissionalService profissionalService
    ) {
        this(
                usuarioRepository,
                empresaRepository,
                pagamentoPlanoRepository,
                assinaturaRepository,
                impersonationSessionRepository,
                chamadoRepository,
                assinaturaService,
                planoService,
                auditService,
                passwordService,
                usuarioSessionService,
                null,
                pagamentoService,
                profissionalService,
                null,
                null
        );
    }

    public AdminService(
            UsuarioRepository usuarioRepository,
            EmpresaRepository empresaRepository,
            PagamentoPlanoRepository pagamentoPlanoRepository,
            AssinaturaRepository assinaturaRepository,
            AdminImpersonationSessionRepository impersonationSessionRepository,
            ChamadoRepository chamadoRepository,
            AssinaturaService assinaturaService,
            AdminAuditService auditService,
            PasswordService passwordService,
            UsuarioSessionService usuarioSessionService,
            PagamentoService pagamentoService,
            ProfissionalService profissionalService
    ) {
        this(
                usuarioRepository,
                empresaRepository,
                pagamentoPlanoRepository,
                assinaturaRepository,
                impersonationSessionRepository,
                chamadoRepository,
                assinaturaService,
                null,
                auditService,
                passwordService,
                usuarioSessionService,
                null,
                pagamentoService,
                profissionalService,
                null,
                null
        );
    }

    public AdminService(
            UsuarioRepository usuarioRepository,
            EmpresaRepository empresaRepository,
            PagamentoPlanoRepository pagamentoPlanoRepository,
            AdminImpersonationSessionRepository impersonationSessionRepository,
            ChamadoRepository chamadoRepository,
            AssinaturaService assinaturaService,
            AdminAuditService auditService,
            PasswordService passwordService,
            UsuarioSessionService usuarioSessionService,
            PagamentoService pagamentoService,
            ProfissionalService profissionalService
    ) {
        this(
                usuarioRepository,
                empresaRepository,
                pagamentoPlanoRepository,
                null,
                impersonationSessionRepository,
                chamadoRepository,
                assinaturaService,
                null,
                auditService,
                passwordService,
                usuarioSessionService,
                null,
                pagamentoService,
                profissionalService,
                null,
                null
        );
    }

    public boolean validarCredenciaisAdmin(String email, String senha) {
        String emailNormalizado = email == null ? "" : email.trim().toLowerCase();
        try {
            UsuarioEntity admin = buscarAdminPorEmail(emailNormalizado);
            return admin != null 
                && admin.getPerfil() == PerfilUsuario.SUPER_ADMIN 
                && admin.getStatus() == StatusUsuario.ATIVO 
                && passwordService.matches(senha, admin.getSenha());
        } catch (Exception e) {
            log.warn("[validar-credenciais-admin] erro ao validar credenciais. erroTipo={}", e.getClass().getSimpleName());
            return false;
        }
    }

    @Transactional
    public AdminLoginResponse login(AdminLoginRequest request, String ip, String userAgent) {
        String email = request.email() == null ? "" : request.email().trim().toLowerCase();
        UsuarioEntity admin = buscarAdminPorEmail(email);
        if (admin == null || admin.getPerfil() != PerfilUsuario.SUPER_ADMIN || admin.getStatus() != StatusUsuario.ATIVO || !passwordService.matches(request.senha(), admin.getSenha())) {
            auditService.registrar("ADMIN_LOGIN_FAILED", "SECURITY", null, null, null, "Falha de login admin", null, ip, userAgent);
            registrarMonitoramentoAdminLoginFalhado(ip, userAgent, email);
            throw new BusinessException("Credenciais invalidas.");
        }
        String token = adminSessionService.criarSessao(admin, ip, userAgent);
        auditService.registrar("ADMIN_LOGIN_SUCCESS", "SECURITY", admin, admin, null, "Login admin realizado", null, ip, userAgent);
        return new AdminLoginResponse(token, new AdminUsuarioResponse(admin.getId(), admin.getNome(), admin.getEmail(), admin.getPerfil().name()));
    }

    public UsuarioEntity exigirAdmin(String token) {
        if (token == null || token.isBlank()) {
            throw new SessaoExpiradaException("Acesso admin nao autorizado.");
        }
        return adminSessionService.validarSessao(token);
    }

    @Transactional(readOnly = true)
    public UsuarioEntity refresh(String token) {
        return exigirAdmin(token);
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            adminSessionService.revogarSessao(token);
        }
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard(String token, String mes) {
        exigirAdmin(token);
        YearMonth mesReferencia = resolverMesDashboard(mes);
        LocalDate inicio = mesReferencia.atDay(1);
        LocalDate fim = mesReferencia.atEndOfMonth();
        LocalDate hoje = LocalDate.now();

        List<EmpresaEntity> empresas = empresaRepository.findAll();
        List<AssinaturaEntity> assinaturas = assinaturaRepository.findAllComPlano();
        Map<Long, List<AssinaturaEntity>> assinaturasPorEmpresa = assinaturas.stream()
                .collect(Collectors.groupingBy(a -> a.getEmpresa().getId()));

        long contasAtivas = 0;
        long contasCanceladas = 0;
        long contasTeste = 0;
        long empresasVencidas = 0;
        Map<String, Long> contagemPorPlano = new HashMap<>();
        PLANOS_OFICIAIS.forEach(nome -> contagemPorPlano.put(nome, 0L));

        for (EmpresaEntity empresa : empresas) {
            if (empresa.getStatus() == StatusEmpresa.BLOQUEADA) {
                continue;
            }
            if (empresa.getStatus() == StatusEmpresa.ENCERRADA) {
                contasCanceladas++;
                continue;
            }
            List<AssinaturaEntity> lista = assinaturasPorEmpresa.getOrDefault(empresa.getId(), List.of());
            AssinaturaEntity vigente = assinaturaVigente(lista, hoje);
            if (vigente != null) {
                if (vigente.getStatus() == StatusAssinatura.TESTE) {
                    contasTeste++;
                } else {
                    contasAtivas++;
                }
                String plano = vigente.getPlano() == null ? null : vigente.getPlano().getNome();
                if (plano != null && contagemPorPlano.containsKey(plano)) {
                    contagemPorPlano.put(plano, contagemPorPlano.get(plano) + 1);
                }
                continue;
            }
            StatusAssinatura ultimo = ultimaAssinaturaIniciada(lista, hoje);
            if (ultimo == StatusAssinatura.CANCELADA) {
                contasCanceladas++;
            } else if (ultimo == StatusAssinatura.EXPIRADA) {
                empresasVencidas++;
            }
        }

        List<PagamentoPlanoEntity> pagamentos = pagamentoPlanoRepository.findAll();
        List<PagamentoPlanoEntity> confirmados = pagamentos.stream()
                .filter(this::pagamentoConfirmado)
                .filter(p -> p.getDataPagamento() != null)
                .toList();
        BigDecimal totalGanho = confirmados.stream()
                .map(PagamentoPlanoEntity::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal faturamentoMes = confirmados.stream()
                .filter(p -> !p.getDataPagamento().toLocalDate().isBefore(inicio))
                .filter(p -> !p.getDataPagamento().toLocalDate().isAfter(fim))
                .map(PagamentoPlanoEntity::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long pagamentosPendentes = pagamentos.stream().filter(this::pagamentoPendente).count();

        List<ReceitaDiaResponse> receitaDia = construirReceitaDia(confirmados, inicio, fim);
        List<PlanoDistribuicaoResponse> distribuicaoPlanos = PLANOS_OFICIAIS.stream()
                .map(nome -> new PlanoDistribuicaoResponse(nome, contagemPorPlano.getOrDefault(nome, 0L)))
                .toList();

        return new AdminDashboardResponse(
                contasAtivas,
                contasCanceladas,
                contasTeste,
                totalGanho,
                faturamentoMes,
                pagamentosPendentes,
                empresasVencidas,
                receitaDia,
                distribuicaoPlanos
        );
    }

    private YearMonth resolverMesDashboard(String mes) {
        if (mes == null || mes.isBlank()) {
            return YearMonth.now();
        }
        YearMonth anoMes;
        try {
            anoMes = YearMonth.parse(mes.trim(), FORMATO_MES_DASHBOARD);
        } catch (DateTimeParseException excecao) {
            throw new BusinessException("Mes invalido. Use o formato yyyy-MM.");
        }
        if (anoMes.getYear() < 2000 || anoMes.getYear() > 2100) {
            throw new BusinessException("Mes invalido. Informe um ano entre 2000 e 2100.");
        }
        return anoMes;
    }

    private AssinaturaEntity assinaturaVigente(List<AssinaturaEntity> assinaturas, LocalDate hoje) {
        return assinaturas.stream()
                .filter(a -> a.getStatus() == StatusAssinatura.ATIVA || a.getStatus() == StatusAssinatura.TESTE)
                .filter(a -> a.getDataInicio() != null && !a.getDataInicio().isAfter(hoje))
                .filter(a -> a.getDataFim() == null || a.getDataFim().isAfter(hoje))
                .sorted(Comparator.comparing(AssinaturaEntity::getDataInicio).thenComparing(AssinaturaEntity::getId))
                .findFirst()
                .orElse(null);
    }

    private StatusAssinatura ultimaAssinaturaIniciada(List<AssinaturaEntity> assinaturas, LocalDate hoje) {
        return assinaturas.stream()
                .filter(a -> a.getDataInicio() != null && !a.getDataInicio().isAfter(hoje))
                .max(Comparator.comparing(AssinaturaEntity::getDataInicio).thenComparing(AssinaturaEntity::getId))
                .map(AssinaturaEntity::getStatus)
                .flatMap(status -> status == StatusAssinatura.CANCELADA
                        ? Optional.of(StatusAssinatura.CANCELADA)
                        : status == StatusAssinatura.PENDENTE_PAGAMENTO
                                ? Optional.<StatusAssinatura>empty()
                                : Optional.of(StatusAssinatura.EXPIRADA))
                .orElse(null);
    }

    private List<ReceitaDiaResponse> construirReceitaDia(List<PagamentoPlanoEntity> confirmados, LocalDate inicio, LocalDate fim) {
        Map<LocalDate, BigDecimal> receitaPorDia = confirmados.stream()
                .collect(Collectors.toMap(
                        p -> p.getDataPagamento().toLocalDate(),
                        p -> p.getValor() == null ? BigDecimal.ZERO : p.getValor(),
                        BigDecimal::add
                ));
        List<ReceitaDiaResponse> resultado = new ArrayList<>();
        LocalDate data = inicio;
        while (!data.isAfter(fim)) {
            resultado.add(new ReceitaDiaResponse(
                    data.toString(),
                    data.format(DATA_LABEL),
                    receitaPorDia.getOrDefault(data, BigDecimal.ZERO)
            ));
            data = data.plusDays(1);
        }
        return resultado;
    }

    @Transactional(readOnly = true)
    public List<AdminEmpresaUsuarioResponse> usuarios(String token) {
        exigirAdmin(token);
        return empresaRepository.findAll().stream().map(empresa -> {
            UsuarioEntity dono = usuarioRepository.findByEmpresaIdAndPerfil(empresa.getId(), PerfilUsuario.DONO).stream().findFirst().orElse(null);
            AssinaturaEntity assinatura = assinaturaService.buscarAtualPorEmpresa(empresa.getId()).orElse(null);
            PagamentoPlanoEntity ultimoPagamento = pagamentoPlanoRepository.findByEmpresaIdOrderByDataCriacaoDesc(empresa.getId()).stream().findFirst().orElse(null);
            return new AdminEmpresaUsuarioResponse(
                    empresa.getId(),
                    dono == null ? null : dono.getId(),
                    empresa.getNomeFantasia(),
                    dono == null ? null : dono.getNome(),
                    dono == null ? empresa.getEmail() : dono.getEmail(),
                    empresa.getEmail(),
                    empresa.getTelefone(),
                    assinatura == null ? null : assinatura.getPlano().getNome(),
                    empresa.getStatus().name(),
                    assinatura == null ? null : assinatura.getStatus().name(),
                    empresa.getDataCriacao(),
                    ultimoPagamento == null ? null : ultimoPagamento.getDataPagamento(),
                    ultimoPagamento == null ? null : ultimoPagamento.getValor()
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminPagamentoResponse> pagamentos(String token, String status, String plano) {
        exigirAdmin(token);
        return pagamentoPlanoRepository.findAll().stream()
                .filter(p -> status == null || status.isBlank() || p.getStatus().name().equalsIgnoreCase(status))
                .filter(p -> plano == null || plano.isBlank() || p.getPlano().getNome().equalsIgnoreCase(plano))
                .map(p -> {
                    UsuarioEntity dono = usuarioRepository.findByEmpresaIdAndPerfil(p.getEmpresa().getId(), PerfilUsuario.DONO)
                            .stream()
                            .findFirst()
                            .orElse(null);
                    return new AdminPagamentoResponse(
                            p.getId(),
                            p.getEmpresa().getNomeFantasia(),
                            dono == null ? null : dono.getNome(),
                            dono == null ? p.getEmpresa().getEmail() : dono.getEmail(),
                            p.getEmpresa().getTelefone(),
                            p.getPlano().getNome(),
                            p.getValor(),
                            p.getProvider(),
                            p.getStatus().name(),
                            p.getEmpresa().getStatus().name(),
                            p.getDataCriacao(),
                            p.getDataExpiracao(),
                            p.getDataPagamento(),
                            p.getProviderPaymentId(),
                            p.getExternalReference(),
                            p.getPaymentReference(),
                            p.getCheckoutUrl()
                    );
                }).toList();
    }

    @Transactional
    public PagamentoPlanoResponse aprovarPagamentoManualmente(String token, Long pagamentoId, AprovarPagamentoManualRequest request, String ip, String userAgent) {
        UsuarioEntity admin = exigirAdmin(token);
        String motivo = motivoAdminOuPadrao(request == null ? null : request.motivo(), "Aprovacao manual confirmada pelo Super Admin.");
        log.info("Super Admin {} solicitou aprovacao manual do pagamento {}", admin.getId(), pagamentoId);
        String transacaoId = request == null ? null : request.transacaoId();
        PagamentoPlanoResponse response = pagamentoService.aprovarPagamentoManual(pagamentoId, transacaoId);
        String transacao = transacaoId == null || transacaoId.isBlank() ? "nao informado" : transacaoId.trim();
        auditService.registrar(
                "PAYMENT_MANUAL_APPROVED",
                "SECURITY",
                admin,
                null,
                empresaRepository.findById(response.empresaId()).orElse(null),
                "Pagamento aprovado manualmente pelo Super Admin",
                "transacao=" + transacao + "; motivo=" + motivo,
                ip,
                userAgent
        );
        log.info("Pagamento {} aprovado manualmente: empresa={}, status={}", pagamentoId, response.empresaId(), response.status());
        return response;
    }

    @Transactional
    public PagamentoPlanoResponse desaprovarPagamentoManualmente(String token, Long pagamentoId, DesaprovarPagamentoManualRequest request, String ip, String userAgent) {
        UsuarioEntity admin = exigirAdmin(token);
        if (request.motivo() == null || request.motivo().trim().length() < 8) {
            throw new BusinessException("Informe um motivo com pelo menos 8 caracteres.");
        }
        log.info("Super Admin {} solicitou reversao manual do pagamento {}", admin.getId(), pagamentoId);
        PagamentoPlanoResponse response = pagamentoService.desaprovarPagamentoManual(pagamentoId, request.transacaoId());
        String transacao = request.transacaoId() == null || request.transacaoId().isBlank() ? "nao informado" : request.transacaoId().trim();
        auditService.registrar(
                "PAYMENT_MANUAL_REJECTED",
                "SECURITY",
                admin,
                null,
                empresaRepository.findById(response.empresaId()).orElse(null),
                "Pagamento revertido manualmente pelo Super Admin",
                "transacao=" + transacao + "; motivo=" + request.motivo().trim(),
                ip,
                userAgent
        );
        log.info("Pagamento {} revertido manualmente: empresa={}, status={}", pagamentoId, response.empresaId(), response.status());
        return response;
    }

    @Transactional
    public ImpersonarResponse iniciarImpersonacao(String token, Long empresaId, ImpersonarRequest request, String ip, String userAgent) {
        UsuarioEntity admin = exigirAdmin(token);
        String motivo = motivoAdminOuPadrao(request == null ? null : request.motivo(), "Acesso administrativo confirmado pelo Super Admin.");
        log.info("Super Admin {} solicitou acesso a empresa {}", admin.getId(), empresaId);
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa nao encontrada."));
        UsuarioEntity dono = usuarioRepository.findByEmpresaIdAndPerfil(empresaId, PerfilUsuario.DONO)
                .stream()
                .findFirst()
                .orElseGet(() -> usuarioRepository.findByEmpresaId(empresaId).stream().findFirst()
                        .orElseThrow(() -> new ResourceNotFoundException("Usuario da empresa nao encontrado.")));
        AdminImpersonationSessionEntity session = impersonationSessionRepository.save(AdminImpersonationSessionEntity.builder()
                .adminUsuarioId(admin.getId())
                .usuarioImpersonadoId(dono.getId())
                .empresaId(empresa.getId())
                .sessionTokenHash("legacy-" + UUID.randomUUID())
                .status("ENCERRADA")
                .ipInicio(ip)
                .userAgentInicio(userAgent)
                .criadoEm(LocalDateTime.now())
                .expiraEm(LocalDateTime.now())
                .encerradoEm(LocalDateTime.now())
                .motivoEncerramento("LEGACY")
                .build());
        auditService.registrar("IMPERSONACAO_INICIADA", "SECURITY", admin, null, empresa, "Super Admin acessou conta de empresa", motivo, ip, userAgent);
        log.info("Sessao de impersonacao {} criada para empresa {}", session.getId(), empresa.getId());
        String plano = assinaturaService.buscarAtualPorEmpresa(empresaId)
                .map(assinatura -> assinatura.getPlano() == null ? null : assinatura.getPlano().getNome())
                .orElse("BASICO");
        return new ImpersonarResponse(
                session.getId(),
                empresa.getId(),
                dono.getId(),
                plano,
                dono.getNome(),
                dono.getEmail(),
                empresa.getNomeFantasia(),
                motivo,
                session.getCriadoEm()
        );
    }

    @Transactional
    public AdminEmpresaUsuarioResponse ativarEmpresa(String token, Long empresaId, AdminAcaoEmpresaRequest request, String ip, String userAgent) {
        UsuarioEntity admin = exigirAdmin(token);
        validarMotivoAdmin(request.motivo());
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa nao encontrada."));
        AssinaturaEntity assinatura = assinaturaService.buscarAtualPorEmpresa(empresaId).orElse(null);
        if (assinatura == null || (assinatura.getStatus() != StatusAssinatura.ATIVA && assinatura.getStatus() != StatusAssinatura.TESTE)) {
            throw new BusinessException("A conta so pode ser ativada quando houver assinatura ativa ou teste valido.");
        }
        empresa.setStatus(StatusEmpresa.ATIVA);
        EmpresaEntity salva = empresaRepository.save(empresa);
        auditService.registrar("EMPRESA_ATIVADA", "SECURITY", admin, null, salva, "Conta ativada manualmente pelo Super Admin", request.motivo().trim(), ip, userAgent);
        return montarEmpresaResponse(salva);
    }

    @Transactional
    public AdminEmpresaUsuarioResponse desativarEmpresa(String token, Long empresaId, AdminAcaoEmpresaRequest request, String ip, String userAgent) {
        UsuarioEntity admin = exigirAdmin(token);
        validarMotivoAdmin(request.motivo());
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa nao encontrada."));
        empresa.setStatus(StatusEmpresa.BLOQUEADA);
        EmpresaEntity salva = empresaRepository.save(empresa);
        usuarioRepository.findByEmpresaId(empresaId).stream()
                .map(UsuarioEntity::getSessaoAtiva)
                .filter(sessao -> sessao != null && !sessao.isBlank())
                .forEach(usuarioSessionService::encerrarSessao);
        auditService.registrar("EMPRESA_DESATIVADA", "SECURITY", admin, null, salva, "Conta bloqueada manualmente pelo Super Admin", request.motivo().trim(), ip, userAgent);
        return montarEmpresaResponse(salva);
    }

    @Transactional
    public AdminEmpresaUsuarioResponse atualizarEmpresa(String token, Long empresaId, AdminAtualizarEmpresaRequest request, String ip, String userAgent) {
        UsuarioEntity admin = exigirAdmin(token);
        validarMotivoAdmin(request.motivo());
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa nao encontrada."));

        String nomeFantasia = normalizarTextoObrigatorio(request.nomeFantasia());
        String telefone = normalizarTelefone(request.telefone());
        String email = normalizarEmail(request.email());

        empresa.setNomeFantasia(nomeFantasia);
        empresa.setTelefone(telefone);
        empresa.setEmail(email);
        EmpresaEntity salva = empresaRepository.save(empresa);

        UsuarioEntity dono = usuarioRepository.findByEmpresaIdAndPerfil(empresa.getId(), PerfilUsuario.DONO)
                .stream()
                .findFirst()
                .orElse(null);
        if (dono != null && !email.equalsIgnoreCase(dono.getEmail())) {
            usuarioRepository.findByEmailIgnoreCase(email).ifPresent((outro) -> {
                if (!outro.getId().equals(dono.getId())) {
                    throw new BusinessException("O e-mail informado ja esta em uso por outra conta.");
                }
            });
            dono.setEmail(email);
            usuarioRepository.save(dono);
        }

        boolean alterarAssinatura = request.planoId() != null || request.diasPlano() != null;
        boolean alterarAssinaturas = request.assinaturas() != null && !request.assinaturas().isEmpty();
        if (alterarAssinatura) {
            atualizarPlanoEAjustarPrazo(empresa, request);
        }
        if (alterarAssinaturas) {
            aplicarAlteracoesAssinaturas(empresaId, request.assinaturas());
        }

        auditService.registrar(
                "EMPRESA_EDITADA",
                "SECURITY",
                admin,
                null,
                salva,
                alterarAssinatura || alterarAssinaturas
                        ? "Dados basicos da empresa e plano atualizados pelo Super Admin"
                        : "Dados basicos da empresa atualizados pelo Super Admin",
                request.motivo().trim(),
                ip,
                userAgent
        );
        return montarEmpresaResponse(salva);
    }

    @Transactional
    public void encerrarImpersonacao(String token, Long sessionId, String ip, String userAgent) {
        UsuarioEntity admin = exigirAdmin(token);
        AdminImpersonationSessionEntity session = impersonationSessionRepository.findByIdAndAdminUsuarioIdAndStatus(sessionId, admin.getId(), "ATIVA")
                .orElseThrow(() -> new ResourceNotFoundException("Sessao de impersonacao nao encontrada."));
        session.setStatus("ENCERRADA");
        session.setEncerradoEm(LocalDateTime.now());
        session.setMotivoEncerramento("MANUAL");
        EmpresaEntity empresa = empresaRepository.findById(session.getEmpresaId()).orElse(null);
        auditService.registrar("IMPERSONACAO_ENCERRADA", "SECURITY", admin, null, empresa, "Super Admin saiu da conta acessada", "MANUAL", ip, userAgent);
    }

    @Transactional(readOnly = true)
    public List<AdminAuditLogResponse> logs(String token) {
        exigirAdmin(token);
        List<AdminAuditLogResponse> auditoria = auditService.listar();
        List<AdminAuditLogResponse> atividade = logAtividadeRepository
                .findTop1000ByOrderByDataHoraDesc()
                .stream()
                .map(this::toAtividadeResponse)
                .toList();
        List<AdminAuditLogResponse> todos = new ArrayList<>(auditoria);
        todos.addAll(atividade);
        todos.sort(Comparator.comparing(
                AdminAuditLogResponse::dataCriacao,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));
        return todos;
    }

    @Transactional
    public void limparLogs(String token) {
        exigirAdmin(token);
        auditService.limpar();
        if (logAtividadeRepository != null) {
            logAtividadeRepository.deleteAll();
        }
    }

    private AdminAuditLogResponse toAtividadeResponse(LogAtividadeEntity l) {
        String empresa = l.getEmpresa() != null ? String.valueOf(l.getEmpresa().getId()) : "-";
        String descricao = l.getAcao() != null ? l.getAcao() : "";
        if (l.getDetalhes() != null && !l.getDetalhes().isBlank()) {
            descricao = descricao + " - " + l.getDetalhes();
        }
        return new AdminAuditLogResponse(
                l.getId(),
                l.getEntidade(),
                "INFO",
                null,
                l.getNomeUsuario(),
                empresa,
                descricao,
                l.getDetalhes(),
                l.getIp(),
                null,
                l.getDataHora()
        );
    }

    public AdminConfigResponse configuracoes(String token) {
        exigirAdmin(token);
        return new AdminConfigResponse(paymentProvider, frontendUrl, "/api", "OPERACIONAL", "1.0.0", "mascarados");
    }

    @Transactional(readOnly = true)
    public List<AdminChamadoResponse> chamados(String token) {
        exigirAdmin(token);
        return chamadoRepository.listarParaAdmin().stream()
                .filter(Objects::nonNull)
                .map(chamado -> new AdminChamadoResponse(
                        chamado.getId(),
                        chamado.getAssunto(),
                        chamado.getMensagem(),
                        chamado.getEmpresa(),
                        chamado.getUsuario(),
                        chamado.getStatus(),
                        chamado.getResposta(),
                        chamado.getDataCriacao(),
                        chamado.getDataAtualizacao()
                ))
                .toList();
    }

    @Transactional
    public AdminChamadoResponse atualizarChamado(String token, Long chamadoId, AtualizarChamadoRequest request, String ip, String userAgent) {
        UsuarioEntity admin = exigirAdmin(token);
        ChamadoEntity chamado = chamadoRepository.findById(chamadoId)
                .orElseThrow(() -> new ResourceNotFoundException("Chamado nao encontrado."));
        var statusAnterior = chamado.getStatus();
        chamado.setStatus(request.status());
        if (request.resposta() != null && !request.resposta().isBlank()) {
            chamado.setResposta(request.resposta().trim());
        }
        ChamadoEntity salvo = chamadoRepository.save(chamado);
        auditService.registrar(
                "CHAMADO_ATUALIZADO",
                "SECURITY",
                admin,
                null,
                salvo.getEmpresa(),
                "Chamado atualizado pelo Super Admin",
                "status=" + statusAnterior + "->" + salvo.getStatus(),
                ip,
                userAgent
        );
        return new AdminChamadoResponse(
                salvo.getId(),
                salvo.getAssunto(),
                salvo.getMensagem(),
                salvo.getEmpresa() != null ? salvo.getEmpresa().getNomeFantasia() : null,
                salvo.getUsuario() != null ? salvo.getUsuario().getNome() : null,
                salvo.getStatus().name(),
                salvo.getResposta(),
                salvo.getDataCriacao(),
                salvo.getDataAtualizacao()
        );
    }

    @Transactional(readOnly = true)
    public List<ProfissionalResponse> listarProfissionais(String token, Long empresaId) {
        exigirAdmin(token);
        return profissionalService.listarPorEmpresa(empresaId);
    }

    @Transactional
    public ProfissionalResponse atualizarProfissional(String token, Long id, SalvarProfissionalRequest request) {
        exigirAdmin(token);
        return profissionalService.atualizarAdmin(id, request);
    }

    @Transactional
    public void excluirProfissional(String token, Long id) {
        exigirAdmin(token);
        profissionalService.excluirAdmin(id);
    }

    private boolean pagamentoConfirmado(PagamentoPlanoEntity pagamento) {
        return pagamento.getStatus() == StatusPagamento.PAGO || pagamento.getStatus() == StatusPagamento.PAYMENT_APPROVED;
    }

    private boolean pagamentoPendente(PagamentoPlanoEntity pagamento) {
        return pagamento.getStatus() == StatusPagamento.PENDENTE || pagamento.getStatus() == StatusPagamento.PAYMENT_PENDING;
    }

    private void validarMotivoAdmin(String motivo) {
        if (motivo == null || motivo.trim().length() < 8) {
            throw new BusinessException("Informe um motivo com pelo menos 8 caracteres.");
        }
    }

    private void atualizarPlanoEAjustarPrazo(EmpresaEntity empresa, AdminAtualizarEmpresaRequest request) {
        AssinaturaEntity assinatura = assinaturaService.buscarAtualPorEmpresa(empresa.getId()).orElse(null);
        PlanoEntity plano = request.planoId() == null ? null : planoService.buscarEntidade(request.planoId());
        int diasPlano = request.diasPlano() == null ? 30 : request.diasPlano();
        if (diasPlano < 1) {
            throw new BusinessException("Informe dias de plano maior ou igual a 1.");
        }

        if (assinatura == null) {
            if (plano == null) {
                throw new BusinessException("Selecione um plano para criar a assinatura.");
            }
            assinatura = assinaturaService.ativarPlanoPago(empresa, plano);
            // mantem o encadeamento da fila: prazo conta a partir do inicio encadeado
            assinatura.setDataFim(assinatura.getDataInicio().plusDays(diasPlano));
        } else {
            if (plano != null) {
                assinatura.setPlano(plano);
            }
            assinatura.setStatus(StatusAssinatura.ATIVA);
            assinatura.setDataInicio(LocalDate.now());
            assinatura.setDataFim(LocalDate.now().plusDays(diasPlano));
        }

        assinaturaRepository.save(assinatura);
        assinaturaService.reposicionarFuturas(empresa.getId(), assinatura.getId());
    }

    private void aplicarAlteracoesAssinaturas(Long empresaId, List<AdminAssinaturaOperacaoRequest> operacoes) {
        List<AdminAssinaturaOperacaoRequest> remocoes = operacoes.stream()
                .filter(op -> "REMOVER".equalsIgnoreCase(op.operacao()))
                .toList();
        List<AdminAssinaturaOperacaoRequest> edicoes = operacoes.stream()
                .filter(op -> "EDITAR".equalsIgnoreCase(op.operacao()))
                .toList();
        List<AdminAssinaturaOperacaoRequest> criacoes = operacoes.stream()
                .filter(op -> "CRIAR".equalsIgnoreCase(op.operacao()))
                .toList();
        if (remocoes.size() + edicoes.size() + criacoes.size() != operacoes.size()) {
            throw new BusinessException("Operacao de assinatura invalida.");
        }

        for (AdminAssinaturaOperacaoRequest op : remocoes) {
            if (op.subscriptionId() == null) {
                throw new BusinessException("Assinatura invalida para remocao.");
            }
            subscriptionAdminService.removerAssinatura(empresaId, op.subscriptionId());
        }

        for (AdminAssinaturaOperacaoRequest op : edicoes) {
            if (op.subscriptionId() == null || op.planoId() == null) {
                throw new BusinessException("Assinatura ou plano invalidos para edicao.");
            }
            subscriptionAdminService.editarAssinatura(
                    empresaId,
                    op.subscriptionId(),
                    new EditarAssinaturaRequest(op.planoId(), op.dias(), op.dataInicio(), op.dataFim(), op.status())
            );
        }

        for (AdminAssinaturaOperacaoRequest op : criacoes) {
            if (op.planoId() == null) {
                throw new BusinessException("Selecione um plano para adicionar a conta.");
            }
            subscriptionAdminService.criarAssinatura(
                    empresaId,
                    new CriarAssinaturaRequest(op.planoId(), op.dias(), op.dataInicio(), op.dataFim(), op.status())
            );
        }
    }

    private String normalizarTextoObrigatorio(String valor) {
        if (valor == null) {
            throw new BusinessException("Campo obrigatorio nao informado.");
        }
        String normalizado = valor.trim().replaceAll("\\s+", " ");
        if (normalizado.length() < 2) {
            throw new BusinessException("Campo obrigatorio nao informado.");
        }
        return normalizado;
    }

    private String normalizarTelefone(String valor) {
        return phoneNumberService.normalizarOpcional(valor);
    }

    private String normalizarEmail(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new BusinessException("E-mail da empresa e obrigatorio.");
        }
        return valor.trim().toLowerCase();
    }

    private String motivoAdminOuPadrao(String motivo, String padrao) {
        if (motivo == null || motivo.isBlank()) {
            return padrao;
        }
        String normalizado = motivo.trim();
        if (normalizado.length() < 8) {
            throw new BusinessException("Informe um motivo com pelo menos 8 caracteres.");
        }
        return normalizado;
    }

    private AdminEmpresaUsuarioResponse montarEmpresaResponse(EmpresaEntity empresa) {
        UsuarioEntity dono = usuarioRepository.findByEmpresaIdAndPerfil(empresa.getId(), PerfilUsuario.DONO).stream().findFirst().orElse(null);
        AssinaturaEntity assinatura = assinaturaService.buscarAtualPorEmpresa(empresa.getId()).orElse(null);
        PagamentoPlanoEntity ultimoPagamento = pagamentoPlanoRepository.findByEmpresaIdOrderByDataCriacaoDesc(empresa.getId()).stream().findFirst().orElse(null);
        return new AdminEmpresaUsuarioResponse(
                empresa.getId(),
                dono == null ? null : dono.getId(),
                empresa.getNomeFantasia(),
                dono == null ? null : dono.getNome(),
                dono == null ? empresa.getEmail() : dono.getEmail(),
                empresa.getEmail(),
                empresa.getTelefone(),
                assinatura == null ? null : assinatura.getPlano().getNome(),
                empresa.getStatus().name(),
                assinatura == null ? null : assinatura.getStatus().name(),
                empresa.getDataCriacao(),
                ultimoPagamento == null ? null : ultimoPagamento.getDataPagamento(),
                ultimoPagamento == null ? null : ultimoPagamento.getValor()
        );
    }

    private void registrarMonitoramentoAdminLoginFalhado(String ip, String userAgent, String email) {
        if (securityMonitoringService == null) {
            return;
        }
        securityMonitoringService.registrarEvento(
                "ADMIN_LOGIN_FALHADO",
                "CRITICAL",
                ip,
                userAgent,
                "/api/admin/auth/login",
                securityMonitoringService.mascararEmail(email),
                "credenciais_invalidas"
        );
    }

    private UsuarioEntity buscarAdminPorEmail(String email) {
        List<UsuarioEntity> admins = usuarioRepository.findAllByEmailIgnoreCase(email).stream()
                .filter(usuario -> usuario.getPerfil() == PerfilUsuario.SUPER_ADMIN)
                .toList();
        if (admins.size() > 1) {
            throw new ConflictException("Dados de usuario duplicados. Contate o suporte para regularizacao.");
        }
        return admins.isEmpty() ? null : admins.get(0);
    }
}


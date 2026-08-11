package com.minhaempresa.gendaz.admin.service;

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
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
import com.minhaempresa.gendaz.shared.SessaoExpiradaException;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AdminService {
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
    private final PagamentoService pagamentoService;
    private final ProfissionalService profissionalService;

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
            PagamentoService pagamentoService,
            ProfissionalService profissionalService
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
        this.pagamentoService = pagamentoService;
        this.profissionalService = profissionalService;
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
                pagamentoService,
                profissionalService
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
                pagamentoService,
                profissionalService
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
            log.warn("[validar-credenciais-admin] erro ao validar credenciais: {}", e.getMessage());
            return false;
        }
    }

    @Transactional
    public AdminLoginResponse login(AdminLoginRequest request, String ip, String userAgent) {
        String email = request.email() == null ? "" : request.email().trim().toLowerCase();
        UsuarioEntity admin = buscarAdminPorEmail(email);
        if (admin == null || admin.getPerfil() != PerfilUsuario.SUPER_ADMIN || admin.getStatus() != StatusUsuario.ATIVO || !passwordService.matches(request.senha(), admin.getSenha())) {
            auditService.registrar("ADMIN_LOGIN_FAILED", "SECURITY", null, null, null, "Falha de login admin", null, ip, userAgent);
            throw new BusinessException("Credenciais invalidas.");
        }
        String token = usuarioSessionService.renovarSessao(admin);
        auditService.registrar("ADMIN_LOGIN_SUCCESS", "SECURITY", admin, admin, null, "Login admin realizado", null, ip, userAgent);
        return new AdminLoginResponse(token, new AdminUsuarioResponse(admin.getId(), admin.getNome(), admin.getEmail(), admin.getPerfil().name()));
    }

public UsuarioEntity exigirAdmin(String token) {
        if (token == null || token.isBlank()) {
            throw new SessaoExpiradaException("Acesso admin nao autorizado.");
        }
        UsuarioEntity admin = usuarioRepository.findBySessaoAtiva(token).orElse(null);
        if (admin == null || admin.getPerfil() != PerfilUsuario.SUPER_ADMIN || admin.getStatus() != StatusUsuario.ATIVO) {
            throw new SessaoExpiradaException("Acesso admin nao autorizado.");
        }
        return admin;
    }

    @Transactional(readOnly = true)
    public UsuarioEntity refresh(String token) {
        return exigirAdmin(token);
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard(String token) {
        exigirAdmin(token);
        List<PagamentoPlanoEntity> pagamentos = pagamentoPlanoRepository.findAll();
        List<AssinaturaEntity> assinaturas = empresaRepository.findAll().stream()
                .map(empresa -> assinaturaService.buscarAtualPorEmpresa(empresa.getId()).orElse(null))
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());
        YearMonth mesAtual = YearMonth.now();
        BigDecimal faturamentoTotal = pagamentos.stream()
                .filter(this::pagamentoConfirmado)
                .map(PagamentoPlanoEntity::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal faturamentoMes = pagamentos.stream()
                .filter(this::pagamentoConfirmado)
                .filter(p -> p.getDataPagamento() != null && YearMonth.from(p.getDataPagamento()).equals(mesAtual))
                .map(PagamentoPlanoEntity::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ReceitaPontoResponse> receita = pagamentos.stream()
                .filter(this::pagamentoConfirmado)
                .filter(p -> p.getDataPagamento() != null)
                .collect(java.util.stream.Collectors.groupingBy(p -> YearMonth.from(p.getDataPagamento()).toString(),
                        java.util.stream.Collectors.reducing(BigDecimal.ZERO, PagamentoPlanoEntity::getValor, BigDecimal::add)))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new ReceitaPontoResponse(e.getKey(), e.getValue()))
                .toList();
        List<PlanoDistribuicaoResponse> planos = assinaturas.stream()
                .collect(java.util.stream.Collectors.groupingBy(a -> a.getPlano().getNome(), java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .map(e -> new PlanoDistribuicaoResponse(e.getKey(), e.getValue()))
                .toList();
        return new AdminDashboardResponse(
                faturamentoTotal,
                faturamentoMes,
                pagamentos.stream().filter(this::pagamentoConfirmado).count(),
                pagamentos.stream().filter(this::pagamentoPendente).count(),
                assinaturas.stream().filter(a -> a.getStatus() == StatusAssinatura.ATIVA).count(),
                assinaturas.stream().filter(a -> a.getStatus() == StatusAssinatura.TESTE).count(),
                assinaturas.stream().filter(a -> a.getStatus() == StatusAssinatura.EXPIRADA).count(),
                usuarioRepository.findAll().stream().filter(u -> u.getStatus() == StatusUsuario.ATIVO && u.getPerfil() != PerfilUsuario.SUPER_ADMIN).count(),
                empresaRepository.findAll().stream().filter(e -> e.getDataCriacao() != null && e.getDataCriacao().isAfter(LocalDateTime.now().minusDays(30))).count(),
                receita,
                planos
        );
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
                    empresa.getNomeFantasia(),
                    empresa.getDocumento(),
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
                .admin(admin)
                .empresa(empresa)
                .motivo(motivo)
                .ip(ip)
                .userAgent(userAgent)
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
                session.getMotivo(),
                session.getDataInicio()
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
        String documento = normalizarDocumento(request.documento());
        String telefone = normalizarTelefone(request.telefone());
        String email = normalizarEmail(request.email());

        if (documento != null && !documento.isBlank() && !Objects.equals(empresa.getDocumento(), documento) && empresaRepository.existsByDocumento(documento)) {
            throw new ConflictException("Ja existe empresa com este documento.");
        }

        empresa.setNomeFantasia(nomeFantasia);
        empresa.setDocumento(documento);
        empresa.setTelefone(telefone);
        empresa.setEmail(email);
        EmpresaEntity salva = empresaRepository.save(empresa);

        boolean alterarAssinatura = request.planoId() != null || request.diasPlano() != null;
        if (alterarAssinatura) {
            atualizarPlanoEAjustarPrazo(empresa, request);
        }

        auditService.registrar(
                "EMPRESA_EDITADA",
                "SECURITY",
                admin,
                null,
                salva,
                alterarAssinatura
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
        AdminImpersonationSessionEntity session = impersonationSessionRepository.findByIdAndAdminIdAndAtivaTrue(sessionId, admin.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Sessao de impersonacao nao encontrada."));
        session.setAtiva(false);
        session.setDataFim(LocalDateTime.now());
        auditService.registrar("IMPERSONACAO_ENCERRADA", "SECURITY", admin, null, session.getEmpresa(), "Super Admin saiu da conta acessada", session.getMotivo(), ip, userAgent);
    }

    @Transactional(readOnly = true)
    public List<AdminAuditLogResponse> logs(String token) {
        exigirAdmin(token);
        return auditService.listar();
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
                salvo.getEmpresa().getNomeFantasia(),
                salvo.getUsuario().getNome(),
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

    private String normalizarDocumento(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.replaceAll("\\D", "");
    }

    private String normalizarTelefone(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String digitos = valor.replaceAll("\\D", "");
        if (digitos.isEmpty()) {
            return null;
        }
        if (!digitos.startsWith("55")) {
            digitos = "55" + digitos;
        }
        if (digitos.length() == 12 && digitos.startsWith("55")) {
            digitos = digitos.substring(0, 4) + "9" + digitos.substring(4);
        }
        if (digitos.length() != 13) {
            throw new BusinessException("Telefone deve ter 13 digitos. Formato: +55 (DDD) 99999-9999");
        }
        int ddd = Integer.parseInt(digitos.substring(2, 4));
        if (ddd < 11 || ddd > 99) {
            throw new BusinessException("DDD invalido. Deve ser entre 11 e 99.");
        }
        return digitos;
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
                empresa.getNomeFantasia(),
                empresa.getDocumento(),
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


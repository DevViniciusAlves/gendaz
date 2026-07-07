package com.minhaempresa.agendapro.admin.service;

import com.minhaempresa.agendapro.admin.dto.AdminDtos.*;
import com.minhaempresa.agendapro.admin.entity.AdminImpersonationSessionEntity;
import com.minhaempresa.agendapro.admin.repository.AdminImpersonationSessionRepository;
import com.minhaempresa.agendapro.chamado.dto.ChamadoDtos.AtualizarChamadoRequest;
import com.minhaempresa.agendapro.chamado.dto.ChamadoDtos.ChamadoResponse;
import com.minhaempresa.agendapro.chamado.repository.ChamadoRepository;
import com.minhaempresa.agendapro.chamado.entity.ChamadoEntity;
import com.minhaempresa.agendapro.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.agendapro.assinatura.enums.StatusAssinatura;
import com.minhaempresa.agendapro.assinatura.service.AssinaturaService;
import com.minhaempresa.agendapro.auth.service.PasswordService;
import com.minhaempresa.agendapro.auth.service.UsuarioSessionService;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.empresa.enums.StatusEmpresa;
import com.minhaempresa.agendapro.empresa.repository.EmpresaRepository;
import com.minhaempresa.agendapro.pagamento.dto.PagamentoDtos.PagamentoPlanoResponse;
import com.minhaempresa.agendapro.pagamento.entity.PagamentoPlanoEntity;
import com.minhaempresa.agendapro.pagamento.enums.StatusPagamento;
import com.minhaempresa.agendapro.pagamento.repository.PagamentoPlanoRepository;
import com.minhaempresa.agendapro.pagamento.service.PagamentoService;
import com.minhaempresa.agendapro.profissional.dto.ProfissionalDtos.ProfissionalResponse;
import com.minhaempresa.agendapro.profissional.dto.ProfissionalDtos.SalvarProfissionalRequest;
import com.minhaempresa.agendapro.profissional.service.ProfissionalService;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.shared.ConflictException;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
import com.minhaempresa.agendapro.usuario.entity.UsuarioEntity;
import com.minhaempresa.agendapro.usuario.enums.PerfilUsuario;
import com.minhaempresa.agendapro.usuario.enums.StatusUsuario;
import com.minhaempresa.agendapro.usuario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {
    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final PagamentoPlanoRepository pagamentoPlanoRepository;
    private final AdminImpersonationSessionRepository impersonationSessionRepository;
    private final ChamadoRepository chamadoRepository;
    private final AssinaturaService assinaturaService;
    private final AdminAuditService auditService;
    private final PasswordService passwordService;
    private final PagamentoService pagamentoService;
    private final UsuarioSessionService usuarioSessionService;
    private final ProfissionalService profissionalService;
    private final Map<String, AdminSession> sessions = new ConcurrentHashMap<>();

    @Value("${app.frontend-url:https://gendaz.site}")
    private String frontendUrl;

    @Value("${PAYMENT_PROVIDER:local}")
    private String paymentProvider;

    @Transactional
    public AdminLoginResponse login(AdminLoginRequest request, String ip, String userAgent) {
        String email = request.email() == null ? "" : request.email().trim().toLowerCase();
        UsuarioEntity admin = usuarioRepository.findByEmail(email).orElse(null);
        if (admin == null || admin.getPerfil() != PerfilUsuario.SUPER_ADMIN || admin.getStatus() != StatusUsuario.ATIVO || !passwordService.matches(request.senha(), admin.getSenha())) {
            auditService.registrar("ADMIN_LOGIN_FAILED", "SECURITY", null, null, null, "Falha de login admin", null, ip, userAgent);
            throw new BusinessException("Credenciais invalidas.");
        }
        String token = UUID.randomUUID().toString() + UUID.randomUUID();
        sessions.put(token, new AdminSession(token, admin, LocalDateTime.now()));
        auditService.registrar("ADMIN_LOGIN_SUCCESS", "SECURITY", admin, admin, null, "Login admin realizado", null, ip, userAgent);
        return new AdminLoginResponse(token, new AdminUsuarioResponse(admin.getId(), admin.getNome(), admin.getEmail(), admin.getPerfil().name()));
    }

    public UsuarioEntity exigirAdmin(String token) {
        AdminSession session = sessions.get(token);
        if (session == null || session.admin().getPerfil() != PerfilUsuario.SUPER_ADMIN || session.admin().getStatus() != StatusUsuario.ATIVO) {
            throw new BusinessException("Acesso admin nao autorizado.");
        }
        return session.admin();
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard(String token) {
        exigirAdmin(token);
        List<PagamentoPlanoEntity> pagamentos = pagamentoPlanoRepository.findAll();
        List<AssinaturaEntity> assinaturas = empresaRepository.findAll().stream()
                .map(empresa -> assinaturaService.buscarAtualPorEmpresa(empresa.getId()).orElse(null))
                .filter(Objects::nonNull)
                .toList();
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
        String sessionToken = usuarioSessionService.obterOuCriarSessao(dono);
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
                sessionToken,
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

        auditService.registrar(
                "EMPRESA_EDITADA",
                "SECURITY",
                admin,
                null,
                salva,
                "Dados basicos da empresa atualizados pelo Super Admin",
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
        return chamadoRepository.findAllByOrderByDataCriacaoDesc().stream().map(chamado -> new AdminChamadoResponse(
                chamado.getId(),
                chamado.getAssunto(),
                chamado.getMensagem(),
                chamado.getEmpresa().getNomeFantasia(),
                chamado.getUsuario().getNome(),
                chamado.getStatus().name(),
                chamado.getResposta(),
                chamado.getDataCriacao(),
                chamado.getDataAtualizacao()
        )).toList();
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
        String telefone = valor.replaceAll("\\D", "");
        if (telefone.length() < 10 || telefone.length() > 15) {
            throw new BusinessException("Telefone deve ter de 10 a 15 digitos.");
        }
        return telefone;
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
}

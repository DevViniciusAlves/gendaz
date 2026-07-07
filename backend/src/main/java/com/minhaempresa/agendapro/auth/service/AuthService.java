package com.minhaempresa.agendapro.auth.service;

import com.minhaempresa.agendapro.assinatura.dto.AssinaturaDtos.AssinaturaResponse;
import com.minhaempresa.agendapro.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.agendapro.assinatura.enums.StatusAssinatura;
import com.minhaempresa.agendapro.assinatura.service.AssinaturaService;
import com.minhaempresa.agendapro.admin.service.AdminAuditService;
import com.minhaempresa.agendapro.auth.dto.AuthDtos.CriarContaRequest;
import com.minhaempresa.agendapro.auth.dto.AuthDtos.LoginRequest;
import com.minhaempresa.agendapro.auth.dto.AuthDtos.LoginResponse;
import com.minhaempresa.agendapro.auth.dto.AuthDtos.RefreshResponse;
import com.minhaempresa.agendapro.empresa.entity.EmpresaEntity;
import com.minhaempresa.agendapro.security.IpTrackingService;
import com.minhaempresa.agendapro.security.RecaptchaService;
import com.minhaempresa.agendapro.empresa.enums.StatusEmpresa;
import com.minhaempresa.agendapro.empresa.repository.EmpresaRepository;
import com.minhaempresa.agendapro.pagamento.dto.PagamentoDtos.PagamentoPlanoResponse;
import com.minhaempresa.agendapro.pagamento.enums.MetodoPagamento;
import com.minhaempresa.agendapro.pagamento.service.PagamentoService;
import com.minhaempresa.agendapro.email.ResendEmailService;
import com.minhaempresa.agendapro.plano.entity.PlanoEntity;
import com.minhaempresa.agendapro.plano.service.PlanoService;
import com.minhaempresa.agendapro.profissional.service.ProfissionalService;
import com.minhaempresa.agendapro.shared.DocumentoUtils;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.shared.ConflictException;
import com.minhaempresa.agendapro.usuario.entity.UsuarioEntity;
import com.minhaempresa.agendapro.usuario.enums.PerfilUsuario;
import com.minhaempresa.agendapro.usuario.enums.StatusUsuario;
import com.minhaempresa.agendapro.usuario.mapper.UsuarioMapper;
import com.minhaempresa.agendapro.usuario.repository.UsuarioRepository;
import com.minhaempresa.agendapro.usuario.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private static final String VERSAO_TERMOS = "2026-06-22";
    private static final String VERSAO_PRIVACIDADE = "2026-06-22";

    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final PlanoService planoService;
    private final AssinaturaService assinaturaService;
    private final PagamentoService pagamentoService;
    private final PasswordRecoveryService passwordRecoveryService;
    private final ResendEmailService resendEmailService;
    private final PasswordService passwordService;
    private final UsuarioSessionService usuarioSessionService;
    private final AdminAuditService auditService;
    private final RecaptchaService recaptchaService;
    private final IpTrackingService ipTrackingService;
    private final ProfissionalService profissionalService;
    private final TransactionTemplate transactionTemplate;
    private final UsuarioMapper mapper = new UsuarioMapper();

    @Transactional
    public LoginResponse login(LoginRequest request) {
        long inicio = System.nanoTime();
        String email = normalizarEmail(request.email());
        log.info("Login solicitado para {}", mascararEmail(email));
        try {
            UsuarioEntity usuario = usuarioService.buscarPorEmail(email);

            if (usuario.estaBloqueado()) {
                long minutosRestantes = java.time.temporal.ChronoUnit.MINUTES
                        .between(LocalDateTime.now(), usuario.getBloqueadoAte());
                log.warn("[login] usuario {} bloqueado por {}min", mascararEmail(email), minutosRestantes);
                throw new BusinessException(
                        "Conta temporariamente bloqueada. Tente novamente em " + minutosRestantes + " minuto(s)."
                );
            }

            if (usuario.precisaCaptcha()) {
                String captchaToken = request.recaptchaToken();
                if (captchaToken == null || captchaToken.isBlank()) {
                    log.warn("[login] CAPTCHA requerido para {} mas nao foi enviado", mascararEmail(email));
                    throw new BusinessException("CAPTCHA_REQUIRED");
                }
                if (!recaptchaService.validarCaptcha(captchaToken)) {
                    log.warn("[login] CAPTCHA invalido para usuario {}", mascararEmail(email));
                    throw new BusinessException("CAPTCHA invalido. Tente novamente.");
                }
            }

            if (!passwordService.matches(request.senha(), usuario.getSenha())) {
                usuario.setTentativasLoginFalhadas(usuario.getTentativasLoginFalhadas() + 1);
                usuario.setUltimoLoginFalhado(LocalDateTime.now());

                if (usuario.getTentativasLoginFalhadas() >= 5) {
                    usuario.setBloqueadoAte(LocalDateTime.now().plusHours(1));
                    usuarioRepository.save(usuario);
                    log.warn("[login] usuario {} bloqueado por 1h apos {} tentativas", mascararEmail(email), usuario.getTentativasLoginFalhadas());
                    throw new BusinessException("Sua conta foi temporariamente bloqueada por segurança. Tente novamente em 1 hora.");
                }

                usuarioRepository.save(usuario);
                log.warn("[login] senha invalida para {} (tentativa {})", mascararEmail(email), usuario.getTentativasLoginFalhadas());
                ipTrackingService.registrarTentativaFalhada(getCurrentClientIp());
                throw new BusinessException("E-mail ou senha invalidos.");
            }

            if (usuario.getStatus() != StatusUsuario.ATIVO) {
                log.warn("Login negado por usuario inativo para {}", mascararEmail(email));
                throw new BusinessException("E-mail ou senha invalidos.");
            }

            usuario.setTentativasLoginFalhadas(0);
            usuario.setBloqueadoAte(null);
            usuario.setUltimoLoginFalhado(null);
            usuarioRepository.save(usuario);
            ipTrackingService.registrarTentativaBemsucedida(getCurrentClientIp());

            AssinaturaResponse assinatura = null;
            PagamentoPlanoResponse pagamentoPlano = null;
            if (usuario.getEmpresa() != null) {
                AssinaturaEntity assinaturaAtual = assinaturaService.buscarAtualPorEmpresa(usuario.getEmpresa().getId()).orElse(null);
                assinatura = assinaturaAtual == null ? null : assinaturaService.toResponse(assinaturaAtual);
                pagamentoPlano = pagamentoService.buscarUltimoPagamentoPlanoPendente(usuario.getEmpresa().getId()).orElse(null);
                if (usuario.getEmpresa().getStatus() == StatusEmpresa.PENDENTE_PAGAMENTO) {
                    log.info("Login pendente de pagamento para {}", mascararEmail(email));
                    return new LoginResponse(
                            "Conta aguardando confirmacao de pagamento.",
                            mapper.toResponse(usuario),
                            assinatura,
                            pagamentoPlano,
                            "ACCOUNT_PENDING_PAYMENT"
                    );
                }
                if (assinaturaAtual != null && assinaturaAtual.getStatus() == StatusAssinatura.EXPIRADA) {
                    if (usuario.getEmpresa().getStatus() != StatusEmpresa.INATIVA) {
                        usuario.getEmpresa().setStatus(StatusEmpresa.INATIVA);
                        empresaRepository.save(usuario.getEmpresa());
                    }
                    log.info("Login redirecionado para conta inativa por teste expirado para {}", mascararEmail(email));
                    return new LoginResponse(
                            "Seu periodo gratuito terminou. Faca o pagamento para continuar usando o AgendNew.",
                            mapper.toResponse(usuario),
                            assinatura,
                            pagamentoPlano,
                            "ACCOUNT_INACTIVE"
                    );
                }
                if (usuario.getEmpresa().getStatus() != StatusEmpresa.ATIVA) {
                    log.info("Login redirecionado para conta inativa para {}", mascararEmail(email));
                    return new LoginResponse(
                            "Sua conta encontra-se inativa. Regularize a mensalidade para continuar usando o AgendNew.",
                            mapper.toResponse(usuario),
                            assinatura,
                            pagamentoPlano,
                            "ACCOUNT_INACTIVE"
                    );
                }
            }
            String sessionToken = usuarioSessionService.renovarSessao(usuario);
            registrarAuditoriaAutenticacao("USER_LOGIN_SUCCESS", usuario, "Login realizado com sucesso");
            log.info("Login concluido para {} em {} ms", mascararEmail(email), duracaoMs(inicio));
            return new LoginResponse("Login realizado com sucesso.", mapper.toResponse(usuario), assinatura, null, "ACTIVE", sessionToken);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Login falhou para {} em {} ms. Causa real abaixo.", mascararEmail(email), duracaoMs(inicio), ex);
            throw ex;
        }
    }

    public LoginResponse criarConta(CriarContaRequest request) {
        long inicio = System.nanoTime();
        String email = normalizarEmail(request.email());
        String telefone = normalizarTelefone(request.telefone());
        String nomeEmpresa = normalizarTexto(request.nomeEmpresa());
        String nomeProprietario = normalizarTexto(request.nomeProprietario());
        String documento = DocumentoUtils.normalizar(request.documentoNumero());

        log.info("Cadastro solicitado para {}", mascararEmail(email));
        try {
            CadastroContaCriada cadastro = transactionTemplate.execute(status -> criarContaBase(request, email, telefone, nomeEmpresa, nomeProprietario, documento));
            if (cadastro == null) {
                throw new BusinessException("Não foi possível criar a conta.");
            }

            PagamentoPlanoResponse pagamentoPlano = null;
            if (cadastro.cadastroPro()) {
                try {
                    pagamentoPlano = pagamentoService.iniciarPagamentoPlanoPro(cadastro.empresaId(), MetodoPagamento.PIX_AUTO);
                } catch (BusinessException ex) {
                    log.warn("Cadastro Pro criado, mas pagamento inicial nao foi gerado para empresa {}: {}", cadastro.empresaId(), ex.getMessage());
                    pagamentoPlano = pagamentoService.criarPagamentoPlanoPendente(cadastro.empresaId(), "PRO", MetodoPagamento.PIX_AUTO);
                }
            }

            if (cadastro.usuario() != null) {
                boolean emailBoasVindas = resendEmailService.enviarBoasVindas(
                        cadastro.usuario().getEmail(),
                        cadastro.usuario().getNome(),
                        cadastro.usuario().getEmpresa() == null ? "Gendaz" : cadastro.usuario().getEmpresa().getNomeFantasia()
                );
                if (!emailBoasVindas) {
                    log.warn("Email de boas-vindas nao enviado para {}", mascararEmail(cadastro.usuario().getEmail()));
                }

                String planoNome = cadastro.assinatura() != null && cadastro.assinatura().getPlano() != null
                        ? cadastro.assinatura().getPlano().getNome() : request.plano();
                String dataCadastro = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
                try {
                    resendEmailService.sendNewCustomerNotification(
                            cadastro.usuario().getNome(),
                            cadastro.usuario().getEmail(),
                            request.telefone(),
                            request.nomeEmpresa(),
                            planoNome,
                            dataCadastro,
                            cadastro.empresaId(),
                            request.documentoNumero(),
                            cadastro.usuario().getId()
                    );
                } catch (Exception e) {
                    log.error("[admin-notification] erro ao enviar notificacao de novo cliente: {}", e.getMessage(), e);
                }
            }

            log.info("Cadastro concluido para {} em {} ms", mascararEmail(email), duracaoMs(inicio));
            if (cadastro.cadastroPro()) {
                return new LoginResponse(
                        "Cadastro criado. A conta Pro aguarda confirmação de pagamento.",
                        mapper.toResponse(cadastro.usuario()),
                        assinaturaService.toResponse(cadastro.assinatura()),
                        pagamentoPlano,
                        "ACCOUNT_PENDING_PAYMENT"
                );
            }
            String sessionToken = usuarioSessionService.renovarSessao(cadastro.usuario());
            registrarAuditoriaAutenticacao("USER_REGISTER_SUCCESS", cadastro.usuario(), "Conta criada com sucesso");
            return new LoginResponse("Conta criada com sucesso. Seu teste grátis de 7 dias começou.", mapper.toResponse(cadastro.usuario()), assinaturaService.toResponse(cadastro.assinatura()), null, "ACTIVE", sessionToken);
        } catch (RuntimeException ex) {
            log.error("Cadastro falhou para {} em {} ms. Causa real abaixo.", mascararEmail(email), duracaoMs(inicio), ex);
            throw ex;
        }
    }

    @Transactional
    public void solicitarRecuperacaoSenha(String email) {
        String normalizado = normalizarEmail(email);
        usuarioRepository.findByEmail(normalizado).ifPresent(usuario -> {
            String token = passwordRecoveryService.solicitarRecuperacao(usuario);
            boolean enviado = resendEmailService.enviarRecuperacaoSenha(
                    usuario.getEmail(),
                    usuario.getNome(),
                    token
            );
            if (!enviado) {
                log.warn("Email de recuperacao nao enviado para {}", mascararEmail(usuario.getEmail()));
            }
        });
    }

    @Transactional
    public void redefinirSenha(String token, String novaSenha, String confirmarNovaSenha) {
        passwordRecoveryService.redefinirSenha(token, novaSenha, confirmarNovaSenha);
    }

    @Transactional
    public void trocarSenha(Long usuarioId, String sessionToken, String senhaAtual, String novaSenha, String confirmarNovaSenha) {
        UsuarioEntity usuario = buscarUsuarioAutenticado(usuarioId);
        if (!passwordService.matches(senhaAtual, usuario.getSenha())) {
            throw new BusinessException("Senha atual inválida.");
        }
        if (!novaSenha.equals(confirmarNovaSenha)) {
            throw new BusinessException("As senhas não coincidem.");
        }
        passwordService.validarSenha(novaSenha);
        usuario.setSenha(passwordService.hash(novaSenha));
        usuarioRepository.save(usuario);
        usuarioSessionService.encerrarSessao(usuario.getId(), sessionToken);
    }

    @Transactional
    public void logout(Long usuarioId, String sessionToken) {
        usuarioSessionService.encerrarSessao(usuarioId, sessionToken);
    }

    @Transactional
    public RefreshResponse refresh(Long usuarioId, String sessionToken) {
        UsuarioEntity usuario = buscarUsuarioAutenticado(usuarioId);
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new BusinessException("Sessão inválida.");
        }
        if (!usuarioSessionService.sessaoValida(usuarioId, sessionToken)) {
            throw new BusinessException("Sessão expirada.");
        }
        String novaSessao = usuarioSessionService.renovarSessao(usuario);
        AssinaturaResponse assinatura = usuario.getEmpresa() == null
                ? null
                : assinaturaService.buscarAtualResponsePorEmpresa(usuario.getEmpresa().getId());
        PagamentoPlanoResponse pagamentoPlano = usuario.getEmpresa() == null
                ? null
                : pagamentoService.buscarUltimoPagamentoPlanoPendente(usuario.getEmpresa().getId()).orElse(null);
        return new RefreshResponse("Sessao renovada com sucesso.", mapper.toResponse(usuario), assinatura, pagamentoPlano, "ACTIVE", novaSessao);
    }

    @Transactional(readOnly = true)
    public UsuarioEntity buscarUsuarioAutenticado(Long usuarioId) {
        if (usuarioId == null) {
            throw new BusinessException("Usuário autenticado obrigatório.");
        }
        UsuarioEntity usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new BusinessException("Usuário autenticado inválido."));
        if (usuario.getStatus() != StatusUsuario.ATIVO) {
            throw new BusinessException("Usuário inativo.");
        }
        if (usuario.getPerfil() != PerfilUsuario.SUPER_ADMIN
                && usuario.getEmpresa() != null
                && usuario.getEmpresa().getStatus() != StatusEmpresa.ATIVA) {
            throw new BusinessException("Conta indisponível. Entre em contato com o suporte.");
        }
        return usuario;
    }

    @Transactional(readOnly = true)
    public UsuarioEntity buscarUsuarioAutenticado(Long usuarioId, String sessionToken) {
        if (usuarioId != null) {
            return buscarUsuarioAutenticado(usuarioId);
        }
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new BusinessException("Usuário autenticado obrigatório.");
        }
        UsuarioEntity usuario = usuarioRepository.findBySessaoAtiva(sessionToken)
                .orElseThrow(() -> new BusinessException("Usuário autenticado inválido."));
        if (usuario.getStatus() != StatusUsuario.ATIVO) {
            throw new BusinessException("Usuário inativo.");
        }
        if (usuario.getPerfil() != PerfilUsuario.SUPER_ADMIN
                && usuario.getEmpresa() != null
                && usuario.getEmpresa().getStatus() != StatusEmpresa.ATIVA) {
            throw new BusinessException("Conta indisponível. Entre em contato com o suporte.");
        }
        return usuario;
    }

    private CadastroContaCriada criarContaBase(CriarContaRequest request, String email, String telefone, String nomeEmpresa, String nomeProprietario, String documento) {
        validarCadastro(request);
        if (usuarioRepository.existsByEmail(email)) {
            log.warn("Cadastro bloqueado por e-mail duplicado para {}", mascararEmail(email));
            throw new ConflictException("Ja existe uma conta com este e-mail.");
        }
        if (empresaRepository.existsByTelefone(telefone)) {
            throw new ConflictException("Este numero ja esta cadastrado.");
        }
        if (empresaRepository.findByNomeFantasiaNormalizado(nomeEmpresa).isPresent()) {
            throw new ConflictException("Este nome de empresa ja esta cadastrado.");
        }
        if (empresaRepository.existsByDocumento(documento)) {
            throw new ConflictException("Este documento ja esta cadastrado.");
        }

            PlanoEntity planoEscolhido = planoService.buscarPorNomePermitido(request.plano());
            boolean cadastroPro = "PRO".equalsIgnoreCase(planoEscolhido.getNome());
            EmpresaEntity empresa = empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia(nomeEmpresa)
                .documento(documento)
                .telefone(telefone)
                .email(email)
                .status(cadastroPro ? StatusEmpresa.PENDENTE_PAGAMENTO : StatusEmpresa.ATIVA)
                .build());

        profissionalService.buscarOuCriarAtendimentoPrincipal(empresa);

        LocalDateTime agora = LocalDateTime.now();
        UsuarioEntity usuario = usuarioRepository.save(UsuarioEntity.builder()
                .nome(nomeProprietario)
                .email(email)
                .senha(passwordService.hash(request.senha()))
                .perfil(PerfilUsuario.DONO)
                .status(StatusUsuario.ATIVO)
                .empresa(empresa)
                .tentativasLoginFalhadas(0)
                .aceitouTermos(true)
                .dataAceiteTermos(agora)
                .dataAceitePolitica(agora)
                .versaoTermos(VERSAO_TERMOS)
                .versaoPolitica(VERSAO_PRIVACIDADE)
                .build());

        boolean boasVindasEnviado = resendEmailService.enviarBoasVindas(email, nomeProprietario, nomeEmpresa);
        if (!boasVindasEnviado) {
            log.warn("Email de boas-vindas nao enviado para {}", mascararEmail(email));
        }

        AssinaturaEntity assinatura = cadastroPro
                ? assinaturaService.criarPendentePagamento(empresa, planoEscolhido)
                : assinaturaService.criarTesteGratis(empresa, planoEscolhido);
        log.info("Conta criada: empresa={}, usuario={}, assinatura={}, plano={}, status={}",
                empresa.getId(), usuario.getId(), assinatura.getId(), planoEscolhido.getNome(), assinatura.getStatus());
        return new CadastroContaCriada(empresa.getId(), usuario, assinatura, cadastroPro);
    }

    private void validarCadastro(CriarContaRequest request) {
        String telefone = normalizarTelefone(request.telefone());
        if (normalizarTexto(request.nomeProprietario()).length() < 2 || normalizarTexto(request.nomeProprietario()).length() > 80) {
            throw new BusinessException("Nome do usuário deve ter entre 2 e 80 caracteres.");
        }
        if (normalizarTexto(request.nomeEmpresa()).length() < 2 || normalizarTexto(request.nomeEmpresa()).length() > 100) {
            throw new BusinessException("Nome da empresa deve ter entre 2 e 100 caracteres.");
        }
        if (normalizarEmail(request.email()).length() > 120) {
            throw new BusinessException("E-mail deve ter no maximo 120 caracteres.");
        }
        if (telefone.length() < 10 || telefone.length() > 15) {
            throw new BusinessException("Telefone deve ter entre 10 e 15 digitos.");
        }
        DocumentoUtils.validar(request.documentoTipo(), request.documentoNumero());
        if (!request.senha().equals(request.confirmarSenha())) {
            throw new BusinessException("As senhas não coincidem.");
        }
        passwordService.validarSenha(request.senha());
        if (!Boolean.TRUE.equals(request.aceiteTermos())) {
            throw new BusinessException("Aceite os termos para continuar.");
        }
    }

    private String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String normalizarTelefone(String telefone) {
        return telefone == null ? "" : telefone.replaceAll("\\D", "");
    }

    private String normalizarTexto(String texto) {
        return texto == null ? "" : texto.trim().replaceAll("\\s+", " ");
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

    private void registrarAuditoriaAutenticacao(String tipo, UsuarioEntity usuario, String descricao) {
        try {
            auditService.registrar(tipo, "INFO", null, usuario, usuario.getEmpresa(), descricao, null, null, null);
        } catch (RuntimeException ex) {
            log.error("Auditoria de autenticacao falhou e foi ignorada para nao derrubar login/cadastro. tipo={}, usuarioId={}",
                    tipo, usuario == null ? null : usuario.getId(), ex);
        }
    }

    private long duracaoMs(long inicio) {
        return (System.nanoTime() - inicio) / 1_000_000;
    }

    private String getCurrentClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "unknown";
        HttpServletRequest request = attrs.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip.split(",")[0].trim();
    }

    private record CadastroContaCriada(Long empresaId, UsuarioEntity usuario, AssinaturaEntity assinatura, boolean cadastroPro) {}
}

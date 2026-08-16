package com.minhaempresa.gendaz.auth.service;

import com.minhaempresa.gendaz.assinatura.dto.AssinaturaDtos.AssinaturaResponse;
import com.minhaempresa.gendaz.assinatura.entity.AssinaturaEntity;
import com.minhaempresa.gendaz.assinatura.enums.StatusAssinatura;
import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.admin.service.AdminAuditService;
import com.minhaempresa.gendaz.auth.dto.AuthDtos.CriarContaRequest;
import com.minhaempresa.gendaz.auth.dto.AuthDtos.LoginRequest;
import com.minhaempresa.gendaz.auth.dto.AuthDtos.LoginResponse;
import com.minhaempresa.gendaz.auth.dto.AuthDtos.RefreshResponse;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.security.IpTrackingService;
import com.minhaempresa.gendaz.security.RecaptchaService;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.PagamentoPlanoResponse;
import com.minhaempresa.gendaz.pagamento.enums.MetodoPagamento;
import com.minhaempresa.gendaz.pagamento.service.PagamentoService;
import com.minhaempresa.gendaz.email.ResendEmailService;
import com.minhaempresa.gendaz.plano.entity.PlanoEntity;
import com.minhaempresa.gendaz.plano.service.PlanoService;
import com.minhaempresa.gendaz.profissional.service.ProfissionalService;
import com.minhaempresa.gendaz.shared.PhoneNumberService;
import com.minhaempresa.gendaz.shared.DocumentoUtils;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.ConflictException;
import com.minhaempresa.gendaz.shared.SessaoExpiradaException;
import com.minhaempresa.gendaz.shared.security.SecurityMonitoringService;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.membresia.enums.StatusMembresia;
import com.minhaempresa.gendaz.membresia.repository.MembresiaRepository;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import com.minhaempresa.gendaz.usuario.mapper.UsuarioMapper;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import com.minhaempresa.gendaz.usuario.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
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
    private static final List<PerfilUsuario> PERFIS_PAINEL_DIRETOS = List.of(PerfilUsuario.SUPER_ADMIN, PerfilUsuario.DONO);

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
    private final SecurityMonitoringService securityMonitoringService;
    private final ProfissionalService profissionalService;
    private final PhoneNumberService phoneNumberService;
    private final TransactionTemplate transactionTemplate;
    private final MembresiaRepository membresiaRepository;
    private final UsuarioMapper mapper = new UsuarioMapper();

    public boolean validarCredenciaisLogin(String email, String senha) {
        String emailNormalizado = normalizarEmail(email);
        try {
            UsuarioEntity usuario = resolverUsuarioUnicoPorEmail(emailNormalizado);
            return usuario != null
                    && usuario.getStatus() == StatusUsuario.ATIVO
                    && passwordService.matches(senha, usuario.getSenha());
        } catch (Exception e) {
            log.warn("[validar-credenciais] erro ao validar credenciais: {}", e.getMessage());
            return false;
        }
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        long inicio = System.nanoTime();
        String email = normalizarEmail(request.email());
        log.info("[LOGIN-SERVICE] chegou no AuthService email={}", mascararEmail(email));
        log.info("Login solicitado para {}", mascararEmail(email));
        try {
            UsuarioEntity usuario = resolverUsuarioUnicoPorEmail(email);

            if (usuario == null || usuario.getStatus() != StatusUsuario.ATIVO) {
                logLoginFalhado(email, 0);
                throw new BusinessException("E-mail ou senha invalidos.");
            }

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
                    usuario.setBloqueadoAte(LocalDateTime.now().plusHours(5));
                    usuarioRepository.save(usuario);
                    log.warn("[login] usuario {} bloqueado por 5h apos {} tentativas", mascararEmail(email), usuario.getTentativasLoginFalhadas());
                    logLoginFalhado(email, usuario.getTentativasLoginFalhadas());
                    throw new BusinessException("Tentativas de senha esgotadas. Sua conta foi bloqueada temporariamente. Tente novamente em 5 horas.");
                }

                usuarioRepository.save(usuario);
                log.warn("[login] senha invalida para {} (tentativa {})", mascararEmail(email), usuario.getTentativasLoginFalhadas());
                logLoginFalhado(email, usuario.getTentativasLoginFalhadas());
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
                            "ACCOUNT_PENDING_PAYMENT",
                            null,
                            "PAGAMENTO_PENDENTE"
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
                            "ACCOUNT_INACTIVE",
                            null,
                            "PAGAMENTO_PENDENTE"
                    );
                }
                if (usuario.getEmpresa().getStatus() != StatusEmpresa.ATIVA) {
                    log.info("Login redirecionado para conta inativa para {}", mascararEmail(email));
                    String motivo = usuario.getEmpresa().getStatus() == StatusEmpresa.BLOQUEADA ? "ADMIN_SUSPENSAO" : "PAGAMENTO_PENDENTE";
                    return new LoginResponse(
                            "Sua conta encontra-se inativa. Regularize a mensalidade para continuar usando o AgendNew.",
                            mapper.toResponse(usuario),
                            assinatura,
                            pagamentoPlano,
                            "ACCOUNT_INACTIVE",
                            null,
                            motivo
                    );
                }
            }
            String sessionToken = usuarioSessionService.renovarSessao(usuario);
            registrarAuditoriaAutenticacao("USER_LOGIN_SUCCESS", usuario, "Login realizado com sucesso");
            logLoginBemSucedido(email);
            log.info("Login concluido para {} em {} ms", mascararEmail(email), duracaoMs(inicio));
            return new LoginResponse("Login realizado com sucesso.", mapper.toResponse(usuario), assinatura, null, "ACTIVE", sessionToken);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Login falhou para {} em {} ms. Causa real abaixo.", mascararEmail(email), duracaoMs(inicio), ex);
            throw ex;
        }
    }

    @Transactional
    public LoginResponse criarConta(CriarContaRequest request) {
        long inicio = System.nanoTime();
        String email = normalizarEmail(request.email());
        String telefone = phoneNumberService.normalizarObrigatorio(request.telefone());
        String nomeEmpresa = normalizarTexto(request.nomeEmpresa());
        String nomeProprietario = normalizarTexto(request.nomeProprietario());
        String documento = DocumentoUtils.normalizar(request.documentoNumero());

        log.info("Cadastro solicitado para {}", mascararEmail(email));
        try {
            CadastroContaCriada cadastro = criarContaBase(request, email, telefone, nomeEmpresa, nomeProprietario, documento);
            
            PagamentoPlanoResponse pagamentoPlano = null;
            if (cadastro.cadastroPro()) {
                pagamentoPlano = pagamentoService.iniciarPagamentoPlanoOnboarding(
                        cadastro.empresaId(),
                        "PRO",
                        MetodoPagamento.CREDIT_CARD,
                        cadastro.usuario().getNome(),
                        cadastro.usuario().getEmail(),
                        cadastro.usuario().getEmpresa().getTelefone(),
                        null, null, null
                );
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
                            phoneNumberService.formatarExibicao(telefone),
                            request.nomeEmpresa(),
                            planoNome,
                            dataCadastro,
                            cadastro.empresaId(),
                            documento.isBlank() ? null : documento,
                            cadastro.usuario().getId()
                    );
                } catch (Exception e) {
                    log.error("[admin-notification] erro ao enviar notificacao de novo cliente: {}", e.getMessage(), e);
                }
            }

            log.info("Cadastro concluido para {} em {} ms", mascararEmail(email), duracaoMs(inicio));
            if (cadastro.cadastroPro()) {
                return new LoginResponse(
                        "Cadastro criado. A conta Pro aguarda confirmaÃ§Ã£o de pagamento.",
                        mapper.toResponse(cadastro.usuario()),
                        assinaturaService.toResponse(cadastro.assinatura()),
                        pagamentoPlano,
                        "ACCOUNT_PENDING_PAYMENT",
                        null,
                        "PAGAMENTO_PENDENTE"
                );
            }
            String sessionToken = usuarioSessionService.renovarSessao(cadastro.usuario());
            registrarAuditoriaAutenticacao("USER_REGISTER_SUCCESS", cadastro.usuario(), "Conta criada com sucesso");
            return new LoginResponse("Conta criada com sucesso. Seu teste grÃ¡tis de 7 dias comeÃ§ou.", mapper.toResponse(cadastro.usuario()), assinaturaService.toResponse(cadastro.assinatura()), null, "ACTIVE", sessionToken, null);
        } catch (RuntimeException ex) {
            log.error("Cadastro falhou para {} em {} ms. Causa real abaixo.", mascararEmail(email), duracaoMs(inicio), ex);
            throw ex;
        }
    }

    @Transactional
    public void solicitarRecuperacaoSenha(String email) {
        String normalizado = normalizarEmail(email);
        logRecuperacaoSenha(normalizado);
        List<UsuarioEntity> usuarios = usuarioRepository.findUsuariosPainelByEmailIgnoreCase(normalizado, PERFIS_PAINEL_DIRETOS);
        if (usuarios.isEmpty()) {
            registrarMonitoramentoRecuperacaoSenha(normalizado, "EMAIL_NAO_ENCONTRADO");
            return;
        }
        usuarios.forEach(usuario -> {
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

    private UsuarioEntity resolverUsuarioUnicoPorEmail(String email) {
        List<UsuarioEntity> usuarios = usuarioRepository.findUsuariosPainelByEmailIgnoreCase(email, PERFIS_PAINEL_DIRETOS);
        if (usuarios.isEmpty()) {
            return null;
        }
        if (usuarios.size() > 1) {
            throw new ConflictException("Dados de usuario duplicados. Contate o suporte para regularizacao.");
        }
        return usuarios.get(0);
    }

    @Transactional
    public void redefinirSenha(String token, String novaSenha, String confirmarNovaSenha) {
        passwordRecoveryService.redefinirSenha(token, novaSenha, confirmarNovaSenha);
    }

    @Transactional
    public void trocarSenha(Long usuarioId, String sessionToken, String senhaAtual, String novaSenha, String confirmarNovaSenha) {
        UsuarioEntity usuario = buscarUsuarioAutenticado(usuarioId, sessionToken);
        if (!passwordService.matches(senhaAtual, usuario.getSenha())) {
            throw new BusinessException("Senha atual invÃ¡lida.");
        }
        if (!novaSenha.equals(confirmarNovaSenha)) {
            throw new BusinessException("As senhas nÃ£o coincidem.");
        }
        passwordService.validarSenha(novaSenha);
        usuario.setSenha(passwordService.hash(novaSenha));
        usuarioRepository.save(usuario);
        logAlteracaoSenha(usuario.getId());
        usuarioSessionService.encerrarSessao(sessionToken);
    }

    @Transactional
    public void logout(String sessionToken) {
        usuarioSessionService.encerrarSessao(sessionToken);
    }

    @Transactional
    public RefreshResponse refresh(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new BusinessException("SessÃ£o nÃ£o encontrada.");
        }
        UsuarioEntity usuario = buscarUsuarioAutenticado(null, sessionToken);
        String novaSessao = usuarioSessionService.renovarSessao(usuario, sessionToken);
        AssinaturaResponse assinatura = usuario.getEmpresa() == null
                ? null
                : assinaturaService.buscarAtualResponsePorEmpresa(usuario.getEmpresa().getId());
        PagamentoPlanoResponse pagamentoPlano = usuario.getEmpresa() == null
                ? null
                : pagamentoService.buscarUltimoPagamentoPlanoPendente(usuario.getEmpresa().getId()).orElse(null);
        String statusConta = calcularStatusConta(usuario, assinatura);
        String motivoInatividade = calcularMotivoInatividade(usuario, assinatura);
        return new RefreshResponse("Sessao renovada com sucesso.", mapper.toResponse(usuario), assinatura, pagamentoPlano, statusConta, novaSessao, motivoInatividade);
    }

    @Transactional(readOnly = true)
    public UsuarioEntity buscarUsuarioAutenticado(Long usuarioId) {
        throw new SessaoExpiradaException("UsuÃ¡rio autenticado obrigatÃ³rio.");
    }

    @Transactional(readOnly = true)
    public UsuarioEntity buscarUsuarioAutenticado(Long usuarioId, String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new SessaoExpiradaException("SessÃ£o nÃ£o encontrada.");
        }
        UsuarioEntity usuario = usuarioRepository.findBySessaoAtiva(sessionToken)
                .orElseThrow(() -> new SessaoExpiradaException("UsuÃ¡rio autenticado invÃ¡lido."));
        if (usuarioId != null && !usuario.getId().equals(usuarioId)) {
            log.warn("Header X-Usuario-Id divergente da sessao. header={}, sessao={}", usuarioId, usuario.getId());
        }
        if (usuario.getStatus() != StatusUsuario.ATIVO || usuario.getStatus() == StatusUsuario.REMOVIDO) {
            throw new BusinessException("UsuÃ¡rio inativo.");
        }
        if (usuario.getPerfil() != PerfilUsuario.SUPER_ADMIN
                && usuario.getEmpresa() != null
                && usuario.getEmpresa().getStatus() != StatusEmpresa.ATIVA) {
            throw new BusinessException("Conta indisponÃ­vel. Entre em contato com o suporte.");
        }
        if (usuario.getPerfil() != PerfilUsuario.SUPER_ADMIN
                && usuario.getEmpresa() != null) {
            garantirMembresiaAtivaOuCriar(usuario);
        }
        return usuario;
    }

    private void garantirMembresiaAtivaOuCriar(UsuarioEntity usuario) {
        List<com.minhaempresa.gendaz.membresia.entity.MembresiaEntity> membros = membresiaRepository.findAllByEmpresaIdAndUsuarioId(usuario.getEmpresa().getId(), usuario.getId());
        if (membros.isEmpty()) {
            membresiaRepository.save(com.minhaempresa.gendaz.membresia.entity.MembresiaEntity.builder()
                    .usuario(usuario)
                    .empresa(usuario.getEmpresa())
                    .status(StatusMembresia.ACTIVE)
                    .funcao(usuario.getPerfil() == PerfilUsuario.DONO
                            ? com.minhaempresa.gendaz.membresia.enums.FuncaoMembresia.OWNER
                            : com.minhaempresa.gendaz.membresia.enums.FuncaoMembresia.MEMBER)
                    .owner(usuario.getPerfil() == PerfilUsuario.DONO)
                    .build());
            return;
        }
        if (membros.size() > 1) {
            throw new ConflictException("Dados de membresia duplicados. Contate o suporte para regularizacao.");
        }
        if (membros.get(0).getStatus() != StatusMembresia.ACTIVE) {
            throw new BusinessException("UsuÃ¡rio sem membresia ativa.");
        }
    }
    private String calcularStatusConta(UsuarioEntity usuario, AssinaturaResponse assinatura) {
        if (usuario.getEmpresa() == null) {
            return "ACTIVE";
        }
        if (usuario.getEmpresa().getStatus() == StatusEmpresa.PENDENTE_PAGAMENTO) {
            return "ACCOUNT_PENDING_PAYMENT";
        }
        if (assinatura != null && assinatura.status() == StatusAssinatura.EXPIRADA) {
            return "ACCOUNT_INACTIVE";
        }
        if (usuario.getEmpresa().getStatus() != StatusEmpresa.ATIVA) {
            return "ACCOUNT_INACTIVE";
        }
        return "ACTIVE";
    }

    private String calcularMotivoInatividade(UsuarioEntity usuario, AssinaturaResponse assinatura) {
        if (usuario.getEmpresa() == null) {
            return null;
        }
        if (usuario.getEmpresa().getStatus() == StatusEmpresa.PENDENTE_PAGAMENTO) {
            return "PAGAMENTO_PENDENTE";
        }
        if (assinatura != null && assinatura.status() == StatusAssinatura.EXPIRADA) {
            return "PAGAMENTO_PENDENTE";
        }
        if (usuario.getEmpresa().getStatus() != StatusEmpresa.ATIVA) {
            return usuario.getEmpresa().getStatus() == StatusEmpresa.BLOQUEADA ? "ADMIN_SUSPENSAO" : "PAGAMENTO_PENDENTE";
        }
        return null;
    }

    private CadastroContaCriada criarContaBase(CriarContaRequest request, String email, String telefone, String nomeEmpresa, String nomeProprietario, String documento) {
        validarCadastro(request);
        if (empresaRepository.existsByTelefone(telefone)) {
            throw new ConflictException("Este numero ja esta cadastrado.");
        }
        if (empresaRepository.findByNomeFantasiaNormalizado(nomeEmpresa).isPresent()) {
            throw new ConflictException("Este nome de empresa ja esta cadastrado.");
        }
        if (!documento.isBlank() && empresaRepository.existsByDocumento(documento)) {
            throw new ConflictException("Este documento ja esta cadastrado.");
        }
        validarEmailDisponivelParaPainel(email);

            PlanoEntity planoEscolhido = planoService.buscarPorNomePermitido(request.plano());
            boolean cadastroPro = "PRO".equalsIgnoreCase(planoEscolhido.getNome());
            EmpresaEntity empresa = empresaRepository.save(EmpresaEntity.builder()
                .nomeFantasia(nomeEmpresa)
                .documento(documento.isBlank() ? null : documento)
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
        phoneNumberService.normalizarObrigatorio(request.telefone());
        if (normalizarTexto(request.nomeProprietario()).length() < 2 || normalizarTexto(request.nomeProprietario()).length() > 80) {
            throw new BusinessException("Nome do usuÃ¡rio deve ter entre 2 e 80 caracteres.");
        }
        if (normalizarTexto(request.nomeEmpresa()).length() < 2 || normalizarTexto(request.nomeEmpresa()).length() > 100) {
            throw new BusinessException("Nome da empresa deve ter entre 2 e 100 caracteres.");
        }
        if (normalizarEmail(request.email()).length() > 120) {
            throw new BusinessException("E-mail deve ter no maximo 120 caracteres.");
        }
        String documento = DocumentoUtils.normalizar(request.documentoNumero());
        if (!documento.isBlank()) {
            DocumentoUtils.validar(request.documentoTipo(), documento);
        }
        if (!request.senha().equals(request.confirmarSenha())) {
            throw new BusinessException("As senhas nÃ£o coincidem.");
        }
        passwordService.validarSenha(request.senha());
        if (!Boolean.TRUE.equals(request.aceiteTermos())) {
            throw new BusinessException("Aceite os termos para continuar.");
        }
    }

    private void validarEmailDisponivelParaPainel(String email) {
        List<UsuarioEntity> usuariosPainel = usuarioRepository.findUsuariosPainelByEmailIgnoreCase(email, PERFIS_PAINEL_DIRETOS);
        if (!usuariosPainel.isEmpty()) {
            throw new ConflictException("Este e-mail ja esta cadastrado.");
        }
    }

    private String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
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

    // Corrigido: logs estruturados de eventos de seguranca sem expor e-mail completo.
    private void logLoginFalhado(String email, int tentativas) {
        HttpServletRequest request = getCurrentRequest();
        log.warn("[SECURITY] Login falhado - Email: {}, IP: {}, Tentativa: {}, User-Agent: {}", mascararEmail(email), getCurrentClientIp(), tentativas, getUserAgent(request));
        securityMonitoringService.registrarEvento(
                "LOGIN_FALHADO",
                tentativas >= 3 ? "MEDIUM" : "LOW",
                request,
                mascararEmail(email),
                "tentativas=" + tentativas
        );
    }

    private void logLoginBemSucedido(String email) {
        HttpServletRequest request = getCurrentRequest();
        log.info("[SECURITY] Login bem-sucedido - Email: {}, IP: {}, User-Agent: {}", mascararEmail(email), getCurrentClientIp(), getUserAgent(request));
    }

    private void logAlteracaoSenha(Long usuarioId) {
        HttpServletRequest request = getCurrentRequest();
        log.info("[SECURITY] Alteracao de senha - UsuarioId: {}, IP: {}, User-Agent: {}", usuarioId, getCurrentClientIp(), getUserAgent(request));
    }

    private void logRecuperacaoSenha(String email) {
        HttpServletRequest request = getCurrentRequest();
        log.info("[SECURITY] Recuperacao de senha - Email: {}, IP: {}, User-Agent: {}", mascararEmail(email), getCurrentClientIp(), getUserAgent(request));
        securityMonitoringService.registrarEvento(
                "RECUPERACAO_SENHA_SOLICITADA",
                "LOW",
                request,
                mascararEmail(email),
                "solicitacao_recebida"
        );
    }

    private void registrarMonitoramentoRecuperacaoSenha(String email, String detalhe) {
        securityMonitoringService.registrarEvento(
                "RECUPERACAO_SENHA_SEM_CONTA",
                "LOW",
                getCurrentRequest(),
                mascararEmail(email),
                detalhe
        );
    }

    private String getUserAgent(HttpServletRequest request) {
        if (request == null) return "unknown";
        String userAgent = request.getHeader("User-Agent");
        return userAgent == null || userAgent.isBlank() ? "unknown" : userAgent;
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }

    private String getCurrentClientIp() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) return "unknown";
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip.split(",")[0].trim();
    }

    private record CadastroContaCriada(Long empresaId, UsuarioEntity usuario, AssinaturaEntity assinatura, boolean cadastroPro) {}
}


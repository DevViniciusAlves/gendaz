package com.minhaempresa.gendaz.auth.service;

import com.minhaempresa.gendaz.auth.config.MeuGendazSecurityProperties;
import com.minhaempresa.gendaz.auth.dto.AuthDtos.MeuGendazAuthResponse;
import com.minhaempresa.gendaz.auth.dto.AuthDtos.MeuGendazCodigoResponse;
import com.minhaempresa.gendaz.auth.entity.MeuGendazOtpChallengeEntity;
import com.minhaempresa.gendaz.auth.repository.MeuGendazOtpChallengeRepository;
import com.minhaempresa.gendaz.cliente.entity.ClienteEntity;
import com.minhaempresa.gendaz.cliente.repository.ClienteRepository;
import com.minhaempresa.gendaz.cliente.service.ClienteEmailBloqueadoService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.email.ResendEmailService;
import com.minhaempresa.gendaz.meugendazacesso.entity.MeuGendazAcessoEntity;
import com.minhaempresa.gendaz.meugendazacesso.repository.MeuGendazAcessoRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.SessaoExpiradaException;
import com.minhaempresa.gendaz.shared.security.PersistentRateLimitService;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeuGendazAuthService {
    private static final int CODIGO_TAMANHO = 6;

    private final MeuGendazAcessoRepository meuGendazAcessoRepository;
    private final MeuGendazOtpChallengeRepository challengeRepository;
    private final EmpresaRepository empresaRepository;
    private final ClienteRepository clienteRepository;
    private final ResendEmailService resendEmailService;
    private final UsuarioSessionService usuarioSessionService;
    private final ClienteEmailBloqueadoService clienteEmailBloqueadoService;
    private final MeuGendazSecurityProperties securityProperties;
    private final MeuGendazTokenHashService tokenHashService;
    private final PersistentRateLimitService persistentRateLimitService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public MeuGendazCodigoResponse solicitarCodigo(String slug, String email, String ip) {
        String normalizadoSlug = normalizarSlug(slug);
        String normalizadoEmail = normalizarEmail(email);

        Optional<EmpresaEntity> empresaOpt = empresaRepository.findByAgendamentoSlug(normalizadoSlug);
        if (empresaOpt.isEmpty() || normalizadoSlug.isBlank()) {
            // Para mitigar enumeração de slugs de empresa, retornamos o mesmo sucesso genérico
            return new MeuGendazCodigoResponse("Enviamos um codigo para o seu e-mail.", normalizadoEmail, false);
        }

        EmpresaEntity empresa = empresaOpt.get();
        clienteEmailBloqueadoService.validarAcesso(empresa.getId(), normalizadoEmail);
        aplicarRateLimitSolicitacao(empresa.getId(), normalizadoEmail, ip);

        LocalDateTime agora = LocalDateTime.now();
        MeuGendazOtpChallengeEntity challenge = challengeRepository.findByEmpresaIdAndEmailForUpdate(empresa.getId(), normalizadoEmail)
                .orElseGet(() -> MeuGendazOtpChallengeEntity.builder()
                        .empresa(empresa)
                        .email(normalizadoEmail)
                        .build());

        validarCooldownEJanela(challenge, agora);

        String codigo = gerarCodigo();
        String otpHash = tokenHashService.hashOtp(codigo, normalizadoEmail, empresa.getId());
        String nomeEmail = clienteRepository.findFirstByEmpresaIdAndEmailIgnoreCase(empresa.getId(), normalizadoEmail)
                .map(ClienteEntity::getNome)
                .orElseGet(() -> meuGendazAcessoRepository.findByEmpresaIdAndEmailIgnoreCase(empresa.getId(), normalizadoEmail)
                        .map(MeuGendazAcessoEntity::getNome)
                        .orElse(nomePadrao(normalizadoEmail)));

        boolean enviado = resendEmailService.enviarCodigoMeuGendaz(normalizadoEmail, nomeEmail, codigo);
        if (!enviado) {
            throw new BusinessException("Nao foi possivel enviar o codigo agora.");
        }

        registrarOtpEmitido(challenge, otpHash, agora);
        challengeRepository.save(challenge);

        log.info("[meu-gendaz] codigo enviado para {} empresaId={}", mascararEmail(normalizadoEmail), empresa.getId());
        return new MeuGendazCodigoResponse("Enviamos um codigo para o seu e-mail.", normalizadoEmail, false);
    }

    @Transactional
    public MeuGendazAuthResponse validarCodigo(String slug, String email, String codigo, String ip) {
        String normalizadoSlug = normalizarSlug(slug);
        Optional<EmpresaEntity> empresaOpt = empresaRepository.findByAgendamentoSlug(normalizadoSlug);
        if (empresaOpt.isEmpty() || normalizadoSlug.isBlank()) {
            throw new BusinessException("Solicite um novo codigo.");
        }

        EmpresaEntity empresa = empresaOpt.get();
        String normalizado = normalizarEmail(email);
        clienteEmailBloqueadoService.validarAcesso(empresa.getId(), normalizado);
        persistentRateLimitService.consumir("OTP_VALIDATE_IP:" + normalizarIp(ip), securityProperties.getOtp().getMaxValidatePerIp10m(), Duration.ofMinutes(10), securityProperties.getOtp().blockDuration());

        MeuGendazOtpChallengeEntity challenge = challengeRepository.findByEmpresaIdAndEmailForUpdate(empresa.getId(), normalizado)
                .orElseThrow(() -> new BusinessException("Solicite um novo codigo."));
        LocalDateTime agora = LocalDateTime.now();
        if (challenge.getBloqueadoAte() != null && agora.isBefore(challenge.getBloqueadoAte())) {
            throw new BusinessException("Muitas tentativas. Aguarde um momento e tente novamente.");
        }
        if (challenge.getOtpHash() == null || challenge.getOtpExpiraEm() == null || !agora.isBefore(challenge.getOtpExpiraEm())) {
            throw new BusinessException("O codigo expirou. Solicite um novo.");
        }
        String codigoInformado = limparCodigo(codigo);
        String hashInformado = tokenHashService.hashOtp(codigoInformado, normalizado, empresa.getId());
        if (!tokenHashService.matches(challenge.getOtpHash(), hashInformado)) {
            challenge.setTentativasFalhas(challenge.getTentativasFalhas() + 1);
            if (challenge.getTentativasFalhas() >= securityProperties.getOtp().getMaxAttempts()) {
                challenge.setOtpHash(null);
                challenge.setBloqueadoAte(agora.plus(securityProperties.getOtp().blockDuration()));
                challengeRepository.save(challenge);
                throw new BusinessException("Codigo bloqueado apos muitas tentativas. Solicite um novo codigo.");
            }
            challengeRepository.save(challenge);
            throw new BusinessException("Codigo invalido.");
        }

        challenge.setOtpHash(null);
        challenge.setOtpExpiraEm(null);
        challenge.setValidadoEm(agora);
        challenge.setTentativasFalhas(0);

        Optional<ClienteEntity> cliente = clienteRepository.findFirstByEmpresaIdAndEmailIgnoreCase(empresa.getId(), normalizado);
        if (cliente.isPresent()) {
            MeuGendazAcessoEntity acesso = buscarOuCriarAcessoDefinitivo(empresa, normalizado, cliente.get().getNome());
            challenge.setOnboardingSessionHash(null);
            challenge.setOnboardingSessionExpiraEm(null);
            challengeRepository.save(challenge);
            String sessionToken = usuarioSessionService.criarSessaoMeuGendaz(acesso);
            return new MeuGendazAuthResponse("Login realizado com sucesso.", normalizado, sessionToken, "ACTIVE");
        }

        String onboardingToken = UUID.randomUUID().toString();
        challenge.setOnboardingSessionHash(tokenHashService.hashToken(onboardingToken));
        challenge.setOnboardingSessionExpiraEm(agora.plus(securityProperties.getOnboarding().ttl()));
        challengeRepository.save(challenge);
        return new MeuGendazAuthResponse("Codigo validado. Complete seu cadastro.", normalizado, onboardingToken, "PENDING_REGISTRATION");
    }

    @Transactional
    public MeuGendazAuthResponse validarCodigo(String slug, String email, String codigo) {
        return validarCodigo(slug, email, codigo, "unknown");
    }

    @Transactional
    public MeuGendazAuthResponse refreshSessao(String slug, String sessionToken) {
        String normalizadoSlug = normalizarSlug(slug);
        Optional<EmpresaEntity> empresaOpt = empresaRepository.findByAgendamentoSlug(normalizadoSlug);
        if (empresaOpt.isEmpty() || normalizadoSlug.isBlank()) {
            throw new SessaoExpiradaException("Sessao invalida. Faca login novamente.");
        }
        EmpresaEntity empresa = empresaOpt.get();
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new SessaoExpiradaException("Sessao nao encontrada. Faca login novamente.");
        }
        MeuGendazAcessoEntity acesso = meuGendazAcessoRepository.findByEmpresaIdAndSessaoAtiva(empresa.getId(), sessionToken)
                .orElseThrow(() -> new SessaoExpiradaException("Sessao invalida. Faca login novamente."));
        if (acesso.getStatus() != StatusUsuario.ATIVO) {
            throw new BusinessException("Acesso inativo.");
        }
        if (!usuarioSessionService.sessaoValidaMeuGendaz(acesso.getId(), sessionToken, empresa.getId())) {
            throw new SessaoExpiradaException("Sessao invalida. Faca login novamente.");
        }
        String sessaoRenovada = usuarioSessionService.renovarSessaoMeuGendaz(acesso, sessionToken);
        return new MeuGendazAuthResponse("Sessao renovada com sucesso.", acesso.getEmail(), sessaoRenovada, "ACTIVE");
    }

    @Transactional
    public MeuGendazAcessoEntity buscarOuCriarAcessoDefinitivo(EmpresaEntity empresa, String email, String nome) {
        String normalizado = normalizarEmail(email);
        return meuGendazAcessoRepository.findByEmpresaIdAndEmailIgnoreCase(empresa.getId(), normalizado)
                .map(acesso -> {
                    if (nome != null && !nome.isBlank()) {
                        acesso.setNome(nome.trim());
                    }
                    if (acesso.getStatus() == null) {
                        acesso.setStatus(StatusUsuario.ATIVO);
                    }
                    return meuGendazAcessoRepository.save(acesso);
                })
                .orElseGet(() -> salvarAcessoDefinitivo(empresa, normalizado, nome));
    }

    private MeuGendazAcessoEntity salvarAcessoDefinitivo(EmpresaEntity empresa, String email, String nome) {
        MeuGendazAcessoEntity novoAcesso = MeuGendazAcessoEntity.builder()
                .nome(nome == null || nome.isBlank() ? nomePadrao(email) : nome.trim())
                .email(email)
                .status(StatusUsuario.ATIVO)
                .empresa(empresa)
                .build();
        try {
            return meuGendazAcessoRepository.save(novoAcesso);
        } catch (DataIntegrityViolationException ex) {
            return meuGendazAcessoRepository.findByEmpresaIdAndEmailIgnoreCase(empresa.getId(), email)
                    .orElseThrow(() -> ex);
        }
    }

    private void aplicarRateLimitSolicitacao(Long empresaId, String email, String ip) {
        persistentRateLimitService.consumir("OTP_EMAIL:" + empresaId + ":" + email, securityProperties.getOtp().getMaxRequestsPerEmailHour(), Duration.ofHours(1), securityProperties.getOtp().blockDuration());
        persistentRateLimitService.consumir("OTP_IP:" + normalizarIp(ip), securityProperties.getOtp().getMaxRequestsPerIp10m(), Duration.ofMinutes(10), securityProperties.getOtp().blockDuration());
    }

    private void validarCooldownEJanela(MeuGendazOtpChallengeEntity challenge, LocalDateTime agora) {
        if (challenge.getBloqueadoAte() != null && agora.isBefore(challenge.getBloqueadoAte())) {
            throw new BusinessException("Muitas tentativas. Aguarde um momento e tente novamente.");
        }
        if (challenge.getReenviarDisponivelEm() != null && agora.isBefore(challenge.getReenviarDisponivelEm())) {
            throw new BusinessException("Aguarde " + Math.max(1, Duration.between(agora, challenge.getReenviarDisponivelEm()).getSeconds()) + " segundos para solicitar um novo codigo.");
        }
    }

    private void registrarOtpEmitido(MeuGendazOtpChallengeEntity challenge, String otpHash, LocalDateTime agora) {
        challenge.setOtpHash(otpHash);
        challenge.setOtpExpiraEm(agora.plus(securityProperties.getOtp().ttl()));
        challenge.setTentativasFalhas(0);
        challenge.setUltimaSolicitacao(agora);
        challenge.setReenviarDisponivelEm(agora.plus(securityProperties.getOtp().resendCooldown()));
        if (challenge.getJanelaSolicitacoesInicio() == null || !agora.isBefore(challenge.getJanelaSolicitacoesInicio().plusHours(1))) {
            challenge.setJanelaSolicitacoesInicio(agora);
            challenge.setSolicitacoesNaJanela(0);
        }
        challenge.setSolicitacoesNaJanela(challenge.getSolicitacoesNaJanela() + 1);
        challenge.setValidadoEm(null);
        challenge.setOnboardingSessionHash(null);
        challenge.setOnboardingSessionExpiraEm(null);
    }

    private EmpresaEntity buscarEmpresa(String slug) {
        String normalizado = normalizarSlug(slug);
        if (normalizado.isBlank()) {
            throw new BusinessException("Slug da empresa invalido.");
        }
        return empresaRepository.findByAgendamentoSlug(normalizado)
                .orElseThrow(() -> new BusinessException("Empresa nao encontrada."));
    }

    private String normalizarSlug(String slug) {
        return slug == null ? "" : slug.trim().toLowerCase();
    }

    private String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String normalizarIp(String ip) {
        return ip == null || ip.isBlank() ? "unknown" : ip.trim();
    }

    private String limparCodigo(String codigo) {
        return codigo == null ? "" : codigo.trim().replaceAll("\\D", "");
    }

    private String gerarCodigo() {
        int numero = secureRandom.nextInt(1_000_000);
        return String.format("%0" + CODIGO_TAMANHO + "d", numero);
    }

    private String nomePadrao(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return "Cliente";
        }
        String local = email.substring(0, email.indexOf('@')).trim();
        if (local.isBlank()) {
            return "Cliente";
        }
        return local.substring(0, 1).toUpperCase() + local.substring(1);
    }

    private String mascararEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return "***";
        }
        String[] partes = email.split("@", 2);
        String local = partes[0];
        String dominio = partes[1];
        String visivel = local.length() <= 2 ? local.charAt(0) + "*" : local.substring(0, 2) + "***";
        return visivel + "@" + dominio;
    }
}

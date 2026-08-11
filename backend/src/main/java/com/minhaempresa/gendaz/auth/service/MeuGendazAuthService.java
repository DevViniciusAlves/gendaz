package com.minhaempresa.gendaz.auth.service;

import com.minhaempresa.gendaz.auth.dto.AuthDtos.MeuGendazAuthResponse;
import com.minhaempresa.gendaz.auth.dto.AuthDtos.MeuGendazCodigoResponse;
import com.minhaempresa.gendaz.cliente.service.ClienteEmailBloqueadoService;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.email.ResendEmailService;
import com.minhaempresa.gendaz.meugendazacesso.entity.MeuGendazAcessoEntity;
import com.minhaempresa.gendaz.meugendazacesso.repository.MeuGendazAcessoRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.SessaoExpiradaException;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final int MAX_TENTATIVAS = 5;
    private static final Duration EXPIRACAO_CODIGO = Duration.ofMinutes(10);
    private static final Duration REENVIO_1 = Duration.ofSeconds(30);
    private static final Duration REENVIO_2 = Duration.ofSeconds(120);

    private final MeuGendazAcessoRepository meuGendazAcessoRepository;
    private final EmpresaRepository empresaRepository;
    private final ResendEmailService resendEmailService;
    private final UsuarioSessionService usuarioSessionService;
    private final ClienteEmailBloqueadoService clienteEmailBloqueadoService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, CodigoLoginState> estados = new ConcurrentHashMap<>();

    public MeuGendazCodigoResponse solicitarCodigo(String slug, String email, String ip) {
        EmpresaEntity empresa = buscarEmpresa(slug);
        String normalizado = normalizarEmail(email);
        clienteEmailBloqueadoService.validarAcesso(empresa.getId(), normalizado);
        MeuGendazAcessoEntity acesso = buscarAcesso(empresa, normalizado);
        CodigoLoginState state = estados.computeIfAbsent(chaveEstado(empresa.getId(), normalizado), key -> new CodigoLoginState());
        synchronized (state) {
            state.limparSeExpirado();
            LocalDateTime agora = LocalDateTime.now();
            if (state.bloqueadoAte != null && agora.isBefore(state.bloqueadoAte)) {
                throw new BusinessException("Voce fez muitas solicitacoes suspeitas. Aguarde um tempo e tente novamente.");
            }
            if (state.ultimaSolicitacao != null) {
                long segundos = Duration.between(state.ultimaSolicitacao, agora).getSeconds();
                if (state.solicitacoes == 0 && segundos < REENVIO_1.toSeconds()) {
                    throw new BusinessException("Aguarde 30 segundos para solicitar um novo codigo.");
                }
                if (state.solicitacoes == 1 && segundos < REENVIO_2.toSeconds()) {
                    throw new BusinessException("Aguarde 120 segundos para solicitar um novo codigo.");
                }
                if (state.solicitacoes >= 2) {
                    state.bloqueadoAte = agora.plusHours(24);
                    throw new BusinessException("Voce fez muitas solicitacoes suspeitas. Aguarde 24 horas para tentar novamente.");
                }
            }

            state.codigo = gerarCodigo();
            state.codigoHash = hashCodigo(state.codigo, normalizado, empresa.getId());
            state.geradoEm = agora;
            state.ultimaSolicitacao = agora;
            state.expiraEm = agora.plus(EXPIRACAO_CODIGO);
            state.reenviarDisponivelEm = agora.plus(state.solicitacoes == 0 ? REENVIO_1 : REENVIO_2);
            state.tentativas = 0;
            state.usado = false;
            state.solicitacoes++;

            boolean enviado = resendEmailService.enviarCodigoMeuGendaz(acesso.getEmail(), acesso.getNome(), state.codigo);
            if (!enviado) {
                throw new BusinessException("Nao foi possivel enviar o codigo agora.");
            }

            log.info("[meu-gendaz] codigo enviado para {}", mascararEmail(normalizado));
            return new MeuGendazCodigoResponse("Enviamos um codigo para o seu e-mail.", normalizado, false);
        }
    }

    @Transactional
    public MeuGendazAuthResponse validarCodigo(String slug, String email, String codigo) {
        EmpresaEntity empresa = buscarEmpresa(slug);
        String normalizado = normalizarEmail(email);
        clienteEmailBloqueadoService.validarAcesso(empresa.getId(), normalizado);
        MeuGendazAcessoEntity acesso = buscarAcesso(empresa, normalizado);
        CodigoLoginState state = estados.get(chaveEstado(empresa.getId(), normalizado));
        if (state == null) {
            throw new BusinessException("Solicite um novo codigo.");
        }
        synchronized (state) {
            state.limparSeExpirado();
            LocalDateTime agora = LocalDateTime.now();
            if (state.bloqueadoAte != null && agora.isBefore(state.bloqueadoAte)) {
                throw new BusinessException("Voce fez muitas solicitacoes suspeitas. Aguarde um tempo e tente novamente.");
            }
            if (state.usado) {
                throw new BusinessException("Este codigo ja foi utilizado.");
            }
            if (state.expiraEm == null || agora.isAfter(state.expiraEm)) {
                throw new BusinessException("O codigo expirou. Solicite um novo.");
            }

            String codigoInformado = limparCodigo(codigo);
            if (!state.codigoHash.equals(hashCodigo(codigoInformado, normalizado, empresa.getId()))) {
                state.tentativas++;
                if (state.tentativas >= MAX_TENTATIVAS) {
                    state.usado = true;
                    throw new BusinessException("Codigo bloqueado apos muitas tentativas. Solicite um novo codigo.");
                }
                throw new BusinessException("Codigo invalido.");
            }

            state.usado = true;
            String sessionToken = usuarioSessionService.criarSessaoMeuGendaz(acesso);
            return new MeuGendazAuthResponse("Login realizado com sucesso.", normalizado, sessionToken, "ACTIVE");
        }
    }

    /**
     * Renova/valida a sessÃ£o do Meu Gendaz de forma idempotente (mesmo padrÃ£o do painel Gendaz),
     * mas usando o slot prÃ³prio de sessÃ£o do Meu Gendaz (nÃ£o interfere no painel).
     * Resolve o usuÃ¡rio pelo token informado â€” nÃ£o depende do e-mail no request.
     * Se o token informado ainda Ã© o ativo, mantÃ©m o mesmo token â€” evita race de rotaÃ§Ã£o
     * em refreshes concorrentes (ex: F5). SÃ³ gera um token novo se o informado nÃ£o for mais o ativo.
     */
    @Transactional
    public MeuGendazAuthResponse refreshSessao(String slug, String sessionToken) {
        EmpresaEntity empresa = buscarEmpresa(slug);
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

    private EmpresaEntity buscarEmpresa(String slug) {
        String normalizado = normalizarSlug(slug);
        if (normalizado.isBlank()) {
            throw new BusinessException("Slug da empresa invalido.");
        }
        return empresaRepository.findByAgendamentoSlug(normalizado)
                .orElseThrow(() -> new BusinessException("Empresa nao encontrada."));
    }

    private MeuGendazAcessoEntity buscarAcesso(EmpresaEntity empresa, String email) {
        return meuGendazAcessoRepository.findByEmpresaIdAndEmailIgnoreCase(empresa.getId(), email)
                .orElseGet(() -> salvarAcesso(empresa, email));
    }

    private MeuGendazAcessoEntity salvarAcesso(EmpresaEntity empresa, String email) {
        MeuGendazAcessoEntity novoAcesso = MeuGendazAcessoEntity.builder()
                .nome(nomePadrao(email))
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

    private String normalizarSlug(String slug) {
        return slug == null ? "" : slug.trim().toLowerCase();
    }

    private String chaveEstado(Long empresaId, String email) {
        return empresaId + ":" + email;
    }

    private String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String limparCodigo(String codigo) {
        return codigo == null ? "" : codigo.trim().replaceAll("\\D", "");
    }

    private String gerarCodigo() {
        int numero = secureRandom.nextInt(1_000_000);
        return String.format("%06d", numero);
    }

    private String hashCodigo(String codigo, String email, Long empresaId) {
        return Integer.toHexString((codigo + ":" + email + ":" + empresaId).hashCode());
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

    private static final class CodigoLoginState {
        private String codigo;
        private String codigoHash;
        private LocalDateTime geradoEm;
        private LocalDateTime expiraEm;
        private LocalDateTime ultimaSolicitacao;
        private LocalDateTime reenviarDisponivelEm;
        private LocalDateTime bloqueadoAte;
        private int tentativas;
        private int solicitacoes;
        private boolean usado;

        private void limparSeExpirado() {
            if (expiraEm != null && LocalDateTime.now().isAfter(expiraEm)) {
                codigo = null;
                codigoHash = null;
                geradoEm = null;
                expiraEm = null;
                tentativas = 0;
                usado = false;
            }
        }
    }
}


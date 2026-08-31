package com.minhaempresa.gendaz.shared.security;

import com.minhaempresa.gendaz.admin.entity.AdminImpersonationSessionEntity;
import com.minhaempresa.gendaz.admin.service.AdminImpersonationService;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.CookieHelper;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class GendazSessionAuthenticationFilter extends OncePerRequestFilter {
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/api/public/",
            "/api/pagamentos/planos/webhook",
            "/api/pagamentos/webhook/stripe"
    );

    private final UsuarioRepository usuarioRepository;
    private final AdminImpersonationService adminImpersonationService;

    @Value("${FRONTEND_URL:https://gendaz.site}")
    private String frontendUrl;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || uri.startsWith("/api/meu-gendaz/")
                || uri.startsWith("/api/admin")
                || isPublicRoute(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        UsuarioEntity usuario = null;
        try {
            String session = CookieHelper.lerCookie(request, "Gendaz_session").orElse(null);
            boolean impersonation = false;
            AdminImpersonationSessionEntity impersonationSession = null;

            if (session != null && !session.isBlank()) {
                usuario = usuarioRepository.findBySessaoAtiva(session).orElse(null);
                if (usuario == null) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Sessao invalida.");
                    return;
                }
            } else {
                String impersonationToken = CookieHelper.lerCookie(request, "Gendaz_impersonation_session").orElse(null);
                impersonationSession = adminImpersonationService.validar(impersonationToken).orElse(null);
                if (impersonationSession != null) {
                    usuario = usuarioRepository.findByIdComEmpresa(impersonationSession.getUsuarioImpersonadoId()).orElse(null);
                    impersonation = true;
                }
            }

            if (usuario == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Sessao nao encontrada.");
                return;
            }
            if (!usuarioAtivo(usuario, request)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Usuario ou conta indisponivel.");
                return;
            }
            if (impersonation && (usuario.getEmpresa() == null || !impersonationSession.getEmpresaId().equals(usuario.getEmpresa().getId()))) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Sessao de impersonacao invalida.");
                return;
            }
            if (!origemPermitida(request)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Origem nao permitida.");
                return;
            }

            if (usuario.getEmpresa() != null) {
                CompanyContext.setCompanyId(usuario.getEmpresa().getId());
            }
            // Para SUPER_ADMIN, nao setar CompanyContext (operacao sem empresa obrigatoria)
            // Usuario com empresa tera CompanyContext definido normalmente
            List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_" + usuario.getPerfil().name()));
            if (impersonation) {
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN_IMPERSONATION"));
                request.setAttribute("adminImpersonation", true);
                request.setAttribute("impersonationAdminId", impersonationSession.getAdminUsuarioId());
                request.setAttribute("impersonationSessionId", impersonationSession.getId());
                request.setAttribute("impersonationUsuarioId", impersonationSession.getUsuarioImpersonadoId());
                request.setAttribute("impersonationEmpresaId", impersonationSession.getEmpresaId());
            }
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    usuario.getId(),
                    null,
                    authorities
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            // Apenas limpar CompanyContext se usuario existir e tiver empresa definida
            // Para Super Admin (usuario sem empresa), nao limpar
            if (usuario != null && usuario.getEmpresa() != null) {
                CompanyContext.clear();
            }
        }
    }

    private boolean isPublicRoute(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        if (("GET".equalsIgnoreCase(method) && "/api/planos".equals(uri))
                || ("GET".equalsIgnoreCase(method) && ("/health".equals(uri) || "/api/health".equals(uri)))
                || ("GET".equalsIgnoreCase(method) && "/api/auth/csrf".equals(uri))
                || ("GET".equalsIgnoreCase(method) && "/api/usuarios/convites/publico".equals(uri))
                || ("POST".equalsIgnoreCase(method) && List.of(
                        "/api/auth/login",
                        "/api/auth/criar-conta",
                        "/api/auth/recuperar-senha",
                        "/api/auth/redefinir-senha",
                        "/api/auth/logout",
                        "/api/usuarios/convites/aceitar",
                        "/api/usuarios/convites/recusar"
                ).contains(uri))) {
            return true;
        }
        return PUBLIC_PREFIXES.stream().anyMatch(uri::startsWith);
    }

    private boolean usuarioAtivo(UsuarioEntity usuario, HttpServletRequest request) {
        if (usuario.getStatus() != StatusUsuario.ATIVO) {
            return false;
        }
        if (usuario.getPerfil() == PerfilUsuario.SUPER_ADMIN || usuario.getEmpresa() == null) {
            return true;
        }
        if (usuario.getEmpresa().getStatus() == StatusEmpresa.ATIVA) {
            return true;
        }
        // Permitir acesso apenas para rotas de reativação se a empresa estiver INATIVA
        if (usuario.getEmpresa().getStatus() == StatusEmpresa.INATIVA) {
            return isReactivationRoute(request);
        }
        // Empresa ENCERRADA permite somente a reativação explícita do dono.
        if (usuario.getEmpresa().getStatus() == StatusEmpresa.ENCERRADA) {
            return isReativarContaRoute(request);
        }
        // Empresa BLOQUEADA fica totalmente bloqueada
        return false;
    }

    private boolean isReativarContaRoute(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && "/api/lgpd/reativar-conta".equals(request.getRequestURI());
    }

    private boolean isReactivationRoute(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        
        // Rotas permitidas para reativação
        boolean isPlanoAtualRoute = "GET".equalsIgnoreCase(method) && uri.matches("/api/pagamentos/planos/empresa/\\d+/atual$");
        boolean isVerificarPagamentoRoute = "GET".equalsIgnoreCase(method) && uri.matches("/api/pagamentos/planos/empresa/\\d+/\\d+/verificar$");
        boolean isIniciarBasicoRoute = "POST".equalsIgnoreCase(method) && "/api/pagamentos/planos/basico/iniciar".equals(uri);
        boolean isIniciarProRoute = "POST".equalsIgnoreCase(method) && "/api/pagamentos/planos/pro/iniciar".equals(uri);
<<<<<<< HEAD
        
        return isPlanoAtualRoute || isVerificarPagamentoRoute || isIniciarBasicoRoute || isIniciarProRoute;
=======
        boolean isIniciarPlanoRoute = "POST".equalsIgnoreCase(method) && "/api/pagamentos/planos/iniciar".equals(uri);
        
        return isPlanoAtualRoute || isVerificarPagamentoRoute || isIniciarBasicoRoute || isIniciarProRoute || isIniciarPlanoRoute;
>>>>>>> origin/stage
    }

    private boolean origemPermitida(HttpServletRequest request) {
        if (!MUTATING_METHODS.contains(request.getMethod())) {
            return true;
        }
        String origem = request.getHeader("Origin");
        if (origem == null || origem.isBlank()) {
            origem = extrairOrigem(request.getHeader("Referer"));
        }
        if (origem == null || origem.isBlank()) {
            return false;
        }
        return List.of(
                frontendUrl,
                "https://gendaz.site",
                "https://www.gendaz.site",
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:5174",
                "http://127.0.0.1:5174"
        ).stream().filter(v -> v != null && !v.isBlank()).map(String::trim).anyMatch(origem::equalsIgnoreCase);
    }

    private String extrairOrigem(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            return uri.getPort() == -1
                    ? uri.getScheme() + "://" + uri.getHost()
                    : uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort();
        } catch (Exception ignored) {
            return null;
        }
    }
}

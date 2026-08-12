package com.minhaempresa.gendaz.shared.security;

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
        try {
            String session = CookieHelper.lerCookie(request, "Gendaz_session").orElse(null);
            if (session == null || session.isBlank()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Sessao nao encontrada.");
                return;
            }

            UsuarioEntity usuario = usuarioRepository.findBySessaoAtiva(session).orElse(null);
            if (usuario == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Sessao invalida.");
                return;
            }
            if (!usuarioAtivo(usuario)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Usuario ou conta indisponivel.");
                return;
            }
            if (!origemPermitida(request)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Origem nao permitida.");
                return;
            }

            if (usuario.getEmpresa() != null) {
                CompanyContext.setCompanyId(usuario.getEmpresa().getId());
            }
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    usuario.getId(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getPerfil().name()))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            CompanyContext.clear();
        }
    }

    private boolean isPublicRoute(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        if (("GET".equalsIgnoreCase(method) && ("/health".equals(uri) || "/api/health".equals(uri)))
                || ("GET".equalsIgnoreCase(method) && "/api/auth/csrf".equals(uri))
                || ("POST".equalsIgnoreCase(method) && List.of(
                        "/api/auth/login",
                        "/api/auth/criar-conta",
                        "/api/auth/recuperar-senha",
                        "/api/auth/redefinir-senha",
                        "/api/auth/logout"
                ).contains(uri))) {
            return true;
        }
        return PUBLIC_PREFIXES.stream().anyMatch(uri::startsWith);
    }

    private boolean usuarioAtivo(UsuarioEntity usuario) {
        if (usuario.getStatus() != StatusUsuario.ATIVO) {
            return false;
        }
        return usuario.getPerfil() == PerfilUsuario.SUPER_ADMIN
                || usuario.getEmpresa() == null
                || usuario.getEmpresa().getStatus() == StatusEmpresa.ATIVA;
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

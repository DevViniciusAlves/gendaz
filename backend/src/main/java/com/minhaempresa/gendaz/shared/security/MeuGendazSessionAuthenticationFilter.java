package com.minhaempresa.gendaz.shared.security;

import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.repository.EmpresaRepository;
import com.minhaempresa.gendaz.meugendazacesso.entity.MeuGendazAcessoEntity;
import com.minhaempresa.gendaz.meugendazacesso.repository.MeuGendazAcessoRepository;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.shared.CookieHelper;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
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
public class MeuGendazSessionAuthenticationFilter extends OncePerRequestFilter {
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final EmpresaRepository empresaRepository;
    private final MeuGendazAcessoRepository meuGendazAcessoRepository;

    @Value("${FRONTEND_URL:https://gendaz.site}")
    private String frontendUrl;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || !uri.startsWith("/api/meu-gendaz/")
                || isPublicRoute(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String slug = slugAtual(request);
            if (slug == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Slug da empresa nao informado.");
                return;
            }
            EmpresaEntity empresa = empresaRepository.findByAgendamentoSlug(slug).orElse(null);
            if (empresa == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Loja nao encontrada.");
                return;
            }
            String session = CookieHelper.lerCookie(request, nomeCookie(slug)).orElse(null);
            if (session == null || session.isBlank()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Sessao nao encontrada.");
                return;
            }
            MeuGendazAcessoEntity acesso = meuGendazAcessoRepository.findByEmpresaIdAndSessaoAtiva(empresa.getId(), session).orElse(null);
            if (acesso == null || acesso.getStatus() != StatusUsuario.ATIVO) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Sessao invalida.");
                return;
            }
            if (!origemPermitida(request)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Origem nao permitida.");
                return;
            }

            CompanyContext.setCompanyId(empresa.getId());
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    "meu-gendaz:" + acesso.getId(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_MEU_GENDAZ_CLIENTE"))
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
        if ("GET".equalsIgnoreCase(method) && uri.startsWith("/api/meu-gendaz/empresa/")) {
            return true;
        }
        if (("GET".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) && "/api/meu-gendaz/perfil".equals(uri)) {
            return true;
        }
        return "POST".equalsIgnoreCase(method) && List.of(
                "/api/meu-gendaz/auth/solicitar-codigo",
                "/api/meu-gendaz/auth/validar-codigo",
                "/api/meu-gendaz/auth/logout"
        ).contains(uri);
    }

    private String slugAtual(HttpServletRequest request) {
        String slug = request.getHeader("X-Meu-Gendaz-Slug");
        if (slug == null || slug.isBlank()) {
            return null;
        }
        return slug.trim().toLowerCase();
    }

    private String nomeCookie(String slug) {
        return "meu_gendaz_session_" + slug;
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

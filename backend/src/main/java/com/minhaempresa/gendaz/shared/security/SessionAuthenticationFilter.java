package com.minhaempresa.gendaz.shared.security;

import com.minhaempresa.gendaz.auth.service.AuthService;
import com.minhaempresa.gendaz.shared.CookieHelper;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {
    private static final List<String> ROTAS_PUBLICAS = List.of(
            "/health",
            "/api/health",
            "/api/auth/login",
            "/api/auth/csrf",
            "/api/auth/criar-conta",
            "/api/auth/recuperar-senha",
            "/api/auth/redefinir-senha",
            "/api/auth/logout",
            "/api/auth/refresh",
            "/api/meu-gendaz/auth/solicitar-codigo",
            "/api/meu-gendaz/auth/validar-codigo",
            "/api/meu-gendaz/auth/refresh",
            "/api/pagamentos/webhook",
            "/api/pagamentos/planos/webhook",
            "/api/pagamentos/planos/webhook/cakto"
    );

    private final AuthService authService;

    public SessionAuthenticationFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return ROTAS_PUBLICAS.stream().anyMatch(uri::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String sessionToken = CookieHelper.lerCookie(request, "Gendaz_session").orElse(null);
            if (sessionToken != null && !sessionToken.isBlank()) {
                try {
                    UsuarioEntity usuario = authService.buscarUsuarioAutenticado(null, sessionToken);
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    usuario,
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + perfilParaRole(usuario.getPerfil())))
                            )
                    );
                } catch (RuntimeException ignored) {
                    SecurityContextHolder.clearContext();
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String perfilParaRole(PerfilUsuario perfil) {
        if (perfil == null) {
            return "USER";
        }
        return perfil.name();
    }
}

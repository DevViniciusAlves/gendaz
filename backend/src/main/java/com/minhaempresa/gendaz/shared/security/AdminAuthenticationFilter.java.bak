package com.minhaempresa.gendaz.shared.security;

import com.minhaempresa.gendaz.admin.service.AdminSessionService;
import com.minhaempresa.gendaz.shared.CookieHelper;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AdminAuthenticationFilter extends OncePerRequestFilter {

    private final AdminSessionService adminSessionService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // Não filtrar se não for admin
        if (!uri.startsWith("/api/admin/")) {
            return true;
        }
        // Permitir login e /access sem autenticação via filtro (serão tratados pelo SecurityFilterChain ou auth controller)
        return uri.equals("/api/admin/auth/login") || uri.equals("/api/admin/access");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String token = request.getHeader("X-Admin-Token");
        if (token == null || token.isBlank()) {
            token = CookieHelper.lerCookie(request, "agendeasy_admin_session").orElse(null);
        }

        if (token != null && !token.isBlank()) {
            try {
                UsuarioEntity admin = adminSessionService.validarSessao(token);
                
                Authentication auth = new UsernamePasswordAuthenticationToken(
                        admin.getId(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception e) {
                // Sessão inválida/expirada, não autentica
            }
        }

        filterChain.doFilter(request, response);
    }
}

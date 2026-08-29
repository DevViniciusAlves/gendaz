package com.minhaempresa.gendaz.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminImpersonationGuardFilter extends OncePerRequestFilter {
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final List<String> SENSITIVE_PREFIXES = List.of(
            "/api/admin/",
            "/api/auth/trocar-senha",
            "/api/pagamentos/",
            "/api/usuarios/"
    );

    private final SecurityMonitoringService securityMonitoringService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod()) || !MUTATING_METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (Boolean.TRUE.equals(request.getAttribute("adminImpersonation")) && rotaSensivel(request)) {
            Long adminId = (Long) request.getAttribute("impersonationAdminId");
            Long usuarioId = (Long) request.getAttribute("impersonationUsuarioId");
            Long empresaId = (Long) request.getAttribute("impersonationEmpresaId");
            log.warn("[ADMIN_IMPERSONATION] BLOCKED_ACTION adminId={} usuarioId={} empresaId={} metodo={} rota={}",
                    adminId, usuarioId, empresaId, request.getMethod(), request.getRequestURI());
            securityMonitoringService.registrarEvento(
                    "ADMIN_IMPERSONATION_BLOCKED_ACTION",
                    "SECURITY",
                    request,
                    adminId == null ? null : String.valueOf(adminId),
                    "usuarioId=" + usuarioId + "; empresaId=" + empresaId + "; metodo=" + request.getMethod()
            );
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
            response.getWriter().write("{\"mensagem\":\"Acao nao permitida durante impersonacao administrativa.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean rotaSensivel(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if ("/api/admin/impersonation/end".equals(uri) || "/api/auth/logout".equals(uri)) {
            return false;
        }
        return SENSITIVE_PREFIXES.stream().anyMatch(uri::startsWith);
    }
}

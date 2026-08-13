package com.minhaempresa.gendaz.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoginDiagnosticFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(LoginDiagnosticFilter.class);
    private static final String LOGIN_URI = "/api/auth/login";
    private static final String CSRF_URI = "/api/auth/csrf";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!deveDiagnosticar(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Instant inicio = Instant.now();
        String metodo = request.getMethod();
        String uri = request.getRequestURI();
        if (LOGIN_URI.equals(uri)) {
            log.info("[LOGIN-HTTP] request chegou no backend method={} uri={} origin={} userAgent={} ip={} horario={} isOptions={} isPost={}",
                    metodo,
                    uri,
                    request.getHeader("Origin"),
                    request.getHeader("User-Agent"),
                    ip(request),
                    inicio,
                    "OPTIONS".equalsIgnoreCase(metodo),
                    "POST".equalsIgnoreCase(metodo));
            if ("POST".equalsIgnoreCase(metodo)) {
                log.info("[LOGIN-CSRF] POST /api/auth/login origin={} userAgent={} hasXsrfHeader={} hasXsrfCookie={}",
                        request.getHeader("Origin"),
                        request.getHeader("User-Agent"),
                        existeHeader(request, "X-XSRF-TOKEN"),
                        existeCookie(request, "XSRF-TOKEN"));
            }
        } else if (CSRF_URI.equals(uri)) {
            log.info("[LOGIN-CSRF] GET /api/auth/csrf chegou origin={} userAgent={} ip={} horario={}",
                    request.getHeader("Origin"),
                    request.getHeader("User-Agent"),
                    ip(request),
                    inicio);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            long tempoMs = Duration.between(inicio, Instant.now()).toMillis();
            if (LOGIN_URI.equals(uri)) {
                log.info("[LOGIN-HTTP] resposta status {} tempoMs={}", response.getStatus(), tempoMs);
            } else if (CSRF_URI.equals(uri)) {
                log.info("[LOGIN-CSRF] GET /api/auth/csrf resposta status {} tentouEnviarXsrfCookie={} tempoMs={}",
                        response.getStatus(),
                        tentouEnviarCookie(response, "XSRF-TOKEN"),
                        tempoMs);
            }
        }
    }

    private boolean deveDiagnosticar(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String metodo = request.getMethod();
        return (LOGIN_URI.equals(uri) && ("POST".equalsIgnoreCase(metodo) || "OPTIONS".equalsIgnoreCase(metodo)))
                || (CSRF_URI.equals(uri) && "GET".equalsIgnoreCase(metodo));
    }

    private boolean existeHeader(HttpServletRequest request, String nome) {
        String valor = request.getHeader(nome);
        return valor != null && !valor.isBlank();
    }

    private boolean existeCookie(HttpServletRequest request, String nome) {
        if (request.getCookies() == null) {
            return false;
        }
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (nome.equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean tentouEnviarCookie(HttpServletResponse response, String nome) {
        return response.getHeaders("Set-Cookie").stream()
                .anyMatch(header -> header != null && header.startsWith(nome + "="));
    }

    private String ip(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}

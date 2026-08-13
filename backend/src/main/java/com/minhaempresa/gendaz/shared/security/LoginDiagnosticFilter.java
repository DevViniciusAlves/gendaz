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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!deveDiagnosticar(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Instant inicio = Instant.now();
        String metodo = request.getMethod();
        log.info("[LOGIN-HTTP] request chegou no backend method={} uri={} origin={} userAgent={} ip={} horario={} isOptions={} isPost={}",
                metodo,
                request.getRequestURI(),
                request.getHeader("Origin"),
                request.getHeader("User-Agent"),
                ip(request),
                inicio,
                "OPTIONS".equalsIgnoreCase(metodo),
                "POST".equalsIgnoreCase(metodo));
        try {
            filterChain.doFilter(request, response);
        } finally {
            long tempoMs = Duration.between(inicio, Instant.now()).toMillis();
            log.info("[LOGIN-HTTP] resposta status {} tempoMs={}", response.getStatus(), tempoMs);
        }
    }

    private boolean deveDiagnosticar(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String metodo = request.getMethod();
        return LOGIN_URI.equals(uri)
                && ("POST".equalsIgnoreCase(metodo) || "OPTIONS".equalsIgnoreCase(metodo));
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

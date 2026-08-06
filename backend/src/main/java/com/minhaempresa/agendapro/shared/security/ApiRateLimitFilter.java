package com.minhaempresa.agendapro.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiRateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(ApiRateLimitFilter.class);
    private static final int LIMITE_JANELA = 10;
    private static final Duration JANELA = Duration.ofMinutes(2);

    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (deveIgnorar(request) || deveIgnorarAutenticacao(request)) {
            executarProximaCamada(request, response, filterChain);
            return;
        }

        try {
            String chave = chaveRateLimit(request);
            if (chave != null) {
                Janela janela = janelas.computeIfAbsent(chave, k -> new Janela(Instant.now()));
                synchronized (janela) {
                    if (janela.expirou()) {
                        janela.reiniciar();
                    }
                    if (janela.quantidade.incrementAndGet() > limiteDaRota(request)) {
                        response.setStatus(429);
                        response.setHeader("Retry-After", String.valueOf(Math.max(1, janela.segundosRestantes())));
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"mensagem\":\"Sistema está carregando. Aguarde um momento.\"}");
                        return;
                    }
                }

                limparJanelasVencidas();
            }
        } catch (Exception ex) {
            log.warn("Rate limit falhou para {} {}. Liberando requisicao.", request.getMethod(), request.getRequestURI(), ex);
        }

        executarProximaCamada(request, response, filterChain);
    }

    private void executarProximaCamada(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException ex) {
            log.error("Erro apos ApiRateLimitFilter em {} {}. Causa real sera exibida abaixo.",
                    request.getMethod(), request.getRequestURI(), ex);
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Erro runtime apos ApiRateLimitFilter em {} {}. Causa real sera exibida abaixo.",
                    request.getMethod(), request.getRequestURI(), ex);
            throw ex;
        }
    }

    private int limiteDaRota(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || path.isBlank()) {
            return LIMITE_JANELA;
        }
        if (deveIgnorarAutenticacao(path) || path.startsWith("/health") || path.startsWith("/actuator/health")) {
            return LIMITE_JANELA;
        }
        if (path.contains("/api/admin/auth/login")) {
            return 5;
        }
        if (path.contains("/api/auth/login") || path.contains("/api/auth/criar-conta")) {
            return 8;
        }
        if (path.contains("/api/auth/recuperar-senha") || path.contains("/api/auth/redefinir-senha")) {
            return 4;
        }
        if (path.contains("/api/auth/refresh")) {
            return 20;
        }
        return LIMITE_JANELA;
    }

    private String chaveRateLimit(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || path.isBlank()) {
            return null;
        }
        if (deveIgnorarAutenticacao(path) || path.startsWith("/health") || path.startsWith("/actuator/health")) {
            return null;
        }
        if (path.startsWith("/api/auth/") || path.startsWith("/api/admin/auth/")) {
            return ip(request) + ":" + path;
        }
        return null;
    }

    private boolean deveIgnorar(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(method)
                || path == null
                || path.isBlank()
                || path.startsWith("/health")
                || path.startsWith("/actuator/health");
    }

    private boolean deveIgnorarAutenticacao(HttpServletRequest request) {
        return deveIgnorarAutenticacao(request.getRequestURI());
    }

    private boolean deveIgnorarAutenticacao(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        return "/login".equals(path)
                || "/auth/login".equals(path)
                || "/api/auth/login".equals(path)
                || "/criar-conta".equals(path)
                || "/auth/criar-conta".equals(path)
                || "/api/auth/criar-conta".equals(path);
    }

    private String ip(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void limparJanelasVencidas() {
        Instant agora = Instant.now();
        janelas.entrySet().removeIf(entry -> entry.getValue().inicio.plus(JANELA).isBefore(agora));
    }

    private static final class Janela {
        private Instant inicio;
        private final AtomicInteger quantidade = new AtomicInteger(0);

        private Janela(Instant inicio) {
            this.inicio = inicio;
        }

        private boolean expirou() {
            return inicio.plus(JANELA).isBefore(Instant.now());
        }

        private void reiniciar() {
            inicio = Instant.now();
            quantidade.set(0);
        }

        private long segundosRestantes() {
            long restantes = Duration.between(Instant.now(), inicio.plus(JANELA)).getSeconds();
            return Math.max(1, restantes);
        }
    }
}

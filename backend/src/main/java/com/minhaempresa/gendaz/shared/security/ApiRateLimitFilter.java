package com.minhaempresa.gendaz.shared.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.minhaempresa.gendaz.auth.config.MeuGendazSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
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

    private final Cache<String, Janela> janelas;
    private final Duration janelaDuracao;
    private final int limitePadrao;
    private final ClientIpResolver clientIpResolver;

    public ApiRateLimitFilter(MeuGendazSecurityProperties properties, ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
        this.janelaDuracao = Duration.ofSeconds(properties.getRateLimit().getLocalWindowSeconds());
        this.limitePadrao = properties.getRateLimit().getLocalDefaultLimit();
        this.janelas = Caffeine.newBuilder()
                .maximumSize(properties.getRateLimit().getLocalMaximumSize())
                .expireAfterWrite(properties.getRateLimit().getLocalWindowSeconds(), TimeUnit.SECONDS)
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (deveIgnorar(request)) {
            executarProximaCamada(request, response, filterChain);
            return;
        }

        try {
            String chave = chaveRateLimit(request);
            if (chave != null) {
                Janela janela = janelas.get(chave, k -> new Janela(Instant.now()));
                synchronized (janela) {
                    if (janela.expirou(janelaDuracao)) {
                        janela.reiniciar();
                    }
                    if (janela.quantidade.incrementAndGet() > limiteDaRota(request)) {
                        escreverRateLimit(response, janela.segundosRestantes(janelaDuracao));
                        return;
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Rate limit local falhou para {} {}.", request.getMethod(), request.getRequestURI(), ex);
            if (endpointCritico(request)) {
                response.setStatus(503);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"mensagem\":\"Protecao temporariamente indisponivel. Tente novamente em instantes.\"}");
                return;
            }
        }

        executarProximaCamada(request, response, filterChain);
    }

    private void executarProximaCamada(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        filterChain.doFilter(request, response);
    }

    private int limiteDaRota(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (path == null || path.isBlank()) {
            return limitePadrao;
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
        if (path.contains("/api/meu-gendaz/auth/solicitar-codigo") || path.contains("/api/meu-gendaz/auth/validar-codigo")) {
            return 20;
        }
        if (isAgendamentoPublicoPost(path, method)) {
            return 20;
        }
        if (isAgendamentoPublicoGet(path, method)) {
            return 120;
        }
        return limitePadrao;
    }

    private String chaveRateLimit(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || path.isBlank() || path.startsWith("/health") || path.startsWith("/actuator/health")) {
            return null;
        }
        if (path.startsWith("/api/auth/")
                || path.startsWith("/api/admin/auth/")
                || path.startsWith("/api/meu-gendaz/auth/")
                || path.startsWith("/api/public/agendamento/")
                || path.startsWith("/api/agendamento-publico/")) {
            return clientIpResolver.resolve(request) + ":" + request.getMethod() + ":" + path;
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

    private boolean endpointCritico(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        return path != null && (path.contains("/api/meu-gendaz/auth/solicitar-codigo")
                || path.contains("/api/meu-gendaz/auth/validar-codigo")
                || isAgendamentoPublicoPost(path, method));
    }

    private boolean isAgendamentoPublicoPost(String path, String method) {
        return "POST".equalsIgnoreCase(method)
                && (path.startsWith("/api/public/agendamento/") || path.startsWith("/api/agendamento-publico/"));
    }

    private boolean isAgendamentoPublicoGet(String path, String method) {
        return "GET".equalsIgnoreCase(method)
                && (path.startsWith("/api/public/agendamento/") || path.startsWith("/api/agendamento-publico/"));
    }

    private void escreverRateLimit(HttpServletResponse response, long retryAfter) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(Math.max(1, retryAfter)));
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"mensagem\":\"Muitas tentativas. Aguarde um momento e tente novamente.\"}");
    }

    private static final class Janela {
        private Instant inicio;
        private final AtomicInteger quantidade = new AtomicInteger(0);

        private Janela(Instant inicio) {
            this.inicio = inicio;
        }

        private boolean expirou(Duration janelaDuracao) {
            return inicio.plus(janelaDuracao).isBefore(Instant.now());
        }

        private void reiniciar() {
            inicio = Instant.now();
            quantidade.set(0);
        }

        private long segundosRestantes(Duration janelaDuracao) {
            long restantes = Duration.between(Instant.now(), inicio.plus(janelaDuracao)).getSeconds();
            return Math.max(1, restantes);
        }
    }
}

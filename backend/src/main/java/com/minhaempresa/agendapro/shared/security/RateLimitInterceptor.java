package com.minhaempresa.agendapro.shared.security;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimitConfig rateLimitConfig;

    public RateLimitInterceptor(RateLimitConfig rateLimitConfig) {
        this.rateLimitConfig = rateLimitConfig;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        String method = request.getMethod();
        String ip = getClientIp(request);

        Optional<Long> usuarioIdOpt = getUserIdFromRequest(request);

        boolean allowed = false;
        String reason = "Unknown";

        if (isLoginEndpoint(path, method)) {
            Bucket bucket = rateLimitConfig.getLoginBucket(ip);
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (probe.isConsumed()) {
                allowed = true;
            } else {
                long waitForRefill = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
                response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitForRefill));
                reason = "Login rate limit exceeded (5 por hora)";
                log.warn("[rate-limit] login bloqueado: IP={} aguarde={}s", ip, waitForRefill);
            }
        } else if (isRegistrarEndpoint(path, method)) {
            Bucket bucket = rateLimitConfig.getRegistrarBucket(ip);
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (probe.isConsumed()) {
                allowed = true;
            } else {
                long waitForRefill = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
                response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitForRefill));
                reason = "Registrar rate limit exceeded (3 por dia)";
                log.warn("[rate-limit] registrar bloqueado: IP={} aguarde={}s", ip, waitForRefill);
            }
        } else if (isHorariosEndpoint(path, method) && usuarioIdOpt.isPresent()) {
            Bucket bucket = rateLimitConfig.getHorariosBucket(usuarioIdOpt.get());
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (probe.isConsumed()) {
                allowed = true;
            } else {
                long waitForRefill = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
                response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitForRefill));
                reason = "Horarios rate limit exceeded (10 por minuto)";
                log.warn("[rate-limit] horarios bloqueado: usuario={} aguarde={}s", usuarioIdOpt.get(), waitForRefill);
            }
        } else if (usuarioIdOpt.isPresent()) {
            Bucket bucket = rateLimitConfig.getApiBucket(usuarioIdOpt.get());
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (probe.isConsumed()) {
                allowed = true;
            }
        } else {
            allowed = true;
        }

        if (!allowed) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"erro\":\"" + reason + "\"}");
            return false;
        }

        return true;
    }

    private boolean isLoginEndpoint(String path, String method) {
        return path.contains("/api/auth/login") && "POST".equals(method);
    }

    private boolean isRegistrarEndpoint(String path, String method) {
        return path.contains("/api/auth/criar-conta") && "POST".equals(method);
    }

    private boolean isHorariosEndpoint(String path, String method) {
        return path.contains("/horarios-disponiveis") && "GET".equals(method);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip.split(",")[0].trim();
    }

    private Optional<Long> getUserIdFromRequest(HttpServletRequest request) {
        return Optional.empty();
    }
}

package com.minhaempresa.gendaz.shared.security;

import com.minhaempresa.gendaz.auth.service.AuthService;
import com.minhaempresa.gendaz.admin.service.AdminService;
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
    private final AuthService authService;
    private final AdminService adminService;

    public RateLimitInterceptor(RateLimitConfig rateLimitConfig, AuthService authService, AdminService adminService) {
        this.rateLimitConfig = rateLimitConfig;
        this.authService = authService;
        this.adminService = adminService;
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
            String email = request.getParameter("email");
            String senha = request.getParameter("senha");
            
            // Rate limit only counts FAILED login attempts (wrong credentials)
            // If credentials are valid, don't count toward limit
            boolean credenciaisValidas = false;
            if (email != null && !email.isBlank() && senha != null && !senha.isBlank()) {
                credenciaisValidas = authService.validarCredenciaisLogin(email, senha);
            }
            
            if (credenciaisValidas) {
                // Valid credentials - allow without consuming rate limit token
                allowed = true;
            } else {
                // Invalid credentials - consume rate limit token
                Bucket bucket = rateLimitConfig.getLoginBucket(ip);
                ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
                if (probe.isConsumed()) {
                    allowed = true;
                } else {
                    long waitForRefill = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
                    response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitForRefill));
                    reason = "Login rate limit exceeded (5 tentativas invalidas por hora)";
                    log.warn("[rate-limit] login bloqueado: IP={} aguarde={}s", ip, waitForRefill);
                }
            }
        } else if (isAdminLoginEndpoint(path, method)) {
            String email = request.getParameter("email");
            String senha = request.getParameter("senha");
            
            // Rate limit only counts FAILED admin login attempts
            boolean credenciaisValidas = false;
            if (email != null && !email.isBlank() && senha != null && !senha.isBlank()) {
                credenciaisValidas = adminService.validarCredenciaisAdmin(email, senha);
            }
            
            if (credenciaisValidas) {
                allowed = true;
            } else {
                Bucket bucket = rateLimitConfig.getLoginBucket(ip);
                ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
                if (probe.isConsumed()) {
                    allowed = true;
                } else {
                    long waitForRefill = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
                    response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitForRefill));
                    reason = "Admin login rate limit exceeded (5 tentativas invalidas por hora)";
                    log.warn("[rate-limit] admin login bloqueado: IP={} aguarde={}s", ip, waitForRefill);
                }
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
            } else {
                long waitForRefill = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
                response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitForRefill));
                reason = "Sistema estÃ¡ carregando. Aguarde um momento.";
                log.warn("[rate-limit] API geral bloqueada: usuario={} aguarde={}s", usuarioIdOpt.get(), waitForRefill);
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

    private boolean isAdminLoginEndpoint(String path, String method) {
        return path.contains("/api/admin/auth/login") && "POST".equals(method);
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
        String userId = request.getHeader("X-Usuario-Id");
        if (userId != null && !userId.isBlank()) {
            try {
                return Optional.of(Long.parseLong(userId.trim()));
            } catch (NumberFormatException e) {
                log.warn("[rate-limit] X-Usuario-Id invalido: {}", userId);
            }
        }
        return Optional.empty();
    }
}


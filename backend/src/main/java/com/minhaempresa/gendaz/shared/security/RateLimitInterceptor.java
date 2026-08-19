package com.minhaempresa.gendaz.shared.security;

import com.minhaempresa.gendaz.admin.service.AdminService;
import com.minhaempresa.gendaz.auth.service.AuthService;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimitConfig rateLimitConfig;
    private final AuthService authService;
    private final AdminService adminService;
    private final SecurityMonitoringService securityMonitoringService;
    private final ClientIpResolver clientIpResolver;

    public RateLimitInterceptor(RateLimitConfig rateLimitConfig, AuthService authService, AdminService adminService, SecurityMonitoringService securityMonitoringService, ClientIpResolver clientIpResolver) {
        this.rateLimitConfig = rateLimitConfig;
        this.authService = authService;
        this.adminService = adminService;
        this.securityMonitoringService = securityMonitoringService;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        String method = request.getMethod();
        String ip = clientIpResolver.resolve(request);
        Long usuarioId = getAuthenticatedUserId(request);

        boolean allowed = false;
        String reason = "Unknown";

        if (isLoginEndpoint(path, method)) {
            String email = request.getParameter("email");
            String senha = request.getParameter("senha");
            boolean credenciaisValidas = email != null && !email.isBlank() && senha != null && !senha.isBlank()
                    && authService.validarCredenciaisLogin(email, senha);
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
                    reason = "Login rate limit exceeded (5 tentativas por minuto)";
                    log.warn("[rate-limit] login bloqueado: IP={} aguarde={}s", ip, waitForRefill);
                    registrarRateLimit(request, ip, path, "RATE_LIMIT_LOGIN", "HIGH", waitForRefill);
                }
            }
        } else if (isAdminLoginEndpoint(path, method)) {
            String email = request.getParameter("email");
            String senha = request.getParameter("senha");
            boolean credenciaisValidas = email != null && !email.isBlank() && senha != null && !senha.isBlank()
                    && adminService.validarCredenciaisAdmin(email, senha);
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
                    reason = "Admin login rate limit exceeded (5 tentativas por minuto)";
                    log.warn("[rate-limit] admin login bloqueado: IP={} aguarde={}s", ip, waitForRefill);
                    registrarRateLimit(request, ip, path, "RATE_LIMIT_ADMIN_LOGIN", "CRITICAL", waitForRefill);
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
                reason = "Registrar rate limit exceeded (3 por minuto)";
                log.warn("[rate-limit] registrar bloqueado: IP={} aguarde={}s", ip, waitForRefill);
                registrarRateLimit(request, ip, path, "RATE_LIMIT_CADASTRO", "HIGH", waitForRefill);
            }
        } else if (isHorariosEndpoint(path, method) && usuarioId != null) {
            Bucket bucket = rateLimitConfig.getHorariosBucket(usuarioId);
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (probe.isConsumed()) {
                allowed = true;
            } else {
                long waitForRefill = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
                response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitForRefill));
                reason = "Horarios rate limit exceeded (10 por minuto)";
                log.warn("[rate-limit] horarios bloqueado: usuario={} aguarde={}s", usuarioId, waitForRefill);
                registrarRateLimit(request, ip, path, "RATE_LIMIT_HORARIOS", "HIGH", waitForRefill);
            }
        } else if (usuarioId != null) {
            Bucket bucket = rateLimitConfig.getApiBucket(usuarioId);
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (probe.isConsumed()) {
                allowed = true;
            } else {
                long waitForRefill = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
                response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitForRefill));
                reason = "Sistema está carregando. Aguarde um momento.";
                log.warn("[rate-limit] API geral bloqueada: usuario={} aguarde={}s", usuarioId, waitForRefill);
                registrarRateLimit(request, ip, path, "RATE_LIMIT_API", "HIGH", waitForRefill);
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

    private void registrarRateLimit(HttpServletRequest request, String ip, String path, String tipo, String severidade, long waitForRefill) {
        securityMonitoringService.registrarEvento(
                tipo,
                severidade,
                ip,
                request.getHeader("User-Agent"),
                path,
                "-",
                "retryAfterSeconds=" + waitForRefill
        );
    }

    private Long getAuthenticatedUserId(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long id) {
            return id;
        }
        if (principal instanceof Number number) {
            return number.longValue();
        }
        return null;
    }
}

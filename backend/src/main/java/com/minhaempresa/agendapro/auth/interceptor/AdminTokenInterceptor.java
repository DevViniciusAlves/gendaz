package com.minhaempresa.agendapro.auth.interceptor;

import com.minhaempresa.agendapro.admin.service.AdminService;
import com.minhaempresa.agendapro.shared.CookieHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AdminTokenInterceptor implements HandlerInterceptor {
    private final AdminService adminService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/admin/") || uri.equals("/api/admin/auth/login") || uri.equals("/api/admin/access")) {
            return true;
        }

        String token = CookieHelper.lerCookie(request, "agendeasy_admin_session").orElse(null);
        if (token != null && !token.isBlank()) {
            try {
                adminService.exigirAdmin(token);
                return true;
            } catch (RuntimeException ignored) {
            }
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        response.getWriter().write("{\"mensagem\":\"Acesso admin nao autorizado.\"}");
        return false;
    }
}

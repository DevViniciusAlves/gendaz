package com.minhaempresa.agendapro.auth.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AdminIpWhitelistInterceptor implements HandlerInterceptor {
    @Value("${ADMIN_ALLOWED_IPS:}")
    private String adminAllowedIps;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String ipRequisicao = extrairIpReal(request);
        Set<String> ipsPermitidos = carregarIpsPermitidos();

        if (ipsPermitidos.isEmpty()) {
            ocultarRota(response);
            return false;
        }

        if (ipRequisicao != null && ipsPermitidos.contains(ipRequisicao)) {
            return true;
        }

        ocultarRota(response);
        return false;
    }

    private Set<String> carregarIpsPermitidos() {
        Set<String> ips = new LinkedHashSet<>();
        if (adminAllowedIps == null || adminAllowedIps.isBlank()) {
            return ips;
        }

        Arrays.stream(adminAllowedIps.split(","))
                .map(String::trim)
                .filter(ip -> !ip.isBlank())
                .forEach(ips::add);
        return ips;
    }

    private String extrairIpReal(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }

    private void ocultarRota(HttpServletResponse response) throws IOException {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
}

package com.minhaempresa.gendaz.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SecurityMonitoringService {

    public void registrarEvento(
            String tipo,
            String severidade,
            String ip,
            String userAgent,
            String rota,
            String identificadorMascarado,
            String detalhe
    ) {
        log.warn("[SECURITY_MONITOR] tipo={} severidade={} ip={} rota={} identificador={} detalhe={} userAgent={}",
                safe(tipo),
                safe(severidade),
                safe(ip),
                safe(rota),
                safe(identificadorMascarado),
                safe(detalhe),
                sanitizeUserAgent(userAgent));
    }

    public void registrarEvento(
            String tipo,
            String severidade,
            HttpServletRequest request,
            String identificadorMascarado,
            String detalhe
    ) {
        registrarEvento(
                tipo,
                severidade,
                getClientIp(request),
                request == null ? null : request.getHeader("User-Agent"),
                request == null ? null : request.getRequestURI(),
                identificadorMascarado,
                detalhe
        );
    }

    public String mascararEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return "***";
        }
        String[] partes = email.trim().split("@", 2);
        String local = partes[0];
        String dominio = partes[1];
        String visivel = local.isBlank() ? "***" : local.length() <= 2 ? local.charAt(0) + "*" : local.substring(0, 2) + "***";
        return visivel + "@" + dominio;
    }

    public String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return ip == null || ip.isBlank() ? "unknown" : ip.split(",")[0].trim();
    }

    private String sanitizeUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "unknown";
        }
        String sanitized = userAgent.replaceAll("[\\r\\n\\t]", " ").trim();
        return sanitized.length() > 200 ? sanitized.substring(0, 200) : sanitized;
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replaceAll("[\\r\\n\\t]", " ").trim();
    }
}

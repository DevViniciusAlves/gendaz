package com.minhaempresa.gendaz.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {
    private final List<String> trustedProxyPrefixes;

    public ClientIpResolver(@Value("${security.trusted-proxy-prefixes:}") String trustedProxyPrefixes) {
        this.trustedProxyPrefixes = Arrays.stream(trustedProxyPrefixes.split(","))
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String remoteAddr = normalizarIp(request.getRemoteAddr());
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String primeiro = normalizarIp(forwarded.split(",")[0].trim());
            if (!primeiro.isBlank()) {
                return primeiro;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return normalizarIp(realIp.trim());
        }
        return remoteAddr;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }
        if (isLoopback(remoteAddr)) {
            return true;
        }
        return trustedProxyPrefixes.stream().anyMatch(remoteAddr::startsWith);
    }

    private boolean isLoopback(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isLoopbackAddress();
        } catch (Exception ignored) {
            return "127.0.0.1".equals(ip) || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip);
        }
    }

    private String normalizarIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }
        String valor = ip.trim();
        if (valor.startsWith("[") && valor.contains("]")) {
            valor = valor.substring(1, valor.indexOf(']'));
        }
        return valor;
    }
}

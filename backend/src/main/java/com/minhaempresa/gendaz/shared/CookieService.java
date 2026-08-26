package com.minhaempresa.gendaz.shared;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class CookieService {

    private final boolean isProd;

    public CookieService(@Value("${spring.profiles.active:dev}") String activeProfile) {
        this.isProd = "prod".equalsIgnoreCase(activeProfile);
    }

    public void adicionarCookie(HttpServletRequest request, HttpServletResponse response, String nome, String valor, int maxAge) {
        ResponseCookie cookie = ResponseCookie.from(nome, valor)
                .httpOnly(true)
                .secure(deveUsarSecure(request))
                .path("/")
                .sameSite("None")
                .maxAge(Duration.ofSeconds(maxAge))
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    public void limparCookie(HttpServletRequest request, HttpServletResponse response, String nome) {
        ResponseCookie cookie = ResponseCookie.from(nome, "")
                .httpOnly(true)
                .secure(deveUsarSecure(request))
                .path("/")
                .sameSite("None")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private boolean deveUsarSecure(HttpServletRequest request) {
        if (isProd) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null) {
            return "https".equalsIgnoreCase(forwardedProto);
        }
        String forwardedScheme = request.getHeader("X-Forwarded-Scheme");
        if (forwardedScheme != null) {
            return "https".equalsIgnoreCase(forwardedScheme);
        }
        return request.isSecure();
    }
}

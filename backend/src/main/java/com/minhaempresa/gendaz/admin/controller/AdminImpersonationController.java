package com.minhaempresa.gendaz.admin.controller;

import com.minhaempresa.gendaz.admin.service.AdminImpersonationService;
import com.minhaempresa.gendaz.shared.CookieHelper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/impersonation")
@RequiredArgsConstructor
public class AdminImpersonationController {
    private static final String COOKIE_NAME = "Gendaz_impersonation_session";
    private static final int MAX_AGE_SECONDS = 60 * 30;

    private final AdminImpersonationService service;

    @PostMapping("/start")
    public ResponseEntity<StartResponse> start(@Valid @RequestBody StartRequest body, HttpServletRequest request, HttpServletResponse response) {
        AdminImpersonationService.StartImpersonationResult result = service.iniciar(tokenAdmin(request), body.empresaId(), body.usuarioId(), request);
        adicionarCookie(request, response, COOKIE_NAME, result.rawToken(), MAX_AGE_SECONDS);
        return ResponseEntity.ok(new StartResponse(true, result.sessionId(), result.empresaId(), result.usuarioId(), true, result.expiraEm()));
    }

    @GetMapping("/current")
    public ResponseEntity<?> current(HttpServletRequest request) {
        return service.atual(CookieHelper.lerCookie(request, COOKIE_NAME).orElse(null))
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(Map.of("active", false)));
    }

    @PostMapping("/end")
    public ResponseEntity<Void> end(HttpServletRequest request, HttpServletResponse response) {
        service.encerrarPorToken(CookieHelper.lerCookie(request, COOKIE_NAME).orElse(null), request);
        limparCookie(request, response, COOKIE_NAME);
        return ResponseEntity.noContent().build();
    }

    private void adicionarCookie(HttpServletRequest request, HttpServletResponse response, String nome, String valor, int maxAge) {
        ResponseCookie cookie = ResponseCookie.from(nome, valor)
                .httpOnly(true)
                .secure(deveUsarSecure(request))
                .path("/")
                .sameSite("None")
                .maxAge(Duration.ofSeconds(maxAge))
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void limparCookie(HttpServletRequest request, HttpServletResponse response, String nome) {
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
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null) {
            return "https".equalsIgnoreCase(forwardedProto);
        }
        return request.isSecure();
    }

    private String tokenAdmin(HttpServletRequest request) {
        String headerToken = request.getHeader("X-Admin-Token");
        if (headerToken != null && !headerToken.isBlank()) {
            return headerToken;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("agendeasy_admin_session".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    public record StartRequest(@NotNull Long empresaId, @NotNull Long usuarioId) {}
    public record StartResponse(boolean ok, Long sessionId, Long empresaId, Long usuarioId, boolean modoImpersonacao, LocalDateTime expiraEm) {}
}

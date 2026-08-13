package com.minhaempresa.gendaz.admin.controller;

import com.minhaempresa.gendaz.admin.service.AdminImpersonationService;
import com.minhaempresa.gendaz.shared.CookieHelper;
import com.minhaempresa.gendaz.shared.CookieService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/impersonation")
@RequiredArgsConstructor
public class AdminImpersonationController {
    private static final String COOKIE_NAME = "Gendaz_impersonation_session";
    private static final int MAX_AGE_SECONDS = 60 * 30;

    private final AdminImpersonationService service;
    private final CookieService cookieService;

    @PostMapping("/start")
    public ResponseEntity<StartResponse> start(@Valid @RequestBody StartRequest body, HttpServletRequest request, HttpServletResponse response) {
        AdminImpersonationService.StartImpersonationResult result = service.iniciar(tokenAdmin(request), body.empresaId(), body.usuarioId(), request);
        cookieService.adicionarCookie(request, response, COOKIE_NAME, result.rawToken(), MAX_AGE_SECONDS);
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
        cookieService.limparCookie(request, response, COOKIE_NAME);
        return ResponseEntity.noContent().build();
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

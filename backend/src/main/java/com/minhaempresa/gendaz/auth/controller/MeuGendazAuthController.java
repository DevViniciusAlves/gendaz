package com.minhaempresa.gendaz.auth.controller;

import com.minhaempresa.gendaz.auth.dto.AuthDtos.MeuGendazAuthResponse;
import com.minhaempresa.gendaz.auth.dto.AuthDtos.MeuGendazCodigoResponse;
import com.minhaempresa.gendaz.auth.dto.AuthDtos.MeuGendazSolicitarCodigoRequest;
import com.minhaempresa.gendaz.auth.dto.AuthDtos.MeuGendazValidarCodigoRequest;
import com.minhaempresa.gendaz.auth.service.MeuGendazAuthService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CookieHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meu-gendaz/auth")
@RequiredArgsConstructor
public class MeuGendazAuthController {
    private static final int SESSION_COOKIE_MAX_AGE = (int) Duration.ofDays(90).getSeconds();
    private final MeuGendazAuthService authService;

    @PostMapping("/solicitar-codigo")
    public ResponseEntity<MeuGendazCodigoResponse> solicitarCodigo(
            @Valid @RequestBody MeuGendazSolicitarCodigoRequest request,
            HttpServletRequest http
    ) {
        MeuGendazCodigoResponse response = authService.solicitarCodigo(request.slug(), request.email(), getClientIp(http));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/validar-codigo")
    public ResponseEntity<MeuGendazAuthResponse> validarCodigo(
            @Valid @RequestBody MeuGendazValidarCodigoRequest request,
            HttpServletRequest http,
            HttpServletResponse response
    ) {
        MeuGendazAuthResponse auth = authService.validarCodigo(request.slug(), request.email(), request.codigo());
        String cookieName = nomeCookie(request.slug());
        adicionarCookie(http, response, cookieName, auth.sessionToken(), SESSION_COOKIE_MAX_AGE);
        // O sessionToken nÃ£o deve ser retornado no JSON para evitar armazenamento no client side.
        return ResponseEntity.ok(new MeuGendazAuthResponse(auth.mensagem(), auth.email(), "", auth.status()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<MeuGendazAuthResponse> refresh(
            HttpServletRequest http,
            HttpServletResponse response
    ) {
        String slug = http.getHeader("X-Meu-Gendaz-Slug");
        if (slug == null || slug.isBlank()) {
            throw new BusinessException("Slug da empresa invalido.");
        }
        String sessionToken = CookieHelper.lerCookie(http, nomeCookie(slug))
                .orElseThrow(() -> new BusinessException("Sessao expirada ou invalida."));
        MeuGendazAuthResponse auth = authService.refreshSessao(slug, sessionToken);
        String cookieName = nomeCookie(slug);
        adicionarCookie(http, response, cookieName, auth.sessionToken(), SESSION_COOKIE_MAX_AGE);
        // O sessionToken nÃ£o deve ser retornado no JSON para evitar armazenamento no client side.
        return ResponseEntity.ok(new MeuGendazAuthResponse(auth.mensagem(), auth.email(), "", auth.status()));
    }


    private void adicionarCookie(HttpServletRequest request, HttpServletResponse response, String nome, String valor, int maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(nome, valor)
                .httpOnly(true)
                .secure(deveUsarSecure(request))
                .path("/")
                .sameSite("None");
        ResponseCookie cookie = builder.maxAge(Duration.ofSeconds(maxAge)).build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private String nomeCookie(String slug) {
        String normalizado = slug == null ? "" : slug.trim().toLowerCase();
        if (normalizado.isBlank()) {
            throw new IllegalArgumentException("Slug da empresa invalido.");
        }
        return "meu_gendaz_session_" + normalizado;
    }

    private boolean deveUsarSecure(HttpServletRequest request) {
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null) {
            return "https".equalsIgnoreCase(forwardedProto);
        }
        return request.isSecure();
    }
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip.split(",")[0].trim();
    }
}


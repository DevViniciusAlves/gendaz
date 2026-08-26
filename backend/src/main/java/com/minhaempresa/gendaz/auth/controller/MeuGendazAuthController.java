package com.minhaempresa.gendaz.auth.controller;

import com.minhaempresa.gendaz.auth.dto.AuthDtos.*;
import com.minhaempresa.gendaz.auth.service.MeuGendazAuthService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CookieHelper;
import com.minhaempresa.gendaz.shared.CookieService;
import com.minhaempresa.gendaz.shared.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
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
    private static final int ONBOARDING_COOKIE_MAX_AGE = (int) Duration.ofMinutes(20).getSeconds();
    private final MeuGendazAuthService authService;
    private final CookieService cookieService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/solicitar-codigo")
    public ResponseEntity<MeuGendazCodigoResponse> solicitarCodigo(
            @Valid @RequestBody MeuGendazSolicitarCodigoRequest request,
            HttpServletRequest http
    ) {
        MeuGendazCodigoResponse response = authService.solicitarCodigo(request.slug(), request.email(), clientIpResolver.resolve(http));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/validar-codigo")
    public ResponseEntity<MeuGendazAuthResponse> validarCodigo(
            @Valid @RequestBody MeuGendazValidarCodigoRequest request,
            HttpServletRequest http,
            HttpServletResponse response
    ) {
        MeuGendazAuthResponse auth = authService.validarCodigo(request.slug(), request.email(), request.codigo(), clientIpResolver.resolve(http));
        String cookieName = nomeCookie(request.slug());
        String onboardingCookieName = nomeOnboardingCookie(request.slug());
        if ("PENDING_REGISTRATION".equals(auth.status())) {
            cookieService.limparCookie(http, response, cookieName);
            cookieService.adicionarCookie(http, response, onboardingCookieName, auth.sessionToken(), ONBOARDING_COOKIE_MAX_AGE);
        } else {
            cookieService.limparCookie(http, response, onboardingCookieName);
            cookieService.adicionarCookie(http, response, cookieName, auth.sessionToken(), SESSION_COOKIE_MAX_AGE);
        }
        // O sessionToken não deve ser retornado no JSON para evitar armazenamento no client side.
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
        cookieService.adicionarCookie(http, response, cookieName, auth.sessionToken(), SESSION_COOKIE_MAX_AGE);
        // O sessionToken não deve ser retornado no JSON para evitar armazenamento no client side.
        return ResponseEntity.ok(new MeuGendazAuthResponse(auth.mensagem(), auth.email(), "", auth.status()));
    }

    private String nomeCookie(String slug) {
        String normalizado = normalizarSlug(slug);
        return "meu_gendaz_session_" + normalizado;
    }

    private String nomeOnboardingCookie(String slug) {
        String normalizado = normalizarSlug(slug);
        return "meu_gendaz_onboarding_" + normalizado;
    }

    private String normalizarSlug(String slug) {
        String normalizado = slug == null ? "" : slug.trim().toLowerCase();
        if (normalizado.isBlank()) {
            throw new IllegalArgumentException("Slug da empresa invalido.");
        }
        return normalizado;
    }
}



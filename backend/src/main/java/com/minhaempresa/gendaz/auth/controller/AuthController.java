package com.minhaempresa.gendaz.auth.controller;

import com.minhaempresa.gendaz.auth.dto.AuthDtos.CriarContaRequest;
import com.minhaempresa.gendaz.auth.dto.AuthDtos.LoginRequest;
import com.minhaempresa.gendaz.auth.dto.AuthDtos.LoginResponse;
import com.minhaempresa.gendaz.auth.dto.AuthDtos.RefreshResponse;
import com.minhaempresa.gendaz.auth.dto.AuthDtos.RecuperacaoSenhaResponse;
import com.minhaempresa.gendaz.auth.dto.AuthDtos.RedefinirSenhaRequest;
import com.minhaempresa.gendaz.auth.dto.AuthDtos.SolicitarRecuperacaoSenhaRequest;
import com.minhaempresa.gendaz.auth.dto.AuthDtos.TrocarSenhaRequest;
import com.minhaempresa.gendaz.auth.dto.AuthDtos.TrocarSenhaResponse;
import com.minhaempresa.gendaz.auth.service.AuthService;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.CookieHelper;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private static final int SESSION_COOKIE_MAX_AGE = 60 * 60 * 24 * 30;
    private static final String SESSION_COOKIE = "meu_gendaz_session";
    private static final String LEGACY_SESSION_COOKIE = "agendapro_session";
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        try {
            LoginResponse login = authService.login(request);
            if (login.sessionToken() != null && login.usuario() != null) {
                limparCookie(http, response, LEGACY_SESSION_COOKIE);
                adicionarCookie(http, response, SESSION_COOKIE, login.sessionToken(), SESSION_COOKIE_MAX_AGE);
            }
            return ResponseEntity.ok(new LoginResponse(login.mensagem(), login.usuario(), login.assinatura(), login.pagamentoPlano(), login.statusConta(), null, login.motivoInatividade()));
        } catch (BusinessException ex) {
            if ("CAPTCHA_REQUIRED".equals(ex.getMessage())) {
                return ResponseEntity.status(403).body(Map.of(
                        "erro", "CAPTCHA_REQUIRED",
                        "mensagem", "CAPTCHA e obrigatorio apos 3 tentativas falhadas."
                ));
            }
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Erro real no POST /api/auth/login para email={}", mascararEmail(request.email()), ex);
            throw ex;
        }
    }

    @PostMapping("/criar-conta")
    public ResponseEntity<LoginResponse> criarConta(@Valid @RequestBody CriarContaRequest request, HttpServletRequest http, HttpServletResponse response) {
        try {
            LoginResponse login = authService.criarConta(request);
            if (login.sessionToken() != null && login.usuario() != null) {
                limparCookie(http, response, LEGACY_SESSION_COOKIE);
                adicionarCookie(http, response, SESSION_COOKIE, login.sessionToken(), SESSION_COOKIE_MAX_AGE);
            }
            return ResponseEntity.ok(new LoginResponse(login.mensagem(), login.usuario(), login.assinatura(), login.pagamentoPlano(), login.statusConta(), null, login.motivoInatividade()));
        } catch (RuntimeException ex) {
            log.error("Erro real no POST /api/auth/criar-conta para email={}", mascararEmail(request.email()), ex);
            throw ex;
        }
    }

    @PostMapping("/recuperar-senha")
    public ResponseEntity<RecuperacaoSenhaResponse> solicitarRecuperacao(@Valid @RequestBody SolicitarRecuperacaoSenhaRequest request) {
        authService.solicitarRecuperacaoSenha(request.email());
        return ResponseEntity.ok(new RecuperacaoSenhaResponse("Se o e-mail existir, voce recebera as instrucoes de recuperacao."));
    }

    @PostMapping("/redefinir-senha")
    public ResponseEntity<RecuperacaoSenhaResponse> redefinirSenha(@Valid @RequestBody RedefinirSenhaRequest request) {
        authService.redefinirSenha(request.token(), request.novaSenha(), request.confirmarNovaSenha());
        return ResponseEntity.ok(new RecuperacaoSenhaResponse("Senha redefinida com sucesso."));
    }

    @PutMapping("/trocar-senha")
    public ResponseEntity<TrocarSenhaResponse> trocarSenha(
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            HttpServletRequest http,
            HttpServletResponse response,
            @Valid @RequestBody TrocarSenhaRequest request
    ) {
        String sessionToken = CookieHelper.lerCookie(http, SESSION_COOKIE).orElse(null);
        authService.trocarSenha(authService.buscarUsuarioAutenticado(usuarioId, sessionToken).getId(), sessionToken, request.senhaAtual(), request.novaSenha(), request.confirmarNovaSenha());
        limparCookie(http, response, SESSION_COOKIE);
        limparCookie(http, response, LEGACY_SESSION_COOKIE);
        return ResponseEntity.ok(new TrocarSenhaResponse("Senha alterada com sucesso."));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            HttpServletRequest http,
            HttpServletResponse response
    ) {
        String sessionToken = CookieHelper.lerCookie(http, SESSION_COOKIE).orElse(null);
        try {
            if (sessionToken != null && !sessionToken.isBlank()) {
                authService.logout(authService.buscarUsuarioAutenticado(usuarioId, sessionToken).getId(), sessionToken);
            } else if (usuarioId != null) {
                authService.logout(usuarioId, null);
            }
        } catch (BusinessException ex) {
            log.debug("Logout sem sessao valida (best-effort): {}", ex.getMessage());
        }
        limparCookie(http, response, SESSION_COOKIE);
        limparCookie(http, response, LEGACY_SESSION_COOKIE);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            HttpServletRequest http,
            HttpServletResponse response
    ) {
        String sessionToken = CookieHelper.lerCookie(http, SESSION_COOKIE).orElse(null);
        RefreshResponse refresh = authService.refresh(sessionToken);
        if (refresh.sessionToken() != null) {
            limparCookie(http, response, LEGACY_SESSION_COOKIE);
            adicionarCookie(http, response, SESSION_COOKIE, refresh.sessionToken(), SESSION_COOKIE_MAX_AGE);
        }
        return ResponseEntity.ok(refresh);
    }

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken == null) {
            return ResponseEntity.ok(Map.of("token", ""));
        }
        return ResponseEntity.ok(Map.of("token", csrfToken.getToken()));
    }

    private void adicionarCookie(HttpServletRequest request, HttpServletResponse response, String nome, String valor, int maxAge) {
        ResponseCookie cookie = ResponseCookie.from(nome, valor)
                .httpOnly(true)
                .secure(deveUsarSecure(request))
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ofSeconds(maxAge))
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void limparCookie(HttpServletRequest request, HttpServletResponse response, String nome) {
        ResponseCookie cookie = ResponseCookie.from(nome, "")
                .httpOnly(true)
                .secure(deveUsarSecure(request))
                .path("/")
                .sameSite("Lax")
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

    private String mascararEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return "***";
        }
        String[] partes = email.trim().split("@", 2);
        String local = partes[0];
        String dominio = partes[1];
        String visivel = local.isBlank() ? "***" : local.length() <= 2 ? local.charAt(0) + "*" : local.substring(0, 2) + "***";
        return visivel + "@" + dominio;
    }
}


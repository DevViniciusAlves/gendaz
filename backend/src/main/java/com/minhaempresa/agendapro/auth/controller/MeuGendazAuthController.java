package com.minhaempresa.agendapro.auth.controller;

import com.minhaempresa.agendapro.auth.dto.AuthDtos.MeuGendazAuthResponse;
import com.minhaempresa.agendapro.auth.dto.AuthDtos.MeuGendazCodigoResponse;
import com.minhaempresa.agendapro.auth.dto.AuthDtos.MeuGendazSolicitarCodigoRequest;
import com.minhaempresa.agendapro.auth.dto.AuthDtos.MeuGendazValidarCodigoRequest;
import com.minhaempresa.agendapro.auth.service.MeuGendazAuthService;
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
        MeuGendazCodigoResponse response = authService.solicitarCodigo(request.email(), getClientIp(http));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/validar-codigo")
    public ResponseEntity<MeuGendazAuthResponse> validarCodigo(
            @Valid @RequestBody MeuGendazValidarCodigoRequest request,
            HttpServletRequest http,
            HttpServletResponse response
    ) {
        MeuGendazAuthResponse auth = authService.validarCodigo(request.email(), request.codigo());
        adicionarCookie(http, response, "meu_gendaz_session", auth.sessionToken(), SESSION_COOKIE_MAX_AGE);
        return ResponseEntity.ok(new MeuGendazAuthResponse(auth.mensagem(), auth.email(), null, auth.status()));
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

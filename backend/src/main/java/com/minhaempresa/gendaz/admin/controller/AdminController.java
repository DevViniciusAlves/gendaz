package com.minhaempresa.gendaz.admin.controller;

import com.minhaempresa.gendaz.admin.dto.AdminDtos.*;
import com.minhaempresa.gendaz.chamado.dto.ChamadoDtos.AtualizarChamadoRequest;
import com.minhaempresa.gendaz.admin.service.AdminService;
import com.minhaempresa.gendaz.pagamento.dto.PagamentoDtos.PagamentoPlanoResponse;
import com.minhaempresa.gendaz.profissional.dto.ProfissionalDtos.ProfissionalResponse;
import com.minhaempresa.gendaz.profissional.dto.ProfissionalDtos.SalvarProfissionalRequest;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private static final int SESSION_COOKIE_MAX_AGE = 60 * 60 * 24 * 30;
    private final AdminService adminService;

    @GetMapping("/access")
    public ResponseEntity<Void> access() {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/auth/refresh")
    public ResponseEntity<AdminLoginResponse> refresh(HttpServletRequest http) {
        UsuarioEntity admin = adminService.refresh(tokenAdmin(http));
        return ResponseEntity.ok(new AdminLoginResponse(null, new AdminUsuarioResponse(admin.getId(), admin.getNome(), admin.getEmail(), admin.getPerfil().name())));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        AdminLoginResponse login = adminService.login(request, ip(http), userAgent(http));
        if (login.token() != null) {
            adicionarCookie(http, response, "agendeasy_admin_session", login.token(), 900);
        }
        return ResponseEntity.ok(new AdminLoginResponse(null, login.admin()));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardResponse> dashboard(HttpServletRequest http) {
        return ResponseEntity.ok(adminService.dashboard(tokenAdmin(http)));
    }

    @GetMapping("/usuarios")
    public ResponseEntity<List<AdminEmpresaUsuarioResponse>> usuarios(HttpServletRequest http) {
        return ResponseEntity.ok(adminService.usuarios(tokenAdmin(http)));
    }

    @GetMapping("/pagamentos")
    public ResponseEntity<List<AdminPagamentoResponse>> pagamentos(
            HttpServletRequest http,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String plano
    ) {
        return ResponseEntity.ok(adminService.pagamentos(tokenAdmin(http), status, plano));
    }

    @PostMapping("/pagamentos/{id}/aprovar-manualmente")
    public ResponseEntity<PagamentoPlanoResponse> aprovarPagamentoManualmente(
            HttpServletRequest http,
            @PathVariable Long id,
            @Valid @RequestBody AprovarPagamentoManualRequest request
    ) {
        return ResponseEntity.ok(adminService.aprovarPagamentoManualmente(tokenAdmin(http), id, request, ip(http), userAgent(http)));
    }

    @PostMapping("/pagamentos/{id}/desaprovar-manualmente")
    public ResponseEntity<PagamentoPlanoResponse> desaprovarPagamentoManualmente(
            HttpServletRequest http,
            @PathVariable Long id,
            @Valid @RequestBody DesaprovarPagamentoManualRequest request
    ) {
        return ResponseEntity.ok(adminService.desaprovarPagamentoManualmente(tokenAdmin(http), id, request, ip(http), userAgent(http)));
    }

    @GetMapping("/logs")
    public ResponseEntity<List<AdminAuditLogResponse>> logs(HttpServletRequest http) {
        return ResponseEntity.ok(adminService.logs(tokenAdmin(http)));
    }

    @GetMapping("/configuracoes")
    public ResponseEntity<AdminConfigResponse> configuracoes(HttpServletRequest http) {
        return ResponseEntity.ok(adminService.configuracoes(tokenAdmin(http)));
    }

    @GetMapping("/chamados")
    public ResponseEntity<List<AdminChamadoResponse>> chamados(HttpServletRequest http) {
        return ResponseEntity.ok(adminService.chamados(tokenAdmin(http)));
    }

    @PatchMapping("/chamados/{chamadoId}")
    public ResponseEntity<AdminChamadoResponse> atualizarChamado(
            HttpServletRequest http,
            @PathVariable Long chamadoId,
            @Valid @RequestBody AtualizarChamadoRequest request
    ) {
        return ResponseEntity.ok(adminService.atualizarChamado(tokenAdmin(http), chamadoId, request, ip(http), userAgent(http)));
    }

    @PatchMapping("/chamados/{chamadoId}/status")
    public ResponseEntity<AdminChamadoResponse> atualizarStatusChamado(
            HttpServletRequest http,
            @PathVariable Long chamadoId,
            @Valid @RequestBody AtualizarChamadoRequest request
    ) {
        return ResponseEntity.ok(adminService.atualizarChamado(tokenAdmin(http), chamadoId, request, ip(http), userAgent(http)));
    }

    @PostMapping("/empresas/{empresaId}/impersonar")
    public ResponseEntity<ImpersonarResponse> impersonar(
            HttpServletRequest http,
            HttpServletResponse response,
            @PathVariable Long empresaId,
            @Valid @RequestBody(required = false) ImpersonarRequest request
    ) {
        ImpersonarResponse impersonacao = adminService.iniciarImpersonacao(tokenAdmin(http), empresaId, request, ip(http), userAgent(http));
        return ResponseEntity.ok(impersonacao);
    }

    @PostMapping("/empresas/{empresaId}/ativar")
    public ResponseEntity<AdminEmpresaUsuarioResponse> ativarEmpresa(
            HttpServletRequest http,
            @PathVariable Long empresaId,
            @Valid @RequestBody AdminAcaoEmpresaRequest request
    ) {
        return ResponseEntity.ok(adminService.ativarEmpresa(tokenAdmin(http), empresaId, request, ip(http), userAgent(http)));
    }

    @PostMapping("/empresas/{empresaId}/desativar")
    public ResponseEntity<AdminEmpresaUsuarioResponse> desativarEmpresa(
            HttpServletRequest http,
            @PathVariable Long empresaId,
            @Valid @RequestBody AdminAcaoEmpresaRequest request
    ) {
        return ResponseEntity.ok(adminService.desativarEmpresa(tokenAdmin(http), empresaId, request, ip(http), userAgent(http)));
    }

    @PutMapping("/empresas/{empresaId}")
    public ResponseEntity<AdminEmpresaUsuarioResponse> atualizarEmpresa(
            HttpServletRequest http,
            @PathVariable Long empresaId,
            @Valid @RequestBody AdminAtualizarEmpresaRequest request
    ) {
        return ResponseEntity.ok(adminService.atualizarEmpresa(tokenAdmin(http), empresaId, request, ip(http), userAgent(http)));
    }

    @GetMapping("/empresas/{empresaId}/profissionais")
    public ResponseEntity<List<ProfissionalResponse>> listarProfissionais(
            HttpServletRequest http,
            @PathVariable Long empresaId
    ) {
        return ResponseEntity.ok(adminService.listarProfissionais(tokenAdmin(http), empresaId));
    }

    @PutMapping("/empresas/{empresaId}/profissionais/{id}")
    public ResponseEntity<ProfissionalResponse> atualizarProfissional(
            HttpServletRequest http,
            @PathVariable Long empresaId,
            @PathVariable Long id,
            @Valid @RequestBody SalvarProfissionalRequest request
    ) {
        return ResponseEntity.ok(adminService.atualizarProfissional(tokenAdmin(http), id, request));
    }

    @DeleteMapping("/empresas/{empresaId}/profissionais/{id}")
    public ResponseEntity<Void> excluirProfissional(
            HttpServletRequest http,
            @PathVariable Long empresaId,
            @PathVariable Long id
    ) {
        adminService.excluirProfissional(tokenAdmin(http), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/impersonacoes/{sessionId}/encerrar")
    public ResponseEntity<Void> encerrarImpersonacao(
            HttpServletRequest http,
            HttpServletResponse response,
            @PathVariable Long sessionId
    ) {
        adminService.encerrarImpersonacao(tokenAdmin(http), sessionId, ip(http), userAgent(http));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/impersonacao/sair")
    public ResponseEntity<Void> encerrarImpersonacaoAlias(
            HttpServletRequest http,
            HttpServletResponse response,
            @RequestParam Long sessionId
    ) {
        adminService.encerrarImpersonacao(tokenAdmin(http), sessionId, ip(http), userAgent(http));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        limparCookie(request, response, "agendeasy_admin_session");
        return ResponseEntity.noContent().build();
    }

    private String ip(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String userAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
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
}


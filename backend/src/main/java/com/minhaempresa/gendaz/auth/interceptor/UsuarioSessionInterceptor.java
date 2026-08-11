package com.minhaempresa.gendaz.auth.interceptor;

import com.minhaempresa.gendaz.admin.service.AdminService;
import com.minhaempresa.gendaz.shared.CookieHelper;
import com.minhaempresa.gendaz.shared.CompanyContext;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class UsuarioSessionInterceptor implements HandlerInterceptor {
    private static final List<String> ROTAS_PUBLICAS = List.of(
            "/api/auth/login",
            "/api/auth/csrf",
            "/api/auth/criar-conta",
            "/api/auth/recuperar-senha",
            "/api/auth/redefinir-senha",
            "/api/public/",
            "/api/health",
            "/api/pagamentos/planos/webhook",
            "/api/pagamentos/planos/webhook/cakto",
            "/api/meu-gendaz/auth/solicitar-codigo",
            "/api/meu-gendaz/auth/validar-codigo",
            "/api/meu-gendaz/",
            "/api/meu-gendaz/servicos",
            "/api/meu-gendaz/profissionais",
            "/api/meu-gendaz/horarios-disponiveis"
    );

    private final UsuarioRepository usuarioRepository;
    private final AdminService adminService;
    @Value("${FRONTEND_URL:https://gendaz.site}")
    private String frontendUrl;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || deveIgnorar(request.getRequestURI())) {
            return true;
        }

        String sessao = CookieHelper.lerCookie(request, "meu_gendaz_session").orElse(null);

        if (isAdminSession(request)) {
            if (sessao != null && !sessao.isBlank()) {
                usuarioRepository.findBySessaoAtiva(sessao).ifPresent(this::registrarEmpresaAtual);
            }
            return true;
        }

        if (sessao == null || sessao.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Sessao nao encontrada.");
            return false;
        }

        Optional<UsuarioEntity> usuarioDaSessao = usuarioRepository.findBySessaoAtiva(sessao);
        if (usuarioDaSessao.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Sessao invalida.");
            return false;
        }
        if (!origemPermitida(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Origem nao permitida.");
            return false;
        }
        registrarEmpresaAtual(usuarioDaSessao.get());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CompanyContext.clear();
    }

    private boolean isAdminSession(HttpServletRequest request) {
        String adminToken = CookieHelper.lerCookie(request, "agendeasy_admin_session").orElse(null);
        if (adminToken == null || adminToken.isBlank()) {
            return false;
        }
        try {
            adminService.exigirAdmin(adminToken);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void registrarEmpresaAtual(UsuarioEntity usuario) {
        if (usuario != null && usuario.getEmpresa() != null) {
            CompanyContext.setCompanyId(usuario.getEmpresa().getId());
        }
    }

    private boolean origemPermitida(HttpServletRequest request) {
        String method = request.getMethod();
        if (!Set.of("POST", "PUT", "PATCH", "DELETE").contains(method)) {
            return true;
        }
        String origem = request.getHeader("Origin");
        if (origem == null || origem.isBlank()) {
            String referer = request.getHeader("Referer");
            if (referer == null || referer.isBlank()) {
                return true;
            }
            origem = extrairOrigem(referer);
        }
        if (origem == null || origem.isBlank()) {
            return true;
        }
        return List.of(
                frontendUrl,
                "https://gendaz.pages.dev",
                "https://gendaz-stage.onrender.com",
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:5174",
                "http://127.0.0.1:5174"
        ).stream().filter(v -> v != null && !v.isBlank()).map(String::trim).anyMatch(origem::equalsIgnoreCase);
    }

    private String extrairOrigem(String url) {
        try {
            URI uri = URI.create(url);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            int porta = uri.getPort();
            if (porta == -1) {
                return uri.getScheme() + "://" + uri.getHost();
            }
            return uri.getScheme() + "://" + uri.getHost() + ":" + porta;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean deveIgnorar(String uri) {
        return ROTAS_PUBLICAS.stream().anyMatch(uri::startsWith);
    }
}


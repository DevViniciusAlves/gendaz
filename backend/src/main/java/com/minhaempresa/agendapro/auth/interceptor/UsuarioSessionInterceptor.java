package com.minhaempresa.agendapro.auth.interceptor;

import com.minhaempresa.agendapro.admin.service.AdminService;
import com.minhaempresa.agendapro.auth.service.UsuarioSessionService;
import com.minhaempresa.agendapro.shared.CookieHelper;
import com.minhaempresa.agendapro.shared.CompanyContext;
import com.minhaempresa.agendapro.usuario.entity.UsuarioEntity;
import com.minhaempresa.agendapro.usuario.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class UsuarioSessionInterceptor implements HandlerInterceptor {
    private static final List<String> ROTAS_PUBLICAS = List.of(
            "/api/auth/login",
            "/api/auth/criar-conta",
            "/api/auth/recuperar-senha",
            "/api/auth/redefinir-senha",
            "/api/public/",
            "/api/health",
            "/api/pagamentos/planos/webhook",
            "/api/pagamentos/planos/webhook/cakto",
            "/api/meu-gendaz/auth/solicitar-codigo",
            "/api/meu-gendaz/auth/validar-codigo",
            "/api/meu-gendaz/servicos",
            "/api/meu-gendaz/profissionais",
            "/api/meu-gendaz/horarios-disponiveis"
    );

    private final UsuarioSessionService usuarioSessionService;
    private final UsuarioRepository usuarioRepository;
    private final AdminService adminService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        try {
            if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || deveIgnorar(request.getRequestURI())) {
                return true;
            }

            String usuarioIdHeader = request.getHeader("X-Usuario-Id");
            String sessao = CookieHelper.lerCookie(request, "agendapro_session").orElse(null);

            if (isAdminSession(request)) {
                if (usuarioIdHeader != null && !usuarioIdHeader.isBlank()) {
                    try {
                        usuarioRepository.findById(Long.valueOf(usuarioIdHeader)).ifPresent(this::registrarEmpresaAtual);
                    } catch (NumberFormatException ignored) {
                    }
                } else if (sessao != null && !sessao.isBlank()) {
                    usuarioRepository.findBySessaoAtiva(sessao).ifPresent(this::registrarEmpresaAtual);
                }
                return true;
            }

            if (usuarioIdHeader == null || usuarioIdHeader.isBlank()) {
                if (sessao != null && !sessao.isBlank()) {
                    usuarioRepository.findBySessaoAtiva(sessao).ifPresent(this::registrarEmpresaAtual);
                }
                return true;
            }

            try {
                Long usuarioId = Long.valueOf(usuarioIdHeader);
                usuarioRepository.findById(usuarioId).ifPresent(this::registrarEmpresaAtual);
                if (usuarioSessionService.sessaoValida(usuarioId, sessao)) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                return true;
            }

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
            response.getWriter().write("{\"mensagem\":\"Sua sessao foi encerrada porque sua conta foi acessada em outro dispositivo.\"}");
            return false;
        } finally {
            CompanyContext.clear();
        }
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
        if (usuario.getEmpresa() != null) {
            CompanyContext.setCompanyId(usuario.getEmpresa().getId());
        }
    }

    private boolean deveIgnorar(String uri) {
        return ROTAS_PUBLICAS.stream().anyMatch(uri::startsWith);
    }
}

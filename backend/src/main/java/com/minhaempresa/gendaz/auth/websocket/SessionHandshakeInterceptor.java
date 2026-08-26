package com.minhaempresa.gendaz.auth.websocket;

import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.shared.CookieHelper;
import com.minhaempresa.gendaz.shared.security.SecurityMonitoringService;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class SessionHandshakeInterceptor implements HandshakeInterceptor {
    private final UsuarioRepository usuarioRepository;
    private final SecurityMonitoringService securityMonitoringService;

    @Value("${FRONTEND_URL:https://gendaz.site}")
    private String frontendUrl;

    public SessionHandshakeInterceptor(UsuarioRepository usuarioRepository, SecurityMonitoringService securityMonitoringService) {
        this.usuarioRepository = usuarioRepository;
        this.securityMonitoringService = securityMonitoringService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequestWrapper)) {
            return false;
        }

        HttpServletRequest servletRequest = servletRequestWrapper.getServletRequest();
        String origin = servletRequest.getHeader("Origin");
        if (!origemPermitida(origin)) {
            registrarNegado(servletRequest, origin, "ORIGEM_NAO_PERMITIDA");
            return false;
        }

        String session = CookieHelper.lerCookie(servletRequest, "Gendaz_session").orElse(null);
        if (session == null || session.isBlank()) {
            registrarNegado(servletRequest, origin, "SESSAO_AUSENTE");
            return false;
        }

        UsuarioEntity usuario = usuarioRepository.findBySessaoAtiva(session).orElse(null);
        if (usuario == null) {
            registrarNegado(servletRequest, origin, "SESSAO_INVALIDA");
            return false;
        }
        if (!usuarioAtivo(usuario)) {
            registrarNegado(servletRequest, origin, "USUARIO_OU_EMPRESA_INATIVA");
            return false;
        }

        attributes.put("userId", usuario.getId());
        attributes.put("usuarioId", usuario.getId());
        attributes.put("empresaId", usuario.getEmpresa() == null ? null : usuario.getEmpresa().getId());
        attributes.put("perfil", usuario.getPerfil().name());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {}

    private boolean usuarioAtivo(UsuarioEntity usuario) {
        if (usuario.getStatus() != StatusUsuario.ATIVO) {
            return false;
        }
        return usuario.getPerfil() == PerfilUsuario.SUPER_ADMIN
                || usuario.getEmpresa() == null
                || usuario.getEmpresa().getStatus() == StatusEmpresa.ATIVA;
    }

    private boolean origemPermitida(String origem) {
        if (origem == null || origem.isBlank()) {
            return false;
        }
        return origensPermitidas().stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .anyMatch(origem::equalsIgnoreCase);
    }

    private List<String> origensPermitidas() {
        return List.of(
                frontendUrl,
                "https://gendaz.site",
                "https://www.gendaz.site",
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:5174",
                "http://127.0.0.1:5174"
        );
    }

    private void registrarNegado(HttpServletRequest request, String origin, String motivo) {
        securityMonitoringService.registrarEvento(
                "WEBSOCKET_HANDSHAKE_NEGADO",
                "HIGH",
                securityMonitoringService.getClientIp(request),
                request.getHeader("User-Agent"),
                request.getRequestURI(),
                "-",
                motivo + "; origin=" + mascararOrigem(origin)
        );
    }

    private String mascararOrigem(String origin) {
        if (origin == null || origin.isBlank()) {
            return "ausente";
        }
        try {
            URI uri = URI.create(origin);
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        } catch (Exception ignored) {
            return "invalida";
        }
    }
}


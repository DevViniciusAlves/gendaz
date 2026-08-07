package com.minhaempresa.agendapro.auth.websocket;

import com.minhaempresa.agendapro.shared.CookieHelper;
import com.minhaempresa.agendapro.usuario.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class SessionHandshakeInterceptor implements HandshakeInterceptor {
    private final UsuarioRepository usuarioRepository;

    public SessionHandshakeInterceptor(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest) {
            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
            CookieHelper.lerCookie(servletRequest, "agendapro_session")
                    .flatMap(usuarioRepository::findBySessaoAtiva)
                    .ifPresent(usuario -> attributes.put("userId", usuario.getId()));
        }
        return attributes.containsKey("userId");
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {}
}

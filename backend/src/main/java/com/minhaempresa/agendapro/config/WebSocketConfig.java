package com.minhaempresa.agendapro.config;

import com.minhaempresa.agendapro.auth.websocket.SessionHandshakeInterceptor;
import com.minhaempresa.agendapro.auth.websocket.SessionWebSocketHandler;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final SessionWebSocketHandler sessionWebSocketHandler;
    private final SessionHandshakeInterceptor sessionHandshakeInterceptor;
    @Value("${FRONTEND_URL:https://gendaz.site}")
    private String frontendUrl;

    public WebSocketConfig(SessionWebSocketHandler sessionWebSocketHandler, 
                           SessionHandshakeInterceptor sessionHandshakeInterceptor) {
        this.sessionWebSocketHandler = sessionWebSocketHandler;
        this.sessionHandshakeInterceptor = sessionHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(sessionWebSocketHandler, "/ws/session")
                .setAllowedOrigins(
                        frontendUrl,
                        "https://gendaz.site",
                        "https://www.gendaz.site",
                        "http://localhost:5173",
                        "http://127.0.0.1:5173",
                        "http://localhost:5174",
                        "http://127.0.0.1:5174"
                )
                .addInterceptors(sessionHandshakeInterceptor);
    }
}

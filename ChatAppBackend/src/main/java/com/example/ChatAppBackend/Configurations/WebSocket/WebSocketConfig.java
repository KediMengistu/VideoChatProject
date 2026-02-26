package com.example.ChatAppBackend.Configurations.WebSocket;

import com.example.ChatAppBackend.WebSocket.SignalingWebSocketHandler;
import com.example.ChatAppBackend.WebSocket.TicketHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SignalingWebSocketHandler signalingWebSocketHandler;
    private final TicketHandshakeInterceptor ticketHandshakeInterceptor;

    @Value("${ws.signaling.path:/ws/signaling}")
    private String signalingPath;

    @Value("${ws.allowed-origin-patterns:*}")
    private String allowedOriginPatterns;

    public WebSocketConfig(SignalingWebSocketHandler signalingWebSocketHandler,
                          TicketHandshakeInterceptor ticketHandshakeInterceptor) {
        this.signalingWebSocketHandler = signalingWebSocketHandler;
        this.ticketHandshakeInterceptor = ticketHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {

        // Split comma-separated patterns if you later want "http://localhost:3000,http://localhost:5173"
        String[] patterns = allowedOriginPatterns.split("\\s*,\\s*");

        registry.addHandler(signalingWebSocketHandler, signalingPath)
                .addInterceptors(ticketHandshakeInterceptor)
                .setAllowedOriginPatterns(patterns);
    }
}

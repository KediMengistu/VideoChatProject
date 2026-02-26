package com.example.ChatAppBackend.WebSocket;

import com.example.ChatAppBackend.Ticket.TicketService;
import com.example.ChatAppBackend.Ticket.TicketValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Validates the ticket and roomId query params before WebSocket upgrade.
 * Rejects handshake with 403 if validation fails.
 */
@Component
public class TicketHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(TicketHandshakeInterceptor.class);

    public static final String ATTR_ROOM_ID = "roomId";
    public static final String ATTR_ROLE = "role";

    private final TicketService ticketService;

    public TicketHandshakeInterceptor(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                  org.springframework.web.socket.WebSocketHandler wsHandler, Map<String, Object> attributes) {

        var params = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
        String ticket = params.getFirst("ticket");
        String roomIdStr = params.getFirst("roomId");

        if (ticket == null || ticket.isBlank() || roomIdStr == null || roomIdStr.isBlank()) {
            logger.warn("WebSocket handshake rejected: missing ticket or roomId");
            rejectHandshake(response, "Missing ticket or roomId");
            return false;
        }

        UUID roomId;
        try {
            roomId = UUID.fromString(roomIdStr.trim());
        } catch (IllegalArgumentException e) {
            logger.warn("WebSocket handshake rejected: invalid roomId format");
            rejectHandshake(response, "Invalid roomId format");
            return false;
        }

        Optional<TicketValidationResult> result = ticketService.validateForWebSocket(ticket, roomId);
        if (result.isEmpty()) {
            logger.warn("WebSocket handshake rejected: invalid or expired ticket for room {}", roomId);
            rejectHandshake(response, "Invalid or expired ticket");
            return false;
        }

        TicketValidationResult validated = result.get();
        attributes.put(ATTR_ROOM_ID, validated.roomId());
        attributes.put(ATTR_ROLE, validated.role());
        logger.debug("WebSocket handshake validated for room {} role {}", validated.roomId(), validated.role());
        return true;
    }

    private void rejectHandshake(ServerHttpResponse response, String errorMessage) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            response.getBody().write(("{\"error\":\"" + errorMessage + "\"}").getBytes());
        } catch (IOException e) {
            logger.warn("Failed to write handshake rejection body: {}", e.getMessage());
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               org.springframework.web.socket.WebSocketHandler wsHandler, Exception ex) {
        // No-op
    }
}

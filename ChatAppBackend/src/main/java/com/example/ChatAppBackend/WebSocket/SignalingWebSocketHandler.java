package com.example.ChatAppBackend.WebSocket;

import com.example.ChatAppBackend.Ticket.TicketRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Handles WebRTC signaling over WebSocket: offer, answer, ice-candidate.
 * Forwards messages between host and guest in the same room.
 * Uses in-memory registry for local sessions; publishes to Redis when other peer is on another instance.
 */
@Component
public class SignalingWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(SignalingWebSocketHandler.class);

    private static final Set<String> ALLOWED_MESSAGE_TYPES = Set.of("offer", "answer", "ice-candidate");

    private static final String PEER_LEFT_PAYLOAD = "{\"type\":\"peer-left\"}";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SignalingSessionRegistry sessionRegistry;
    private final SignalingRedisPublisher redisPublisher;

    public SignalingWebSocketHandler(SignalingSessionRegistry sessionRegistry,
                                      SignalingRedisPublisher redisPublisher) {
        this.sessionRegistry = sessionRegistry;
        this.redisPublisher = redisPublisher;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        UUID roomId = (UUID) session.getAttributes().get(TicketHandshakeInterceptor.ATTR_ROOM_ID);
        TicketRole role = (TicketRole) session.getAttributes().get(TicketHandshakeInterceptor.ATTR_ROLE);

        if (roomId == null || role == null) {
            logger.warn("WebSocket session missing roomId or role, closing");
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        sessionRegistry.addSession(roomId, role, session);
        logger.info("WebSocket connected for room {} role {}", roomId, role);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        UUID roomId = (UUID) session.getAttributes().get(TicketHandshakeInterceptor.ATTR_ROOM_ID);
        TicketRole role = (TicketRole) session.getAttributes().get(TicketHandshakeInterceptor.ATTR_ROLE);

        if (roomId == null || role == null) {
            return;
        }

        WebSocketSession otherSession = sessionRegistry.getOtherSession(roomId, role);
        if (otherSession != null && otherSession.isOpen()) {
            try {
                otherSession.sendMessage(new TextMessage(PEER_LEFT_PAYLOAD));
            } catch (IOException e) {
                logger.warn("Failed to send peer-left to other session: {}", e.getMessage());
            }
        }
        sessionRegistry.removeSession(roomId, role);
        logger.info("WebSocket disconnected for room {} role {}", roomId, role);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if (payload == null || payload.isBlank()) {
            return;
        }

        JsonNode json;
        try {
            json = objectMapper.readTree(payload);
        } catch (Exception e) {
            logger.warn("Invalid JSON in signaling message: {}", e.getMessage());
            return;
        }

        String type = json.has("type") ? json.get("type").asText() : null;
        if (type == null || !ALLOWED_MESSAGE_TYPES.contains(type)) {
            logger.warn("Unknown or missing message type: {}", type);
            return;
        }

        UUID roomId = (UUID) session.getAttributes().get(TicketHandshakeInterceptor.ATTR_ROOM_ID);
        TicketRole myRole = (TicketRole) session.getAttributes().get(TicketHandshakeInterceptor.ATTR_ROLE);

        if (roomId == null || myRole == null) {
            return;
        }

        TicketRole otherRole = myRole == TicketRole.HOST ? TicketRole.GUEST : TicketRole.HOST;
        WebSocketSession otherSession = sessionRegistry.getOtherSession(roomId, myRole);

        if (otherSession != null && otherSession.isOpen()) {
            try {
                otherSession.sendMessage(new TextMessage(payload));
            } catch (IOException e) {
                logger.warn("Failed to forward {} to other peer: {}", type, e.getMessage());
            }
        } else {
            redisPublisher.publish(roomId, otherRole, payload);
        }
    }
}

package com.example.ChatAppBackend.WebSocket;

import com.example.ChatAppBackend.Ticket.TicketRole;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds WebSocket sessions per room and role. Used by SignalingWebSocketHandler
 * and SignalingRedisSubscriber for local session lookup and forwarding.
 */
@Component
public class SignalingSessionRegistry {

    private final Map<UUID, Map<TicketRole, WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    public void addSession(UUID roomId, TicketRole role, WebSocketSession session) {
        roomSessions.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>()).put(role, session);
    }

    public void removeSession(UUID roomId, TicketRole role) {
        Map<TicketRole, WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions != null) {
            sessions.remove(role);
            if (sessions.isEmpty()) {
                roomSessions.remove(roomId);
            }
        }
    }

    public WebSocketSession getSession(UUID roomId, TicketRole role) {
        Map<TicketRole, WebSocketSession> sessions = roomSessions.get(roomId);
        return sessions != null ? sessions.get(role) : null;
    }

    public WebSocketSession getOtherSession(UUID roomId, TicketRole myRole) {
        TicketRole otherRole = myRole == TicketRole.HOST ? TicketRole.GUEST : TicketRole.HOST;
        return getSession(roomId, otherRole);
    }

    /**
     * Forward payload to the session for the given room and role, if it exists locally.
     * Returns true if forwarded, false if no local session.
     */
    public boolean forwardToLocalSession(UUID roomId, TicketRole targetRole, String payload) {
        WebSocketSession session = getSession(roomId, targetRole);
        if (session == null || !session.isOpen()) {
            return false;
        }
        try {
            session.sendMessage(new TextMessage(payload));
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}

package com.example.ChatAppBackend.WebSocket;

import com.example.ChatAppBackend.Ticket.TicketRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Subscribes to Redis room channels and forwards messages to local WebSocket sessions.
 */
@Component
public class SignalingRedisSubscriber implements MessageListener {

    private static final Logger logger = LoggerFactory.getLogger(SignalingRedisSubscriber.class);

    private static final String CHANNEL_PREFIX = "room:";

    private final SignalingSessionRegistry sessionRegistry;
    private final SignalingRedisPublisher publisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SignalingRedisSubscriber(SignalingSessionRegistry sessionRegistry,
                                    SignalingRedisPublisher publisher,
                                    RedisMessageListenerContainer redisMessageListenerContainer) {
        this.sessionRegistry = sessionRegistry;
        this.publisher = publisher;
        redisMessageListenerContainer.addMessageListener(this, new PatternTopic(CHANNEL_PREFIX + "*"));
        logger.info("SignalingRedisSubscriber subscribed to room:*");
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        if (!channel.startsWith(CHANNEL_PREFIX)) {
            return;
        }
        String roomIdStr = channel.substring(CHANNEL_PREFIX.length());
        UUID roomId;
        try {
            roomId = UUID.fromString(roomIdStr);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid roomId in channel: {}", channel);
            return;
        }

        String body = new String(message.getBody());
        try {
            JsonNode json = objectMapper.readTree(body);
            String targetRoleStr = json.has("targetRole") ? json.get("targetRole").asText() : null;
            String payload = json.has("payload") ? json.get("payload").asText() : null;
            String sourceInstanceId = json.has("sourceInstanceId") ? json.get("sourceInstanceId").asText() : null;

            if (targetRoleStr == null || payload == null) {
                logger.warn("Invalid Redis message: missing targetRole or payload");
                return;
            }
            if (sourceInstanceId != null && sourceInstanceId.equals(publisher.getInstanceId())) {
                return;
            }

            TicketRole targetRole = TicketRole.valueOf(targetRoleStr);
            if (sessionRegistry.forwardToLocalSession(roomId, targetRole, payload)) {
                logger.debug("Forwarded Redis message to local session room={} role={}", roomId, targetRole);
            }
        } catch (Exception e) {
            logger.warn("Failed to process Redis message: {}", e.getMessage());
        }
    }
}

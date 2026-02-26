package com.example.ChatAppBackend.WebSocket;

import com.example.ChatAppBackend.Ticket.TicketRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Publishes signaling messages to Redis for cross-instance forwarding.
 */
@Component
public class SignalingRedisPublisher {

    private static final Logger logger = LoggerFactory.getLogger(SignalingRedisPublisher.class);

    private static final String CHANNEL_PREFIX = "room:";

    private final StringRedisTemplate redisTemplate;
    private final String instanceId;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SignalingRedisPublisher(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.instanceId = java.util.UUID.randomUUID().toString();
        logger.info("SignalingRedisPublisher initialized with instanceId={}", instanceId);
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void publish(UUID roomId, TicketRole targetRole, String payload) {
        String channel = CHANNEL_PREFIX + roomId;
        try {
            String message = objectMapper.writeValueAsString(Map.of(
                    "targetRole", targetRole.name(),
                    "payload", payload,
                    "sourceInstanceId", instanceId
            ));
            redisTemplate.convertAndSend(channel, message);
            logger.debug("Published to {} for targetRole={}", channel, targetRole);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize Redis message: {}", e.getMessage());
        }
    }
}

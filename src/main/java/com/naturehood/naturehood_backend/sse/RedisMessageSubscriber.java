package com.naturehood.naturehood_backend.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naturehood.naturehood_backend.dto.response.NewPostEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis Pub/Sub subscriber for the feed channel.
 */
@Component
public class RedisMessageSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisMessageSubscriber.class);

    private final SseEmitterRegistry sseEmitterRegistry;
    private final ObjectMapper objectMapper;

    public RedisMessageSubscriber(SseEmitterRegistry sseEmitterRegistry, ObjectMapper objectMapper) {
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody());
        log.debug("Received Redis Pub/Sub message on feed channel: {}", body);

        try {
            FeedPublishEvent event = objectMapper.readValue(body, FeedPublishEvent.class);

            if (event.targetUserIds() == null || event.targetUserIds().isEmpty()) {
                return;
            }

            String eventJson = objectMapper.writeValueAsString(event.event());

            int delivered = 0;
            for (String userId : event.targetUserIds()) {
                if (sseEmitterRegistry.isConnected(userId)) {
                    sseEmitterRegistry.sendToUser(userId, "new-post", eventJson);
                    delivered++;
                }
            }

            log.debug("SSE delivery: {} of {} target users were connected",
                    delivered, event.targetUserIds().size());

        } catch (Exception e) {
            log.error("Failed to process Redis feed event: {}", e.getMessage(), e);
        }
    }

    public record FeedPublishEvent(List<String> targetUserIds, NewPostEvent event) {}
}

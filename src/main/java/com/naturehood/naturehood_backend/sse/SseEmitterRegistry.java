package com.naturehood.naturehood_backend.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of active SSE emitters keyed by userId.
 */
@Component
public class SseEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);

    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final long timeoutMs;

    public SseEmitterRegistry(
            @Value("${sse.emitter.timeout-ms:300000}") long timeoutMs
    ) {
        this.timeoutMs = timeoutMs;
    }

    public SseEmitter register(String userId) {
        SseEmitter emitter = new SseEmitter(timeoutMs);

        Runnable cleanup = () -> {
            emitters.remove(userId, emitter);
            log.debug("SSE emitter deregistered for user={}", userId);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ex -> {
            log.debug("SSE emitter error for user={}: {}", userId, ex.getMessage());
            cleanup.run();
        });

        SseEmitter previous = emitters.put(userId, emitter);
        if (previous != null) {
            try {
                previous.complete();
            } catch (Exception ignored) {}
        }

        log.debug("SSE emitter registered for user={} (total active={})", userId, emitters.size());
        return emitter;
    }

    public void sendToUser(String userId, String eventName, String data) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(
                    SseEmitter.event()
                            .name(eventName)
                            .data(data)
            );
            log.debug("SSE event '{}' sent to user={}", eventName, userId);
        } catch (Exception e) {
            log.warn("Failed to send SSE event to user={}: {}", userId, e.getMessage());
            emitters.remove(userId, emitter);
            try { emitter.completeWithError(e); } catch (Exception ignored) {}
        }
    }

    public int activeConnectionCount() {
        return emitters.size();
    }

    public boolean isConnected(String userId) {
        return emitters.containsKey(userId);
    }
}

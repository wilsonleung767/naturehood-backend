package com.naturehood.naturehood_backend.controller;

import com.naturehood.naturehood_backend.sse.SseEmitterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * Server-Sent Events controller for real-time feed updates.
 */
@RestController
@RequestMapping("/api/feed")
public class SseController {

    private static final Logger log = LoggerFactory.getLogger(SseController.class);

    private final SseEmitterRegistry sseEmitterRegistry;

    public SseController(SseEmitterRegistry sseEmitterRegistry) {
        this.sseEmitterRegistry = sseEmitterRegistry;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamFeed(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        log.info("SSE connection opened for userId={}", userId);

        SseEmitter emitter = sseEmitterRegistry.register(userId);

        try {
            emitter.send(
                    SseEmitter.event()
                            .comment("connected")
                            .name("ping")
                            .data("")
            );
        } catch (IOException e) {
            log.warn("Failed to send initial keepalive to userId={}: {}", userId, e.getMessage());
        }

        return emitter;
    }
}

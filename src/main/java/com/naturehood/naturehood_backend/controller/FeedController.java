package com.naturehood.naturehood_backend.controller;

import com.naturehood.naturehood_backend.dto.response.ApiResponse;
import com.naturehood.naturehood_backend.dto.response.FeedResponse;
import com.naturehood.naturehood_backend.dto.response.PostDTO;
import com.naturehood.naturehood_backend.service.FeedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Feed retrieval controller.
 */
@RestController
@RequestMapping("/api/feed")
public class FeedController {

    private static final Logger log = LoggerFactory.getLogger(FeedController.class);

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<FeedResponse<PostDTO>>> getFeed(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        String userId = jwt.getSubject();
        int clampedLimit = Math.min(limit, 50);

        log.debug("Feed request: userId={}, cursor={}, limit={}", userId, cursor, clampedLimit);

        FeedResponse<PostDTO> feed = feedService.getFeed(userId, cursor, clampedLimit);
        return ResponseEntity.ok(ApiResponse.ok(feed));
    }
}

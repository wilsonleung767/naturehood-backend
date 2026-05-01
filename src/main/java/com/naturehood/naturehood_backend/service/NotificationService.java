package com.naturehood.naturehood_backend.service;

import com.naturehood.naturehood_backend.config.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationProperties props;
    private final SupabaseProfileService profileService;
    private final RestTemplate restTemplate;

    public NotificationService(NotificationProperties props, SupabaseProfileService profileService) {
        this.props = props;
        this.profileService = profileService;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Async
    public void notifyPostLiked(String postId, String postAuthorId, String likerUserId) {
        if (shouldSkip(likerUserId, postAuthorId)) return;
        String actor = profileService.getDisplayName(likerUserId);
        send(postAuthorId, "New Like", actor + " liked your post",
                Map.of("type", "like", "postId", postId));
    }

    @Async
    public void notifyCommentOnPost(String postId, String postAuthorId, String commentId, String commenterUserId) {
        if (shouldSkip(commenterUserId, postAuthorId)) return;
        String actor = profileService.getDisplayName(commenterUserId);
        send(postAuthorId, "New Comment", actor + " commented on your post",
                Map.of("type", "comment", "postId", postId, "commentId", commentId));
    }

    @Async
    public void notifyReplyToComment(String postId, String parentCommentAuthorId, String commentId, String replierUserId) {
        if (shouldSkip(replierUserId, parentCommentAuthorId)) return;
        String actor = profileService.getDisplayName(replierUserId);
        send(parentCommentAuthorId, "New Reply", actor + " replied to your comment",
                Map.of("type", "reply", "postId", postId, "commentId", commentId));
    }

    @Async
    public void notifyNewFollower(String followeeId, String followerUserId) {
        if (shouldSkip(followerUserId, followeeId)) return;
        String actor = profileService.getDisplayName(followerUserId);
        send(followeeId, "New Follower", actor + " started following you",
                Map.of("type", "follow", "userId", followerUserId));
    }

    private boolean shouldSkip(String actorId, String recipientId) {
        if (!props.isEnabled()) return true;
        if (props.getSendUrl().isBlank() || props.getApiKey().isBlank()) return true;
        return actorId != null && actorId.equals(recipientId);
    }

    private void send(String recipientUserId, String title, String body, Map<String, Object> data) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", props.getApiKey());

            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", recipientUserId);
            payload.put("title", title);
            payload.put("body", body);
            payload.put("data", data);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    props.getSendUrl(), new HttpEntity<>(payload, headers), String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Notification send failed: status={} recipient={}", response.getStatusCode(), recipientUserId);
            } else {
                log.debug("Notification sent: title='{}' recipient={}", title, recipientUserId);
            }
        } catch (Exception e) {
            log.error("Notification send error: title='{}' recipient={}: {}", title, recipientUserId, e.getMessage());
        }
    }
}

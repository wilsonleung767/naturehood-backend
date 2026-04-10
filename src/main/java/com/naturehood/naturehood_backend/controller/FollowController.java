package com.naturehood.naturehood_backend.controller;

import com.naturehood.naturehood_backend.dto.response.ApiResponse;
import com.naturehood.naturehood_backend.service.FollowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for follow/unfollow and social-graph queries.
 *
 * All endpoints operate on the authenticated caller's identity extracted from
 * the Supabase JWT {@code sub} claim. The {@code userId} path variable refers
 * to the target user being followed/inspected.
 *
 * Endpoints:
 *   PUT    /api/users/{userId}/follow        — follow a user
 *   DELETE /api/users/{userId}/follow        — unfollow a user
 *   GET    /api/users/{userId}/followers     — list follower IDs
 *   GET    /api/users/{userId}/following     — list following IDs
 *   GET    /api/users/{userId}/follow-counts — follower + following counts
 *   GET    /api/users/{userId}/is-following  — is the caller following this user?
 */
@RestController
@RequestMapping("/api/users")
public class FollowController {

    private static final Logger log = LoggerFactory.getLogger(FollowController.class);

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    /**
     * Follow a user.
     *
     * Idempotent: calling this when already following is a no-op (returns
     * {@code following: true} either way).
     *
     * @param jwt    authenticated caller's JWT
     * @param userId the user to follow
     * @return {@code { "following": true }}
     */
    @PutMapping("/{userId}/follow")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> follow(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId
    ) {
        String callerId = jwt.getSubject();
        followService.follow(callerId, userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("following", true)));
    }

    /**
     * Unfollow a user.
     *
     * Idempotent: calling this when not following is a no-op (returns
     * {@code following: false} either way).
     *
     * @param jwt    authenticated caller's JWT
     * @param userId the user to unfollow
     * @return {@code { "following": false }}
     */
    @DeleteMapping("/{userId}/follow")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> unfollow(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId
    ) {
        String callerId = jwt.getSubject();
        followService.unfollow(callerId, userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("following", false)));
    }

    /**
     * List the IDs of all users who follow the given user.
     *
     * @param userId the user whose followers to list
     * @return {@code { "followerIds": [...] }}
     */
    @GetMapping("/{userId}/followers")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> getFollowers(
            @PathVariable String userId
    ) {
        List<String> followerIds = followService.getFollowerIds(userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("followerIds", followerIds)));
    }

    /**
     * List the IDs of all users the given user is following.
     *
     * @param userId the user whose following list to return
     * @return {@code { "followingIds": [...] }}
     */
    @GetMapping("/{userId}/following")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> getFollowing(
            @PathVariable String userId
    ) {
        List<String> followingIds = followService.getFollowingIds(userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("followingIds", followingIds)));
    }

    /**
     * Return follower and following counts for a user.
     *
     * @param userId the user to inspect
     * @return {@code { "followerCount": N, "followingCount": M }}
     */
    @GetMapping("/{userId}/follow-counts")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getFollowCounts(
            @PathVariable String userId
    ) {
        Map<String, Long> counts = followService.getFollowCounts(userId);
        return ResponseEntity.ok(ApiResponse.ok(counts));
    }

    /**
     * Check whether the authenticated caller is following a given user.
     *
     * Useful for populating a "Follow / Unfollow" button in the UI without
     * loading the full follower list.
     *
     * @param jwt    authenticated caller's JWT
     * @param userId the user to check
     * @return {@code { "following": true/false }}
     */
    @GetMapping("/{userId}/is-following")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> isFollowing(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId
    ) {
        String callerId = jwt.getSubject();
        boolean following = followService.isFollowing(callerId, userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("following", following)));
    }
}

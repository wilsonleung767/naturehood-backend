package com.naturehood.naturehood_backend.service;

import com.naturehood.naturehood_backend.domain.Follow;
import com.naturehood.naturehood_backend.repository.FollowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages follow/unfollow relationships between users.
 *
 * Timeline side-effects are delegated to FeedService to keep this class
 * focused on the social-graph concern only.
 */
@Service
public class FollowService {

    private static final Logger log = LoggerFactory.getLogger(FollowService.class);

    private final FollowRepository followRepository;
    private final FeedService feedService;

    public FollowService(FollowRepository followRepository, FeedService feedService) {
        this.followRepository = followRepository;
        this.feedService = feedService;
    }

    /**
     * Follow a user.
     *
     * Idempotent: if the caller is already following the target, this is a no-op
     * and returns {@code false} (already following).
     *
     * @param followerId the user performing the follow action (the authenticated caller)
     * @param followeeId the user being followed
     * @return {@code true} if a new follow relationship was created,
     *         {@code false} if it already existed
     * @throws IllegalArgumentException if followerId equals followeeId
     */
    public boolean follow(String followerId, String followeeId) {
        if (followerId.equals(followeeId)) {
            throw new IllegalArgumentException("A user cannot follow themselves");
        }

        if (followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
            log.debug("Already following: follower={} followee={}", followerId, followeeId);
            return false;
        }

        Follow follow = Follow.builder()
                .followerId(followerId)
                .followeeId(followeeId)
                .build();

        try {
            followRepository.save(follow);
        } catch (DuplicateKeyException e) {
            // Race condition: another request created the same follow concurrently.
            // Treat as idempotent success.
            log.debug("Concurrent follow detected (duplicate key): follower={} followee={}", followerId, followeeId);
            return false;
        }

        log.info("Follow created: follower={} -> followee={}", followerId, followeeId);

        // Notify feed service so it can perform any timeline side-effects
        // (currently a no-op; future hook for backfill on follow).
        feedService.onFollow(followerId, followeeId);

        return true;
    }

    /**
     * Unfollow a user.
     *
     * Idempotent: if the caller is not following the target, this is a no-op.
     *
     * @param followerId the user performing the unfollow action (the authenticated caller)
     * @param followeeId the user being unfollowed
     * @return {@code false} always (the resulting following state is false)
     */
    public boolean unfollow(String followerId, String followeeId) {
        if (!followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
            log.debug("Not following (no-op): follower={} followee={}", followerId, followeeId);
            return false;
        }

        followRepository.deleteByFollowerIdAndFolloweeId(followerId, followeeId);
        log.info("Follow deleted: follower={} -/-> followee={}", followerId, followeeId);

        // Remove the unfollowed author's posts from the follower's Redis timeline.
        feedService.onUnfollow(followerId, followeeId);

        return false;
    }

    /**
     * Returns the IDs of all users who follow the given user.
     */
    public List<String> getFollowerIds(String userId) {
        return followRepository.findByFolloweeId(userId).stream()
                .map(Follow::getFollowerId)
                .collect(Collectors.toList());
    }

    /**
     * Returns the IDs of all users the given user is following.
     */
    public List<String> getFollowingIds(String userId) {
        return followRepository.findByFollowerId(userId).stream()
                .map(Follow::getFolloweeId)
                .collect(Collectors.toList());
    }

    /**
     * Returns follower and following counts for the given user.
     */
    public Map<String, Long> getFollowCounts(String userId) {
        long followerCount = followRepository.countByFolloweeId(userId);
        long followingCount = followRepository.countByFollowerId(userId);
        return Map.of("followerCount", followerCount, "followingCount", followingCount);
    }

    /**
     * Returns whether the given follower is currently following the given followee.
     */
    public boolean isFollowing(String followerId, String followeeId) {
        return followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId);
    }
}

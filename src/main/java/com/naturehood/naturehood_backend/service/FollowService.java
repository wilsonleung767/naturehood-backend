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

@Service
public class FollowService {

    private static final Logger log = LoggerFactory.getLogger(FollowService.class);

    private final FollowRepository followRepository;
    private final FeedService feedService;
    private final NotificationService notificationService;

    public FollowService(FollowRepository followRepository, FeedService feedService,
                         NotificationService notificationService) {
        this.followRepository = followRepository;
        this.feedService = feedService;
        this.notificationService = notificationService;
    }

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
            // Race condition: concurrent follow created the same relationship.
            log.debug("Concurrent follow detected (duplicate key): follower={} followee={}", followerId, followeeId);
            return false;
        }

        log.info("Follow created: follower={} -> followee={}", followerId, followeeId);

        notificationService.notifyNewFollower(followeeId, followerId);
        feedService.onFollow(followerId, followeeId);

        return true;
    }

    public boolean unfollow(String followerId, String followeeId) {
        if (!followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
            log.debug("Not following (no-op): follower={} followee={}", followerId, followeeId);
            return false;
        }

        followRepository.deleteByFollowerIdAndFolloweeId(followerId, followeeId);
        log.info("Follow deleted: follower={} -/-> followee={}", followerId, followeeId);

        feedService.onUnfollow(followerId, followeeId);

        return false;
    }

    public List<String> getFollowerIds(String userId) {
        return followRepository.findByFolloweeId(userId).stream()
                .map(Follow::getFollowerId)
                .collect(Collectors.toList());
    }

    public List<String> getFollowingIds(String userId) {
        return followRepository.findByFollowerId(userId).stream()
                .map(Follow::getFolloweeId)
                .collect(Collectors.toList());
    }

    public Map<String, Long> getFollowCounts(String userId) {
        long followerCount = followRepository.countByFolloweeId(userId);
        long followingCount = followRepository.countByFollowerId(userId);
        return Map.of("followerCount", followerCount, "followingCount", followingCount);
    }

    public boolean isFollowing(String followerId, String followeeId) {
        return followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId);
    }
}

package com.naturehood.naturehood_backend.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Redis Sorted Set operations for per-user timelines.
 */
@Repository
public class FeedRedisRepository {

    private static final Logger log = LoggerFactory.getLogger(FeedRedisRepository.class);
    private static final String TIMELINE_KEY_PREFIX = "timeline:";

    private final RedisTemplate<String, String> redisTemplate;
    private final long maxTimelineSize;

    public FeedRedisRepository(
            RedisTemplate<String, String> redisTemplate,
            @Value("${feed.timeline.max-size:2000}") long maxTimelineSize
    ) {
        this.redisTemplate = redisTemplate;
        this.maxTimelineSize = maxTimelineSize;
    }

    // ─── Write ────────────────────────────────────────────────────────────────

    public void addToTimeline(String userId, String postId, double score) {
        String key = timelineKey(userId);
        ZSetOperations<String, String> zOps = redisTemplate.opsForZSet();

        Boolean added = zOps.addIfAbsent(key, postId, score);

        if (Boolean.TRUE.equals(added)) {
            Long size = zOps.size(key);
            if (size != null && size > maxTimelineSize) {
                long countToRemove = size - maxTimelineSize;
                zOps.removeRange(key, 0, countToRemove - 1);
            }
        }
    }

    public void addToTimelines(List<String> userIds, String postId, double score) {
        for (String userId : userIds) {
            addToTimeline(userId, postId, score);
        }
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    public List<ZSetOperations.TypedTuple<String>> fetchTimeline(
            String userId,
            double maxScore,
            int limit
    ) {
        String key = timelineKey(userId);
        Set<ZSetOperations.TypedTuple<String>> result =
                redisTemplate.opsForZSet().reverseRangeByScoreWithScores(
                        key,
                        Double.NEGATIVE_INFINITY,
                        Math.nextDown(maxScore),
                        0,
                        limit
                );

        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }

        return result.stream()
                .filter(t -> t.getValue() != null && t.getScore() != null)
                .collect(Collectors.toList());
    }

    public List<ZSetOperations.TypedTuple<String>> fetchTimelineFirstPage(
            String userId,
            int limit
    ) {
        String key = timelineKey(userId);
        Set<ZSetOperations.TypedTuple<String>> result =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, limit - 1L);

        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }

        return result.stream()
                .filter(t -> t.getValue() != null && t.getScore() != null)
                .collect(Collectors.toList());
    }

    public void removeFromTimeline(String userId, String postId) {
        redisTemplate.opsForZSet().remove(timelineKey(userId), postId);
    }

    public long timelineSize(String userId) {
        Long size = redisTemplate.opsForZSet().size(timelineKey(userId));
        return size != null ? size : 0L;
    }

    /**
     * Bulk-load a set of (postId, score) pairs into a user's timeline.
     *
     * Called during timeline warm-up after a Redis crash: the caller has already
     * re-scored posts from MongoDB and passes them here for a single pipeline write.
     * Uses addIfAbsent (ZADD NX) per entry so it is safe to call even if some
     * entries already exist (e.g. partial warm-up scenario).
     *
     * The timeline is trimmed to maxTimelineSize after all entries are inserted.
     */
    public void warmUpTimeline(String userId, Set<ZSetOperations.TypedTuple<String>> entries) {
        if (entries == null || entries.isEmpty()) return;

        String key = timelineKey(userId);
        ZSetOperations<String, String> zOps = redisTemplate.opsForZSet();

        for (ZSetOperations.TypedTuple<String> entry : entries) {
            if (entry.getValue() != null && entry.getScore() != null) {
                zOps.addIfAbsent(key, entry.getValue(), entry.getScore());
            }
        }

        // Trim to cap after bulk insert.
        Long size = zOps.size(key);
        if (size != null && size > maxTimelineSize) {
            zOps.removeRange(key, 0, size - maxTimelineSize - 1);
        }

        log.info("Timeline warm-up complete for user={}: {} entries written", userId, entries.size());
    }

    private String timelineKey(String userId) {
        return TIMELINE_KEY_PREFIX + userId;
    }
}

package com.naturehood.naturehood_backend.service;

import com.naturehood.naturehood_backend.domain.Post;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Feed ranking algorithm service.
 *
 * Computes an epoch-based relevance score for a (post, recipient) pair:
 *
 *   score = createdAt_epochMillis + (engagement × engagementBoostMs) + (affinity × affinityBoostMs)
 *
 * The post's creation timestamp is the base score, so newer posts always rank
 * higher by default. Engagement and affinity add bounded time-boosts — a viral
 * post effectively ranks as if it were N hours younger than it actually is.
 *
 * This avoids the stale-score problem where scores frozen at fan-out time
 * become inconsistent with scores recomputed during timeline rebuilds.
 */
@Service
public class AlgorithmService {

    private static final Logger log = LoggerFactory.getLogger(AlgorithmService.class);

    private static final double MAX_ENGAGEMENT_LOG = Math.log1p(1000.0);
    private static final long MS_PER_HOUR = 3_600_000L;

    public enum AffinityType { SELF, FOLLOWEE, STRANGER }

    private final long engagementBoostMs;
    private final long affinityBoostMs;
    private final double selfAffinity;

    public AlgorithmService(
            @Value("${feed.score.engagement-boost-hours:4}") double engagementBoostHours,
            @Value("${feed.score.affinity-boost-hours:1}") double affinityBoostHours,
            @Value("${feed.score.self-affinity:0.6}") double selfAffinity
    ) {
        this.engagementBoostMs = (long) (engagementBoostHours * MS_PER_HOUR);
        this.affinityBoostMs = (long) (affinityBoostHours * MS_PER_HOUR);
        this.selfAffinity = selfAffinity;
    }

    /**
     * Compute the ranking score for a post placed in a recipient's timeline.
     *
     * @param post         the post being scored
     * @param affinityType relationship between the recipient and the post's author
     * @return epoch-millisecond-based score (higher = ranks higher in feed)
     */
    public double computeScore(Post post, AffinityType affinityType) {
        long baseMs = post.getCreatedAt() != null ? post.getCreatedAt().toEpochMilli() : 0L;
        double engagement = computeEngagement(post.getLikeCount(), post.getCommentCount(), post.getRepostCount());
        double affinity = computeAffinity(affinityType);

        double score = baseMs
                + (engagement * engagementBoostMs)
                + (affinity * affinityBoostMs);

        log.debug("Score for post={}: base={}, engagement={}, affinity={} ({}) -> total={}",
                post.getId(), baseMs, engagement, affinity, affinityType, score);

        return score;
    }

    // ─── Component calculators ────────────────────────────────────────────────

    /**
     * Engagement score in [0, 1]:
     *   f(e) = log1p(totalInteractions) / log1p(MAX_CAP)
     */
    double computeEngagement(int likes, int comments, int reposts) {
        double total = likes + comments + reposts;
        if (total <= 0) return 0.0;
        return Math.min(1.0, Math.log1p(total) / MAX_ENGAGEMENT_LOG);
    }

    double computeAffinity(AffinityType type) {
        return switch (type) {
            case FOLLOWEE -> 1.0;
            case SELF     -> selfAffinity;
            case STRANGER -> selfAffinity;
        };
    }
}

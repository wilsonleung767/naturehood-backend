package com.naturehood.naturehood_backend.service;

import com.naturehood.naturehood_backend.domain.Post;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Feed ranking algorithm service.
 *
 * Computes a relevance score for a (post, recipient) pair:
 *
 *   score = (recency × W_recency) + (engagement × W_engagement) + (affinity × W_affinity)
 *
 * Affinity tiers (highest → lowest):
 *   FOLLOWEE  1.0  — post from someone the viewer follows
 *   SELF      0.6  — viewer's own post (intentionally below followee so own posts
 *                    don't monopolise the top of the feed)
 *   STRANGER  0.0  — explore / non-follower post
 *
 * Default weights (recency=0.30, engagement=0.60, affinity=0.10) are tuned so
 * that a viral post (engagement ≈ 1.0) scores higher than any fresh-but-empty
 * post regardless of affinity tier.
 */
@Service
public class AlgorithmService {

    private static final Logger log = LoggerFactory.getLogger(AlgorithmService.class);

    private static final double MAX_ENGAGEMENT_LOG = Math.log1p(1000.0);

    public enum AffinityType { SELF, FOLLOWEE, STRANGER }

    private final double recencyWeight;
    private final double engagementWeight;
    private final double affinityWeight;
    private final double selfAffinity;

    public AlgorithmService(
            @Value("${feed.score.recency-weight:0.30}") double recencyWeight,
            @Value("${feed.score.engagement-weight:0.60}") double engagementWeight,
            @Value("${feed.score.affinity-weight:0.10}") double affinityWeight,
            @Value("${feed.score.self-affinity:0.6}") double selfAffinity
    ) {
        this.recencyWeight = recencyWeight;
        this.engagementWeight = engagementWeight;
        this.affinityWeight = affinityWeight;
        this.selfAffinity = selfAffinity;

        double total = recencyWeight + engagementWeight + affinityWeight;
        if (Math.abs(total - 1.0) > 0.001) {
            log.warn("Feed score weights do not sum to 1.0 (sum={}). Scores will be off-scale.", total);
        }
    }

    /**
     * Compute the ranking score for a post placed in a recipient's timeline.
     *
     * @param post         the post being scored
     * @param affinityType relationship between the recipient and the post's author
     * @return score in [0, 1] range (approximately)
     */
    public double computeScore(Post post, AffinityType affinityType) {
        double recency    = computeRecency(post.getCreatedAt());
        double engagement = computeEngagement(post.getLikeCount(), post.getCommentCount(), post.getRepostCount());
        double affinity   = computeAffinity(affinityType);

        double score = (recency * recencyWeight)
                + (engagement * engagementWeight)
                + (affinity * affinityWeight);

        log.debug("Score for post={}: recency={}, engagement={}, affinity={} ({}) -> total={}",
                post.getId(), recency, engagement, affinity, affinityType, score);

        return score;
    }

    // ─── Component calculators ────────────────────────────────────────────────

    /**
     * Recency score in [0, 1]:
     *   f(t) = 1 / (1 + minutesSinceCreation)
     */
    double computeRecency(Instant createdAt) {
        if (createdAt == null) return 0.0;
        long minutesAgo = Math.max(0, ChronoUnit.MINUTES.between(createdAt, Instant.now()));
        return 1.0 / (1.0 + minutesAgo);
    }

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

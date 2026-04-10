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
 */
@Service
public class AlgorithmService {

    private static final Logger log = LoggerFactory.getLogger(AlgorithmService.class);

    /**
     * Practical engagement ceiling for normalization.
     */
    private static final double MAX_ENGAGEMENT_LOG = Math.log1p(1000.0);

    private final double recencyWeight;
    private final double engagementWeight;
    private final double affinityWeight;

    public AlgorithmService(
            @Value("${feed.score.recency-weight:0.5}") double recencyWeight,
            @Value("${feed.score.engagement-weight:0.3}") double engagementWeight,
            @Value("${feed.score.affinity-weight:0.2}") double affinityWeight
    ) {
        this.recencyWeight = recencyWeight;
        this.engagementWeight = engagementWeight;
        this.affinityWeight = affinityWeight;

        double total = recencyWeight + engagementWeight + affinityWeight;
        if (Math.abs(total - 1.0) > 0.001) {
            log.warn("Feed score weights do not sum to 1.0 (sum={}). Scores will be off-scale.", total);
        }
    }

    /**
     * Compute the ranking score for a post to be placed in a recipient's timeline.
     *
     * @param post       the post being scored
     * @param isFollower true if the recipient follows the post's author
     * @return score in [0, 1] range (approximately)
     */
    public double computeScore(Post post, boolean isFollower) {
        double recency = computeRecency(post.getCreatedAt());
        double engagement = computeEngagement(post.getLikeCount(), post.getCommentCount(), post.getRepostCount());
        double affinity = computeAffinity(isFollower);

        double score = (recency * recencyWeight)
                + (engagement * engagementWeight)
                + (affinity * affinityWeight);

        log.debug("Score for post={}: recency={}, engagement={}, affinity={} -> total={}",
                post.getId(), recency, engagement, affinity, score);

        return score;
    }

    // ─── Component calculators ────────────────────────────────────────────────

    /**
     * Recency score in [0, 1]:
     *   f(t) = 1 / (1 + minutesSinceCreation)
     */
    double computeRecency(Instant createdAt) {
        if (createdAt == null) return 0.0;
        long minutesAgo = ChronoUnit.MINUTES.between(createdAt, Instant.now());
        minutesAgo = Math.max(0, minutesAgo);
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

    /**
     * Affinity score in [0, 1].
     */
    double computeAffinity(boolean isFollower) {
        return isFollower ? 1.0 : 0.0;
    }
}

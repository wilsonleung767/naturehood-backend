package com.naturehood.naturehood_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naturehood.naturehood_backend.domain.Follow;
import com.naturehood.naturehood_backend.domain.Post;
import com.naturehood.naturehood_backend.dto.response.FeedResponse;
import com.naturehood.naturehood_backend.dto.response.NewPostEvent;
import com.naturehood.naturehood_backend.dto.response.PostDTO;
import com.naturehood.naturehood_backend.cursor.CursorCodec;
import com.naturehood.naturehood_backend.repository.FeedRedisRepository;
import com.naturehood.naturehood_backend.repository.FollowRepository;
import com.naturehood.naturehood_backend.repository.LikeRepository;
import com.naturehood.naturehood_backend.repository.PostRepository;
import com.naturehood.naturehood_backend.domain.Like;
import com.naturehood.naturehood_backend.sse.RedisMessageSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Feed service: fan-out and timeline retrieval.
 *
 * Two kinds of content are written into every user's Redis timeline:
 *
 *   1. Followee posts  — from people the user follows (isFollower=true).
 *      Written during fanOut() when a new post is created.
 *      Affinity component = 1.0  →  always score at least +0.2 vs a stranger.
 *
 *   2. Explore posts   — trending posts from people the user does NOT follow
 *      (isFollower=false). Written during explorefanOut() when a new post is
 *      created, selecting only the top-N posts by engagement from the wider
 *      neighbourhood. Affinity component = 0.0  →  naturally rank below
 *      followee posts of the same age and engagement.
 *
 * Redis crash recovery:
 *   If a user's timeline key is missing from Redis (empty result on first page
 *   with no cursor), getFeed() calls rebuildTimeline() which re-scores posts
 *   from MongoDB and writes them back — transparently to the caller.
 */
@Service
public class FeedService {

    private static final Logger log = LoggerFactory.getLogger(FeedService.class);

    /**
     * Fan-out limit: above this follower count, log a warning.
     * TODO: for accounts > this threshold, switch to Kafka async fan-out.
     */
    private static final int SYNC_FANOUT_LIMIT = 10_000;

    private final FeedRedisRepository feedRedisRepository;
    private final FollowRepository followRepository;
    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final AlgorithmService algorithmService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${redis.channel.feed}")
    private String feedChannel;

    @Value("${feed.default-page-size:20}")
    private int defaultPageSize;

    /**
     * How many explore (stranger) posts are injected into a user's timeline per
     * new post creation event. Keep this small: explore posts are lower-ranked but
     * still consume slots from the 2000-entry cap.
     */
    @Value("${feed.explore.max-inject-per-post:50}")
    private int exploreMaxInjectPerPost;

    /**
     * How far back (in hours) to look for explore candidate posts.
     */
    @Value("${feed.explore.lookback-hours:48}")
    private int exploreLookbackHours;

    /**
     * How far back (in hours) to look when rebuilding a cold/crashed timeline
     * from MongoDB.
     */
    @Value("${feed.warmup.lookback-hours:72}")
    private int warmupLookbackHours;

    public FeedService(FeedRedisRepository feedRedisRepository,
                       FollowRepository followRepository,
                       PostRepository postRepository,
                       LikeRepository likeRepository,
                       AlgorithmService algorithmService,
                       RedisTemplate<String, String> redisTemplate,
                       ObjectMapper objectMapper) {
        this.feedRedisRepository = feedRedisRepository;
        this.followRepository = followRepository;
        this.postRepository = postRepository;
        this.likeRepository = likeRepository;
        this.algorithmService = algorithmService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // ─── Fan-out: followee posts ──────────────────────────────────────────────

    public void fanOut(Post post, String authorId) {
        List<Follow> follows = followRepository.findByFolloweeId(authorId);

        if (follows.size() > SYNC_FANOUT_LIMIT) {
            log.warn("Author {} has {} followers (> {}). Consider async Kafka fan-out for this account.",
                    authorId, follows.size(), SYNC_FANOUT_LIMIT);
        }

        List<String> followerIds = follows.stream()
                .map(Follow::getFollowerId)
                .collect(Collectors.toList());

        // Author always sees their own post.
        followerIds.add(authorId);

        // isFollower=true → affinity = 1.0
        double score = algorithmService.computeScore(post, true);
        feedRedisRepository.addToTimelines(followerIds, post.getId(), score);

        log.info("Followee fan-out complete: post={} -> {} recipients (score={})",
                post.getId(), followerIds.size(), score);

        // Explore fan-out: push this post into the timelines of non-followers
        // who don't follow the author but are in the neighbourhood.
        exploreFanOut(post, authorId, new HashSet<>(followerIds));

        publishSseEvent(followerIds, post);
    }

    // ─── Fan-out: explore posts (strangers) ───────────────────────────────────

    /**
     * Inject this post into a limited number of non-follower timelines.
     *
     * Strategy:
     *   1. Find up to exploreMaxInjectPerPost recent users who are NOT already
     *      getting this post via the followee fan-out.
     *   2. Score the post with isFollower=false (affinity = 0.0).
     *   3. Write to their timelines with the lower score so followee posts always
     *      rank higher than explore posts of equal age/engagement.
     *
     * The "who receives explore posts" logic here is intentionally simple:
     * we pick the most-engaged recent non-follower posts and surface their
     * authors' recent posts to the broader neighbourhood. For a geo-based app
     * like Naturehood, you would replace this with a geo-radius query.
     */
    private void exploreFanOut(Post post, String authorId, Set<String> alreadyFannedOut) {
        // Score this post as a stranger's post (affinity = 0.0).
        double exploreScore = algorithmService.computeScore(post, false);

        // Only inject if the post has at least some engagement signal or is very
        // recent (< 30 min). Pure ghost posts don't get explore distribution.
        boolean recentEnough = post.getCreatedAt() != null
                && ChronoUnit.MINUTES.between(post.getCreatedAt(), Instant.now()) < 30;
        boolean hasEngagement = (post.getLikeCount() + post.getCommentCount() + post.getRepostCount()) > 0;

        if (!recentEnough && !hasEngagement) {
            log.debug("Skipping explore fan-out for post={}: not recent and no engagement", post.getId());
            return;
        }

        // Find candidate recipient user IDs: users who recently posted themselves
        // (i.e. active users) and are not already in the followee fan-out set.
        // Using recent posters as a proxy for "active neighbourhood users" is a
        // simple heuristic — replace with a proper geo/interest query for prod.
        Instant since = Instant.now().minus(exploreLookbackHours, ChronoUnit.HOURS);
        List<String> excludedAuthorIds = new ArrayList<>(alreadyFannedOut);
        excludedAuthorIds.add(authorId); // don't include the post's own author

        List<Post> recentNeighbourhoodPosts = postRepository
                .findRecentByAuthorIdsNotInAndCreatedAtAfter(
                        excludedAuthorIds,
                        since,
                        Sort.by(Sort.Direction.DESC, "createdAt")
                );

        // Deduplicate to unique user IDs, cap at exploreMaxInjectPerPost.
        List<String> exploreRecipients = recentNeighbourhoodPosts.stream()
                .map(Post::getAuthorId)
                .filter(id -> !alreadyFannedOut.contains(id))
                .distinct()
                .limit(exploreMaxInjectPerPost)
                .collect(Collectors.toList());

        if (exploreRecipients.isEmpty()) {
            log.debug("No explore recipients found for post={}", post.getId());
            return;
        }

        feedRedisRepository.addToTimelines(exploreRecipients, post.getId(), exploreScore);

        log.info("Explore fan-out complete: post={} -> {} non-follower recipients (score={})",
                post.getId(), exploreRecipients.size(), exploreScore);
    }

    // ─── SSE publish ──────────────────────────────────────────────────────────

    private void publishSseEvent(List<String> targetUserIds, Post post) {
        NewPostEvent postEvent = new NewPostEvent.Builder()
                .id(post.getId())
                .authorId(post.getAuthorId())
                .content(post.getContent())
                .images(post.getImages())
                .createdAt(post.getCreatedAt())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .repostCount(post.getRepostCount())
                .build();

        RedisMessageSubscriber.FeedPublishEvent event =
                new RedisMessageSubscriber.FeedPublishEvent(targetUserIds, postEvent);

        try {
            String json = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(feedChannel, json);
            log.debug("Published SSE event for post={} to Redis channel={}", post.getId(), feedChannel);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize SSE feed event for post={}: {}", post.getId(), e.getMessage());
        }
    }

    // ─── Feed retrieval ───────────────────────────────────────────────────────

    /**
     * Return a cursor-paginated feed for the given user.
     *
     * Redis crash recovery:
     *   On the very first page request (no cursor), if the timeline sorted set is
     *   absent from Redis (returns empty), we treat it as a cold/crashed timeline
     *   and call rebuildTimeline() to re-populate it from MongoDB before retrying
     *   the Redis read. This is transparent to the caller and the client.
     *
     *   Subsequent pages (cursor present) skip the rebuild because a partial
     *   timeline is still better than a full MongoDB scan mid-scroll, and the
     *   cursor score would be meaningless against a freshly rebuilt set anyway.
     */
    public FeedResponse<PostDTO> getFeed(String userId, String cursor, int limit) {
        int fetchSize = limit + 1;
        boolean isFirstPage = (cursor == null || cursor.isBlank());

        List<ZSetOperations.TypedTuple<String>> tuples;

        if (isFirstPage) {
            tuples = feedRedisRepository.fetchTimelineFirstPage(userId, fetchSize);

            // ── Redis crash / cold-start recovery ──────────────────────────────
            // If the timeline is empty on the first page, it could mean:
            //   (a) Redis was restarted and all sorted-set data was lost, or
            //   (b) this is a brand-new user who has never had a timeline warmed up.
            // Either way, rebuild it from MongoDB right now.
            if (tuples.isEmpty()) {
                log.warn("Timeline miss for user={} — rebuilding from MongoDB", userId);
                rebuildTimeline(userId);
                tuples = feedRedisRepository.fetchTimelineFirstPage(userId, fetchSize);
            }
        } else {
            CursorCodec.CursorData cursorData = CursorCodec.decode(cursor);
            tuples = feedRedisRepository.fetchTimeline(userId, cursorData.score(), fetchSize);
        }

        boolean hasNext = tuples.size() > limit;
        if (hasNext) {
            tuples = tuples.subList(0, limit);
        }

        if (tuples.isEmpty()) {
            return FeedResponse.of(Collections.emptyList(), null);
        }

        List<String> postIds = tuples.stream()
                .map(ZSetOperations.TypedTuple::getValue)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<String, Post> postMap = postRepository.findByIdIn(postIds).stream()
                .filter(p -> !p.isDeleted())
                .collect(Collectors.toMap(Post::getId, Function.identity()));

        Set<String> likedPostIds = postIds.stream()
                .filter(id -> likeRepository.existsByTargetIdAndUserIdAndTargetType(
                        id, userId, Like.TargetType.POST))
                .collect(Collectors.toSet());

        List<PostDTO> dtos = postIds.stream()
                .map(postMap::get)
                .filter(Objects::nonNull)
                .map(post -> toPostDTO(post, likedPostIds.contains(post.getId())))
                .collect(Collectors.toList());

        String nextCursor = null;
        if (hasNext) {
            ZSetOperations.TypedTuple<String> lastTuple = tuples.get(tuples.size() - 1);
            if (lastTuple.getValue() != null && lastTuple.getScore() != null) {
                nextCursor = CursorCodec.encode(lastTuple.getScore(), lastTuple.getValue());
            }
        }

        return FeedResponse.of(dtos, nextCursor);
    }

    // ─── Timeline rebuild (Redis crash recovery) ──────────────────────────────

    /**
     * Rebuild a user's Redis timeline from MongoDB after a crash or cold start.
     *
     * What it does:
     *   1. Looks up who the user follows.
     *   2. Fetches recent posts from those followees from MongoDB (up to
     *      warmupLookbackHours ago), re-scores each one (isFollower=true).
     *   3. Fetches recent trending posts from non-followees (explore), re-scores
     *      with isFollower=false.
     *   4. Writes all scored entries into the timeline sorted set via warmUpTimeline().
     *
     * This is a best-effort rebuild: posts older than warmupLookbackHours are not
     * recovered. That is acceptable because very old posts would have near-zero
     * recency scores anyway and would be evicted by the 2000-entry cap.
     */
    private void rebuildTimeline(String userId) {
        Instant since = Instant.now().minus(warmupLookbackHours, ChronoUnit.HOURS);

        // 1. Followee posts.
        List<Follow> follows = followRepository.findByFolloweeId(userId);
        List<String> followeeIds = follows.stream()
                .map(Follow::getFolloweeId)
                .collect(Collectors.toList());
        followeeIds.add(userId); // include own posts

        Set<ZSetOperations.TypedTuple<String>> entries = new HashSet<>();

        if (!followeeIds.isEmpty()) {
            List<Post> followeePosts = postRepository.findRecentByAuthorIdsAndCreatedAtAfter(
                    followeeIds, since, Sort.by(Sort.Direction.DESC, "createdAt"));

            for (Post post : followeePosts) {
                double score = algorithmService.computeScore(post, true);
                entries.add(ZSetOperations.TypedTuple.of(post.getId(), score));
            }
            log.debug("Rebuild: {} followee posts found for user={}", followeePosts.size(), userId);
        }

        // 2. Explore posts (strangers — isFollower=false, lower scores).
        List<Post> explorePosts = postRepository.findRecentByAuthorIdsNotInAndCreatedAtAfter(
                followeeIds,
                since,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        // Cap explore posts during rebuild to avoid flooding the timeline.
        int exploreCap = (int) Math.min(explorePosts.size(), exploreMaxInjectPerPost * 2L);
        for (Post post : explorePosts.subList(0, exploreCap)) {
            double score = algorithmService.computeScore(post, false);
            entries.add(ZSetOperations.TypedTuple.of(post.getId(), score));
        }
        log.debug("Rebuild: {} explore posts injected for user={}", exploreCap, userId);

        feedRedisRepository.warmUpTimeline(userId, entries);
    }

    // ─── Follow / Unfollow side-effects ──────────────────────────────────────

    /**
     * Called after a new follow relationship is created.
     *
     * Currently a no-op: future posts from the followee will reach the follower
     * via the normal fanOut() path. We deliberately do not backfill historical
     * posts here to keep the follow action fast and side-effect-light.
     *
     * This method exists as an explicit hook so future features (e.g. backfill,
     * Kafka events) have a clean integration point without touching FollowService.
     *
     * @param followerId the user who just followed
     * @param followeeId the user who was just followed
     */
    public void onFollow(String followerId, String followeeId) {
        log.debug("onFollow hook: follower={} followee={} (no timeline backfill)", followerId, followeeId);
    }

    /**
     * Called after a follow relationship is deleted.
     *
     * Removes all posts authored by {@code followeeId} from {@code followerId}'s
     * Redis timeline. We look up the unfollowed author's recent post IDs from
     * MongoDB (up to {@code warmupLookbackHours} ago) and call ZREM on each.
     *
     * This is best-effort: posts older than the lookback window are not removed,
     * but their near-zero recency scores make them invisible in practice.
     *
     * @param followerId the user who just unfollowed
     * @param followeeId the user who was just unfollowed
     */
    public void onUnfollow(String followerId, String followeeId) {
        Instant since = Instant.now().minus(warmupLookbackHours, ChronoUnit.HOURS);

        List<Post> authorPosts = postRepository.findRecentByAuthorIdsAndCreatedAtAfter(
                List.of(followeeId),
                since,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        if (authorPosts.isEmpty()) {
            log.debug("onUnfollow: no recent posts found for followee={}, nothing to remove", followeeId);
            return;
        }

        for (Post post : authorPosts) {
            feedRedisRepository.removeFromTimeline(followerId, post.getId());
        }

        log.info("onUnfollow: removed {} posts by followee={} from follower={} timeline",
                authorPosts.size(), followeeId, followerId);
    }

    // ─── Mapping ─────────────────────────────────────────────────────────────

    private PostDTO toPostDTO(Post post, boolean likedByMe) {
        return new PostDTO.Builder()
                .id(post.getId())
                .authorId(post.getAuthorId())
                .content(post.getContent())
                .images(post.getImages())
                .createdAt(post.getCreatedAt())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .repostCount(post.getRepostCount())
                .likedByMe(likedByMe)
                .build();
    }
}

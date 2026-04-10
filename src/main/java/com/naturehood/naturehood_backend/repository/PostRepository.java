package com.naturehood.naturehood_backend.repository;

import com.naturehood.naturehood_backend.domain.Post;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PostRepository extends MongoRepository<Post, String> {

    /**
     * Fetch multiple posts by their IDs in a single query.
     * Used to hydrate feed after reading postIds from Redis sorted set.
     */
    List<Post> findByIdIn(List<String> ids);

    /**
     * Fetch posts by author, newest first (for profile feed).
     */
    List<Post> findByAuthorIdAndDeletedFalseOrderByCreatedAtDesc(String authorId);

    /**
     * Soft-delete check.
     */
    @Query("{'_id': ?0, 'deleted': false}")
    java.util.Optional<Post> findActiveById(String id);

    /**
     * Fetch the most recent non-deleted posts from a specific set of authors,
     * sorted by createdAt descending.
     *
     * Used by:
     *  1. Timeline warm-up after a Redis crash — rebuild a user's timeline from
     *     the posts of the people they follow.
     *  2. Explore fan-out — pick recent posts from non-followees to surface to
     *     a user's timeline with a lower affinity score.
     */
    @Query("{'authorId': {$in: ?0}, 'deleted': false, 'createdAt': {$gte: ?1}}")
    List<Post> findRecentByAuthorIdsAndCreatedAtAfter(List<String> authorIds,
                                                      Instant since,
                                                      Sort sort);

    /**
     * Fetch recent non-deleted posts from authors NOT in the exclusion list,
     * sorted by (likeCount + commentCount + repostCount) descending then by
     * createdAt descending.
     *
     * Used by explore fan-out to pick trending posts from strangers.
     * The sort is applied by the caller via Sort parameter so it stays
     * flexible without requiring a custom aggregation.
     */
    @Query("{'authorId': {$nin: ?0}, 'deleted': false, 'createdAt': {$gte: ?1}}")
    List<Post> findRecentByAuthorIdsNotInAndCreatedAtAfter(List<String> excludedAuthorIds,
                                                           Instant since,
                                                           Sort sort);
}

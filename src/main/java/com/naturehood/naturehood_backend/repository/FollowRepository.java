package com.naturehood.naturehood_backend.repository;

import com.naturehood.naturehood_backend.domain.Follow;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FollowRepository extends MongoRepository<Follow, String> {

    /**
     * Returns all follower IDs for a given followee.
     * Critical for fan-out: "who should receive this post in their timeline?"
     */
    @Query(value = "{'followeeId': ?0}", fields = "{'followerId': 1, '_id': 0}")
    List<Follow> findByFolloweeId(String followeeId);

    /**
     * Returns all followee IDs for a given follower.
     * Used for "following" list on profile.
     */
    List<Follow> findByFollowerId(String followerId);

    boolean existsByFollowerIdAndFolloweeId(String followerId, String followeeId);

    void deleteByFollowerIdAndFolloweeId(String followerId, String followeeId);

    long countByFolloweeId(String followeeId);

    long countByFollowerId(String followerId);
}

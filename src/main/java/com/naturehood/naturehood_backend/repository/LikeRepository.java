package com.naturehood.naturehood_backend.repository;

import com.naturehood.naturehood_backend.domain.Like;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LikeRepository extends MongoRepository<Like, String> {

    boolean existsByTargetIdAndUserIdAndTargetType(String targetId, String userId, Like.TargetType targetType);

    Optional<Like> findByTargetIdAndUserIdAndTargetType(String targetId, String userId, Like.TargetType targetType);

    long countByTargetIdAndTargetType(String targetId, Like.TargetType targetType);
}

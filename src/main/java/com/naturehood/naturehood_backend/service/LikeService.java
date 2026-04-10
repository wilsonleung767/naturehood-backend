package com.naturehood.naturehood_backend.service;

import com.naturehood.naturehood_backend.domain.Comment;
import com.naturehood.naturehood_backend.domain.Like;
import com.naturehood.naturehood_backend.domain.Post;
import com.naturehood.naturehood_backend.repository.CommentRepository;
import com.naturehood.naturehood_backend.repository.LikeRepository;
import com.naturehood.naturehood_backend.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/**
 * Like/unlike service for Posts and Comments.
 */
@Service
public class LikeService {

    private static final Logger log = LoggerFactory.getLogger(LikeService.class);

    private final LikeRepository likeRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final MongoTemplate mongoTemplate;

    public LikeService(LikeRepository likeRepository,
                       PostRepository postRepository,
                       CommentRepository commentRepository,
                       MongoTemplate mongoTemplate) {
        this.likeRepository = likeRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public boolean togglePostLike(String postId, String userId) {
        postRepository.findActiveById(postId)
                .orElseThrow(() -> new NoSuchElementException("Post not found: " + postId));
        return toggleLike(postId, userId, Like.TargetType.POST, Post.class, "posts");
    }

    public boolean toggleCommentLike(String commentId, String userId) {
        commentRepository.findById(commentId)
                .orElseThrow(() -> new NoSuchElementException("Comment not found: " + commentId));
        return toggleLike(commentId, userId, Like.TargetType.COMMENT, Comment.class, "comments");
    }

    private boolean toggleLike(
            String targetId,
            String userId,
            Like.TargetType targetType,
            Class<?> entityClass,
            String collectionName
    ) {
        boolean alreadyLiked = likeRepository.existsByTargetIdAndUserIdAndTargetType(
                targetId, userId, targetType);

        if (alreadyLiked) {
            likeRepository.findByTargetIdAndUserIdAndTargetType(targetId, userId, targetType)
                    .ifPresent(like -> {
                        likeRepository.delete(like);
                        decrementLikeCount(targetId, collectionName);
                    });
            log.debug("Unliked: target={} user={}", targetId, userId);
            return false;
        } else {
            try {
                Like like = new Like.Builder()
                        .targetId(targetId)
                        .userId(userId)
                        .targetType(targetType)
                        .build();
                likeRepository.save(like);
                incrementLikeCount(targetId, collectionName);
                log.debug("Liked: target={} user={}", targetId, userId);
                return true;
            } catch (DuplicateKeyException e) {
                log.debug("Duplicate like ignored: target={} user={}", targetId, userId);
                return true;
            }
        }
    }

    private void incrementLikeCount(String targetId, String collectionName) {
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(targetId)),
                new Update().inc("likeCount", 1),
                collectionName
        );
    }

    private void decrementLikeCount(String targetId, String collectionName) {
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(targetId)),
                new Update().inc("likeCount", -1),
                collectionName
        );
    }
}

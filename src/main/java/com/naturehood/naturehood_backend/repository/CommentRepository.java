package com.naturehood.naturehood_backend.repository;

import com.naturehood.naturehood_backend.domain.Comment;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommentRepository extends MongoRepository<Comment, String> {

    /**
     * Top-level comments for a post (parentCommentId is null), sorted by createdAt ASC.
     */
    List<Comment> findByPostIdAndParentCommentIdIsNull(String postId, Sort sort);

    /**
     * Replies to a specific top-level comment, sorted by createdAt ASC.
     */
    List<Comment> findByParentCommentId(String parentCommentId, Sort sort);

    /**
     * All top-level comments AND replies for a post (used for count).
     */
    long countByPostId(String postId);

    /**
     * Cursor-based top-level comment fetch (id > cursorId for stable pagination).
     * We use MongoTemplate for the actual cursor query; this covers simple lookups.
     */
    Optional<Comment> findByIdAndParentCommentIdIsNull(String id);
}

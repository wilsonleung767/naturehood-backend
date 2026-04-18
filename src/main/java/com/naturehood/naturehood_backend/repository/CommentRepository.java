package com.naturehood.naturehood_backend.repository;

import com.naturehood.naturehood_backend.domain.Comment;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

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
     * Replies to a specific comment scoped to a post, sorted as specified.
     */
    List<Comment> findByPostIdAndParentCommentId(String postId, String parentCommentId, Sort sort);

    /**
     * All top-level comments AND replies for a post (used for count).
     */
    long countByPostId(String postId);

}

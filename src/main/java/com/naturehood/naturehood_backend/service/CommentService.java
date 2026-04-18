package com.naturehood.naturehood_backend.service;

import com.naturehood.naturehood_backend.domain.Comment;
import com.naturehood.naturehood_backend.domain.Like;
import com.naturehood.naturehood_backend.domain.Post;
import com.naturehood.naturehood_backend.dto.request.CreateCommentRequest;
import com.naturehood.naturehood_backend.dto.response.CommentDTO;
import com.naturehood.naturehood_backend.dto.response.FeedResponse;
import com.naturehood.naturehood_backend.repository.CommentRepository;
import com.naturehood.naturehood_backend.repository.LikeRepository;
import com.naturehood.naturehood_backend.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Comment service: create and retrieve comments with cursor-based pagination.
 *
 * Supports unlimited nesting depth: any comment can be replied to.
 * Replies are stored flat in MongoDB (each comment carries parentCommentId)
 * and the tree is assembled recursively on read.
 */
@Service
public class CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentService.class);
    private static final int MAX_PREVIEW_REPLIES = 3;

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final MongoTemplate mongoTemplate;

    public CommentService(CommentRepository commentRepository,
                          PostRepository postRepository,
                          LikeRepository likeRepository,
                          MongoTemplate mongoTemplate) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.likeRepository = likeRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public CommentDTO addComment(String postId, String authorId, CreateCommentRequest request) {
        postRepository.findActiveById(postId)
                .orElseThrow(() -> new NoSuchElementException("Post not found: " + postId));

        if (request.getParentCommentId() != null) {
            Comment parent = commentRepository.findById(request.getParentCommentId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Parent comment not found: " + request.getParentCommentId()));
            if (!parent.getPostId().equals(postId)) {
                throw new IllegalArgumentException("Parent comment does not belong to post: " + postId);
            }
        }

        Comment comment = new Comment.Builder()
                .postId(postId)
                .parentCommentId(request.getParentCommentId())
                .authorId(authorId)
                .content(request.getContent())
                .images(request.getImages() != null ? request.getImages() : new ArrayList<>())
                .contentType(request.getContentType())
                .build();

        comment = commentRepository.save(comment);
        log.info("Comment created: id={} on post={} by author={}", comment.getId(), postId, authorId);

        Query postQuery = new Query(Criteria.where("id").is(postId));
        Update postUpdate = new Update().inc("commentCount", 1);
        mongoTemplate.updateFirst(postQuery, postUpdate, Post.class);

        return toCommentDTO(comment, false, 0, null);
    }

    public FeedResponse<CommentDTO> getRepliesForComment(
            String postId, String commentId, String requesterId, String cursor, int limit) {

        // Verify the parent comment exists and belongs to the post
        Comment parent = commentRepository.findById(commentId)
                .orElseThrow(() -> new NoSuchElementException("Comment not found: " + commentId));
        if (!parent.getPostId().equals(postId)) {
            throw new IllegalArgumentException("Comment does not belong to post: " + postId);
        }

        int fetchSize = limit + 1;
        Sort ascCreated = Sort.by(Sort.Direction.ASC, "createdAt");

        List<Comment> replies;
        if (cursor == null || cursor.isBlank()) {
            replies = commentRepository.findByPostIdAndParentCommentId(postId, commentId, ascCreated);
        } else {
            String decodedCursorId = decodeCursor(cursor);
            Query q = new Query(
                    Criteria.where("postId").is(postId)
                            .and("parentCommentId").is(commentId)
                            .and("_id").gt(new org.bson.types.ObjectId(decodedCursorId))
            ).with(ascCreated).limit(fetchSize);
            replies = mongoTemplate.find(q, Comment.class);
        }

        boolean hasNext = replies.size() > limit;
        if (hasNext) {
            replies = replies.subList(0, limit);
        }

        List<CommentDTO> dtos = replies.stream()
                .map(c -> buildCommentTree(c, requesterId))
                .collect(Collectors.toList());

        String nextCursor = null;
        if (hasNext && !replies.isEmpty()) {
            nextCursor = encodeCursor(replies.get(replies.size() - 1).getId());
        }

        return FeedResponse.of(dtos, nextCursor);
    }

    public FeedResponse<CommentDTO> getComments(
            String postId, String requesterId, String cursor, int limit) {

        int fetchSize = limit + 1;
        Sort ascCreated = Sort.by(Sort.Direction.ASC, "createdAt");

        List<Comment> topLevel;
        if (cursor == null || cursor.isBlank()) {
            topLevel = commentRepository.findByPostIdAndParentCommentIdIsNull(postId, ascCreated);
        } else {
            String decodedCursorId = decodeCursor(cursor);
            Query q = new Query(
                    Criteria.where("postId").is(postId)
                            .and("parentCommentId").isNull()
                            .and("_id").gt(new org.bson.types.ObjectId(decodedCursorId))
            ).with(ascCreated).limit(fetchSize);
            topLevel = mongoTemplate.find(q, Comment.class);
        }

        boolean hasNext = topLevel.size() > limit;
        if (hasNext) {
            topLevel = topLevel.subList(0, limit);
        }

        List<CommentDTO> dtos = topLevel.stream()
                .map(c -> buildCommentTree(c, requesterId))
                .collect(Collectors.toList());

        String nextCursor = null;
        if (hasNext && !topLevel.isEmpty()) {
            nextCursor = encodeCursor(topLevel.get(topLevel.size() - 1).getId());
        }

        return FeedResponse.of(dtos, nextCursor);
    }

    // ─── Cursor helpers ───────────────────────────────────────────────────────

    private String encodeCursor(String commentId) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(commentId.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeCursor(String cursor) {
        return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
    }

    // ─── Tree building ────────────────────────────────────────────────────────

    /**
     * Recursively builds a comment tree.
     *
     * Each comment is expanded with a preview of its direct children (capped at
     * MAX_PREVIEW_REPLIES). Children are themselves expanded recursively, so the
     * full nesting is preserved in the response.
     */
    private CommentDTO buildCommentTree(Comment comment, String requesterId) {
        boolean likedByMe = likeRepository.existsByTargetIdAndUserIdAndTargetType(
                comment.getId(), requesterId, Like.TargetType.COMMENT);

        List<Comment> children = commentRepository.findByParentCommentId(
                comment.getId(), Sort.by(Sort.Direction.ASC, "createdAt"));

        int totalReplyCount = children.size();

        if (children.size() > MAX_PREVIEW_REPLIES) {
            children = children.subList(0, MAX_PREVIEW_REPLIES);
        }

        List<CommentDTO> childDTOs = children.stream()
                .map(c -> buildCommentTree(c, requesterId))
                .collect(Collectors.toList());

        return toCommentDTO(comment, likedByMe, totalReplyCount, childDTOs.isEmpty() ? null : childDTOs);
    }

    // ─── Mapping ─────────────────────────────────────────────────────────────

    private CommentDTO toCommentDTO(Comment comment, boolean likedByMe, int replyCount, List<CommentDTO> replies) {
        return new CommentDTO.Builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .parentCommentId(comment.getParentCommentId())
                .authorId(comment.getAuthorId())
                .content(comment.getContent())
                .images(comment.getImages())
                .contentType(comment.getContentType())
                .createdAt(comment.getCreatedAt())
                .likeCount(comment.getLikeCount())
                .likedByMe(likedByMe)
                .replyCount(replyCount)
                .replies(replies)
                .build();
    }
}

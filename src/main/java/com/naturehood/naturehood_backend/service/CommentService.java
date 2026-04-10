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
        Post post = postRepository.findActiveById(postId)
                .orElseThrow(() -> new NoSuchElementException("Post not found: " + postId));

        if (request.getParentCommentId() != null) {
            Comment parent = commentRepository.findByIdAndParentCommentIdIsNull(request.getParentCommentId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "parentCommentId must point to a top-level comment: " + request.getParentCommentId()));
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
                .build();

        comment = commentRepository.save(comment);
        log.info("Comment created: id={} on post={} by author={}", comment.getId(), postId, authorId);

        Query postQuery = new Query(Criteria.where("id").is(postId));
        Update postUpdate = new Update().inc("commentCount", 1);
        mongoTemplate.updateFirst(postQuery, postUpdate, Post.class);

        return toCommentDTO(comment, false, null);
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
                .map(c -> {
                    boolean likedByMe = likeRepository.existsByTargetIdAndUserIdAndTargetType(
                            c.getId(), requesterId, Like.TargetType.COMMENT);

                    List<Comment> replies = commentRepository.findByParentCommentId(
                            c.getId(),
                            Sort.by(Sort.Direction.ASC, "createdAt")
                    );
                    if (replies.size() > MAX_PREVIEW_REPLIES) {
                        replies = replies.subList(0, MAX_PREVIEW_REPLIES);
                    }

                    List<CommentDTO> replyDTOs = replies.stream()
                            .map(r -> {
                                boolean rLiked = likeRepository.existsByTargetIdAndUserIdAndTargetType(
                                        r.getId(), requesterId, Like.TargetType.COMMENT);
                                return toCommentDTO(r, rLiked, null);
                            })
                            .collect(Collectors.toList());

                    return toCommentDTO(c, likedByMe, replyDTOs);
                })
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

    // ─── Mapping ─────────────────────────────────────────────────────────────

    private CommentDTO toCommentDTO(Comment comment, boolean likedByMe, List<CommentDTO> replies) {
        return new CommentDTO.Builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .parentCommentId(comment.getParentCommentId())
                .authorId(comment.getAuthorId())
                .content(comment.getContent())
                .images(comment.getImages())
                .createdAt(comment.getCreatedAt())
                .likeCount(comment.getLikeCount())
                .likedByMe(likedByMe)
                .replies(replies)
                .build();
    }
}

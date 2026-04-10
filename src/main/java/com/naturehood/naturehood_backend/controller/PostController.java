package com.naturehood.naturehood_backend.controller;

import com.naturehood.naturehood_backend.dto.request.CreateCommentRequest;
import com.naturehood.naturehood_backend.dto.request.CreatePostRequest;
import com.naturehood.naturehood_backend.dto.response.ApiResponse;
import com.naturehood.naturehood_backend.dto.response.CommentDTO;
import com.naturehood.naturehood_backend.dto.response.FeedResponse;
import com.naturehood.naturehood_backend.dto.response.PostDTO;
import com.naturehood.naturehood_backend.service.CommentService;
import com.naturehood.naturehood_backend.service.LikeService;
import com.naturehood.naturehood_backend.service.PostService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for post and comment operations.
 */
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private static final Logger log = LoggerFactory.getLogger(PostController.class);

    private final PostService postService;
    private final CommentService commentService;
    private final LikeService likeService;

    public PostController(PostService postService, CommentService commentService, LikeService likeService) {
        this.postService = postService;
        this.commentService = commentService;
        this.likeService = likeService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PostDTO>> createPost(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreatePostRequest request
    ) {
        String userId = jwt.getSubject();
        PostDTO post = postService.createPost(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(post));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostDTO>> getPost(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String postId
    ) {
        String userId = jwt.getSubject();
        PostDTO post = postService.getPost(postId, userId);
        return ResponseEntity.ok(ApiResponse.ok(post));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String postId
    ) {
        String userId = jwt.getSubject();
        postService.deletePost(postId, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{postId}/like")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> togglePostLike(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String postId
    ) {
        String userId = jwt.getSubject();
        boolean liked = likeService.togglePostLike(postId, userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("liked", liked)));
    }

    @PostMapping("/{postId}/comments/{commentId}/like")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> toggleCommentLike(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String postId,
            @PathVariable String commentId
    ) {
        String userId = jwt.getSubject();
        boolean liked = likeService.toggleCommentLike(commentId, userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("liked", liked)));
    }

    @PostMapping("/{postId}/comments")
    public ResponseEntity<ApiResponse<CommentDTO>> addComment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String postId,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        String userId = jwt.getSubject();
        CommentDTO comment = commentService.addComment(postId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(comment));
    }

    @GetMapping("/{postId}/comments")
    public ResponseEntity<ApiResponse<FeedResponse<CommentDTO>>> getComments(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String postId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        String userId = jwt.getSubject();
        FeedResponse<CommentDTO> response = commentService.getComments(postId, userId, cursor, limit);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}

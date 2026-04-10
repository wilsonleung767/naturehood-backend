package com.naturehood.naturehood_backend.service;

import com.naturehood.naturehood_backend.domain.Post;
import com.naturehood.naturehood_backend.dto.request.CreatePostRequest;
import com.naturehood.naturehood_backend.dto.response.PostDTO;
import com.naturehood.naturehood_backend.repository.LikeRepository;
import com.naturehood.naturehood_backend.repository.PostRepository;
import com.naturehood.naturehood_backend.domain.Like;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.NoSuchElementException;

/**
 * Post management service.
 */
@Service
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);

    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final FeedService feedService;
    private final MongoTemplate mongoTemplate;

    public PostService(PostRepository postRepository,
                       LikeRepository likeRepository,
                       FeedService feedService,
                       MongoTemplate mongoTemplate) {
        this.postRepository = postRepository;
        this.likeRepository = likeRepository;
        this.feedService = feedService;
        this.mongoTemplate = mongoTemplate;
    }

    public PostDTO createPost(String authorId, CreatePostRequest request) {
        validateCreatePostRequest(request);

        Post post = new Post.Builder()
                .authorId(authorId)
                .content(request.getContent())
                .images(request.getImages() != null ? request.getImages() : new ArrayList<>())
                .build();

        post = postRepository.save(post);
        log.info("Post created: id={} by author={}", post.getId(), authorId);

        feedService.fanOut(post, authorId);

        return toPostDTO(post, false);
    }

    public PostDTO getPost(String postId, String requesterId) {
        Post post = postRepository.findActiveById(postId)
                .orElseThrow(() -> new NoSuchElementException("Post not found: " + postId));

        boolean likedByMe = likeRepository.existsByTargetIdAndUserIdAndTargetType(
                postId, requesterId, Like.TargetType.POST);

        return toPostDTO(post, likedByMe);
    }

    public void deletePost(String postId, String authorId) {
        Post post = postRepository.findActiveById(postId)
                .orElseThrow(() -> new NoSuchElementException("Post not found: " + postId));

        if (!post.getAuthorId().equals(authorId)) {
            throw new SecurityException("Not authorized to delete post: " + postId);
        }

        Query query = new Query(Criteria.where("id").is(postId));
        Update update = new Update().set("deleted", true);
        mongoTemplate.updateFirst(query, update, Post.class);

        log.info("Post soft-deleted: id={} by author={}", postId, authorId);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void validateCreatePostRequest(CreatePostRequest request) {
        boolean hasContent = request.getContent() != null && !request.getContent().isBlank();
        boolean hasImages = request.getImages() != null && !request.getImages().isEmpty();
        if (!hasContent && !hasImages) {
            throw new IllegalArgumentException("Post must have content or at least one image");
        }
    }

    PostDTO toPostDTO(Post post, boolean likedByMe) {
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

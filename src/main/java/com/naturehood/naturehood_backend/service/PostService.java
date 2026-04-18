package com.naturehood.naturehood_backend.service;

import com.naturehood.naturehood_backend.domain.Post;
import com.naturehood.naturehood_backend.domain.Like;
import com.naturehood.naturehood_backend.dto.request.CreatePostRequest;
import com.naturehood.naturehood_backend.dto.response.FeedResponse;
import com.naturehood.naturehood_backend.dto.response.PostDTO;
import com.naturehood.naturehood_backend.repository.LikeRepository;
import com.naturehood.naturehood_backend.repository.PostRepository;
import org.bson.types.ObjectId;
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
                .contentType(request.getContentType())
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

    /**
     * Return cursor-paginated posts authored by a given user, newest first.
     *
     * Cursor encoding: Base64URL of the last post's MongoDB ObjectId. Because
     * ObjectIds are monotonically increasing, {@code _id < cursor} is equivalent
     * to {@code createdAt < cursor.createdAt}, with no risk of ties.
     *
     * @param targetUserId the profile being viewed (may differ from the caller)
     * @param requesterId  the authenticated caller (used for likedByMe)
     * @param cursor       opaque cursor from a previous response, or null for page 1
     * @param limit        page size (clamped to 50 in the controller)
     */
    public FeedResponse<PostDTO> getUserPosts(
            String targetUserId, String requesterId, String cursor, int limit) {

        int fetchSize = limit + 1;
        Sort descCreated = Sort.by(Sort.Direction.DESC, "createdAt");

        List<Post> posts;
        if (cursor == null || cursor.isBlank()) {
            // First page — use the simple repository-derived query.
            Query q = new Query(
                    Criteria.where("authorId").is(targetUserId).and("deleted").is(false)
            ).with(descCreated).limit(fetchSize);
            posts = mongoTemplate.find(q, Post.class);
        } else {
            // Subsequent pages — walk backwards using _id < cursorId.
            String cursorId = decodeCursor(cursor);
            Query q = new Query(
                    Criteria.where("authorId").is(targetUserId)
                            .and("deleted").is(false)
                            .and("_id").lt(new ObjectId(cursorId))
            ).with(descCreated).limit(fetchSize);
            posts = mongoTemplate.find(q, Post.class);
        }

        boolean hasNext = posts.size() > limit;
        if (hasNext) {
            posts = posts.subList(0, limit);
        }

        List<PostDTO> dtos = posts.stream()
                .map(p -> {
                    boolean likedByMe = likeRepository.existsByTargetIdAndUserIdAndTargetType(
                            p.getId(), requesterId, Like.TargetType.POST);
                    return toPostDTO(p, likedByMe);
                })
                .collect(Collectors.toList());

        String nextCursor = null;
        if (hasNext && !posts.isEmpty()) {
            nextCursor = encodeCursor(posts.get(posts.size() - 1).getId());
        }

        return FeedResponse.of(dtos, nextCursor);
    }

    // ─── Cursor helpers ───────────────────────────────────────────────────────

    private String encodeCursor(String postId) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(postId.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeCursor(String cursor) {
        try {
            return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid pagination cursor");
        }
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
                .contentType(post.getContentType())
                .createdAt(post.getCreatedAt())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .repostCount(post.getRepostCount())
                .likedByMe(likedByMe)
                .build();
    }
}

package com.naturehood.naturehood_backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Mobile-friendly DTO for a comment.
 *
 * For top-level comments, {@code replies} is populated (may be empty list).
 * For replies themselves, {@code replies} is null (only 1 level of nesting).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommentDTO {

    private String id;
    private String postId;

    /** Null for top-level comments. */
    private String parentCommentId;

    private String authorId;
    private String content;
    private List<String> images;
    private Instant createdAt;
    private int likeCount;

    /** True if the requesting user has liked this comment. */
    private boolean likedByMe;

    /**
     * Direct replies (populated only for top-level comments, null for replies).
     */
    private List<CommentDTO> replies;

    public CommentDTO() {}

    public CommentDTO(String id, String postId, String parentCommentId, String authorId,
                      String content, List<String> images, Instant createdAt,
                      int likeCount, boolean likedByMe, List<CommentDTO> replies) {
        this.id = id;
        this.postId = postId;
        this.parentCommentId = parentCommentId;
        this.authorId = authorId;
        this.content = content;
        this.images = images;
        this.createdAt = createdAt;
        this.likeCount = likeCount;
        this.likedByMe = likedByMe;
        this.replies = replies;
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getPostId() { return postId; }
    public String getParentCommentId() { return parentCommentId; }
    public String getAuthorId() { return authorId; }
    public String getContent() { return content; }
    public List<String> getImages() { return images; }
    public Instant getCreatedAt() { return createdAt; }
    public int getLikeCount() { return likeCount; }
    public boolean isLikedByMe() { return likedByMe; }
    public List<CommentDTO> getReplies() { return replies; }

    // ─── Setters ──────────────────────────────────────────────────────────────

    public void setId(String id) { this.id = id; }
    public void setPostId(String postId) { this.postId = postId; }
    public void setParentCommentId(String parentCommentId) { this.parentCommentId = parentCommentId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    public void setContent(String content) { this.content = content; }
    public void setImages(List<String> images) { this.images = images; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }
    public void setLikedByMe(boolean likedByMe) { this.likedByMe = likedByMe; }
    public void setReplies(List<CommentDTO> replies) { this.replies = replies; }

    // ─── Builder ──────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id;
        private String postId;
        private String parentCommentId;
        private String authorId;
        private String content;
        private List<String> images;
        private Instant createdAt;
        private int likeCount;
        private boolean likedByMe;
        private List<CommentDTO> replies;

        public Builder id(String id) { this.id = id; return this; }
        public Builder postId(String postId) { this.postId = postId; return this; }
        public Builder parentCommentId(String parentCommentId) { this.parentCommentId = parentCommentId; return this; }
        public Builder authorId(String authorId) { this.authorId = authorId; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder images(List<String> images) { this.images = images; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder likeCount(int likeCount) { this.likeCount = likeCount; return this; }
        public Builder likedByMe(boolean likedByMe) { this.likedByMe = likedByMe; return this; }
        public Builder replies(List<CommentDTO> replies) { this.replies = replies; return this; }

        public CommentDTO build() {
            return new CommentDTO(id, postId, parentCommentId, authorId, content, images,
                    createdAt, likeCount, likedByMe, replies);
        }
    }
}

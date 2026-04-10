package com.naturehood.naturehood_backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * SSE event payload pushed to connected clients.
 *
 * Sent as the 'data' field of a Server-Sent Event with event name "new-post".
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NewPostEvent {

    private String id;
    private String authorId;
    private String content;
    private List<String> images;
    private Instant createdAt;
    private int likeCount;
    private int commentCount;
    private int repostCount;

    public NewPostEvent() {}

    public NewPostEvent(String id, String authorId, String content, List<String> images,
                        Instant createdAt, int likeCount, int commentCount, int repostCount) {
        this.id = id;
        this.authorId = authorId;
        this.content = content;
        this.images = images;
        this.createdAt = createdAt;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.repostCount = repostCount;
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getAuthorId() { return authorId; }
    public String getContent() { return content; }
    public List<String> getImages() { return images; }
    public Instant getCreatedAt() { return createdAt; }
    public int getLikeCount() { return likeCount; }
    public int getCommentCount() { return commentCount; }
    public int getRepostCount() { return repostCount; }

    // ─── Setters ──────────────────────────────────────────────────────────────

    public void setId(String id) { this.id = id; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    public void setContent(String content) { this.content = content; }
    public void setImages(List<String> images) { this.images = images; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }
    public void setCommentCount(int commentCount) { this.commentCount = commentCount; }
    public void setRepostCount(int repostCount) { this.repostCount = repostCount; }

    // ─── Builder ──────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id;
        private String authorId;
        private String content;
        private List<String> images;
        private Instant createdAt;
        private int likeCount;
        private int commentCount;
        private int repostCount;

        public Builder id(String id) { this.id = id; return this; }
        public Builder authorId(String authorId) { this.authorId = authorId; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder images(List<String> images) { this.images = images; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder likeCount(int likeCount) { this.likeCount = likeCount; return this; }
        public Builder commentCount(int commentCount) { this.commentCount = commentCount; return this; }
        public Builder repostCount(int repostCount) { this.repostCount = repostCount; return this; }

        public NewPostEvent build() {
            return new NewPostEvent(id, authorId, content, images, createdAt, likeCount, commentCount, repostCount);
        }
    }
}

package com.naturehood.naturehood_backend.domain;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "posts")
@CompoundIndexes({
        @CompoundIndex(name = "author_created_idx", def = "{'authorId': 1, 'createdAt': -1}"),
})
public class Post {

    @Id
    private String id;

    @Indexed
    private String authorId;

    private String content;

    private List<String> images = new ArrayList<>();

    @CreatedDate
    @Indexed
    private Instant createdAt;

    private int likeCount = 0;

    private int commentCount = 0;

    private int repostCount = 0;

    /** Whether this post has been deleted (soft delete). */
    private boolean deleted = false;

    public Post() {}

    public Post(String id, String authorId, String content, List<String> images,
                Instant createdAt, int likeCount, int commentCount, int repostCount, boolean deleted) {
        this.id = id;
        this.authorId = authorId;
        this.content = content;
        this.images = images;
        this.createdAt = createdAt;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.repostCount = repostCount;
        this.deleted = deleted;
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
    public boolean isDeleted() { return deleted; }

    // ─── Setters ──────────────────────────────────────────────────────────────

    public void setId(String id) { this.id = id; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    public void setContent(String content) { this.content = content; }
    public void setImages(List<String> images) { this.images = images; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }
    public void setCommentCount(int commentCount) { this.commentCount = commentCount; }
    public void setRepostCount(int repostCount) { this.repostCount = repostCount; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    // ─── Builder ──────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id;
        private String authorId;
        private String content;
        private List<String> images = new ArrayList<>();
        private Instant createdAt;
        private int likeCount = 0;
        private int commentCount = 0;
        private int repostCount = 0;
        private boolean deleted = false;

        public Builder id(String id) { this.id = id; return this; }
        public Builder authorId(String authorId) { this.authorId = authorId; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder images(List<String> images) { this.images = images; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder likeCount(int likeCount) { this.likeCount = likeCount; return this; }
        public Builder commentCount(int commentCount) { this.commentCount = commentCount; return this; }
        public Builder repostCount(int repostCount) { this.repostCount = repostCount; return this; }
        public Builder deleted(boolean deleted) { this.deleted = deleted; return this; }

        public Post build() {
            return new Post(id, authorId, content, images, createdAt, likeCount, commentCount, repostCount, deleted);
        }
    }
}

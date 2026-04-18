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

/**
 * Represents a comment on a post.
 *
 * Depth rules:
 *  - Top-level comment: parentCommentId == null
 *  - Reply:             parentCommentId points to any existing comment (unlimited depth)
 *
 * Comments are stored flat in MongoDB. The tree is assembled recursively on read.
 */
@Document(collection = "comments")
@CompoundIndexes({
        @CompoundIndex(name = "post_created_idx", def = "{'postId': 1, 'createdAt': 1}"),
        @CompoundIndex(name = "post_parent_created_idx", def = "{'postId': 1, 'parentCommentId': 1, 'createdAt': 1}")
})
public class Comment {

    @Id
    private String id;

    @Indexed
    private String postId;

    /** Null for top-level comments; points to a top-level comment ID for replies. */
    @Indexed
    private String parentCommentId;

    private String authorId;

    private String content;

    private List<String> images = new ArrayList<>();

    private String contentType;

    @CreatedDate
    private Instant createdAt;

    private int likeCount = 0;

    public Comment() {}

    public Comment(String id, String postId, String parentCommentId, String authorId,
                   String content, List<String> images, String contentType,
                   Instant createdAt, int likeCount) {
        this.id = id;
        this.postId = postId;
        this.parentCommentId = parentCommentId;
        this.authorId = authorId;
        this.content = content;
        this.images = images;
        this.contentType = contentType;
        this.createdAt = createdAt;
        this.likeCount = likeCount;
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getPostId() { return postId; }
    public String getParentCommentId() { return parentCommentId; }
    public String getAuthorId() { return authorId; }
    public String getContent() { return content; }
    public List<String> getImages() { return images; }
    public String getContentType() { return contentType; }
    public Instant getCreatedAt() { return createdAt; }
    public int getLikeCount() { return likeCount; }

    // ─── Setters ──────────────────────────────────────────────────────────────

    public void setId(String id) { this.id = id; }
    public void setPostId(String postId) { this.postId = postId; }
    public void setParentCommentId(String parentCommentId) { this.parentCommentId = parentCommentId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    public void setContent(String content) { this.content = content; }
    public void setImages(List<String> images) { this.images = images; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }

    // ─── Builder ──────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id;
        private String postId;
        private String parentCommentId;
        private String authorId;
        private String content;
        private List<String> images = new ArrayList<>();
        private String contentType;
        private Instant createdAt;
        private int likeCount = 0;

        public Builder id(String id) { this.id = id; return this; }
        public Builder postId(String postId) { this.postId = postId; return this; }
        public Builder parentCommentId(String parentCommentId) { this.parentCommentId = parentCommentId; return this; }
        public Builder authorId(String authorId) { this.authorId = authorId; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder images(List<String> images) { this.images = images; return this; }
        public Builder contentType(String contentType) { this.contentType = contentType; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder likeCount(int likeCount) { this.likeCount = likeCount; return this; }

        public Comment build() {
            return new Comment(id, postId, parentCommentId, authorId, content, images, contentType, createdAt,
                    likeCount);
        }
    }
}

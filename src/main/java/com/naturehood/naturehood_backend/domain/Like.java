package com.naturehood.naturehood_backend.domain;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Represents a "like" action on either a Post or a Comment.
 *
 * The compound unique index on (targetId, userId, targetType) prevents duplicate likes.
 */
@Document(collection = "likes")
@CompoundIndexes({
        @CompoundIndex(
                name = "target_user_type_uidx",
                def = "{'targetId': 1, 'userId': 1, 'targetType': 1}",
                unique = true
        ),
        @CompoundIndex(
                name = "target_type_idx",
                def = "{'targetId': 1, 'targetType': 1}"
        )
})
public class Like {

    @Id
    private String id;

    /** The ID of the post or comment that was liked. */
    private String targetId;

    /** Discriminator for the target type. */
    private TargetType targetType;

    /** The user who performed the like. */
    private String userId;

    @CreatedDate
    private Instant createdAt;

    public Like() {}

    public Like(String id, String targetId, TargetType targetType, String userId, Instant createdAt) {
        this.id = id;
        this.targetId = targetId;
        this.targetType = targetType;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getTargetId() { return targetId; }
    public TargetType getTargetType() { return targetType; }
    public String getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }

    // ─── Setters ──────────────────────────────────────────────────────────────

    public void setId(String id) { this.id = id; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public void setTargetType(TargetType targetType) { this.targetType = targetType; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    // ─── Builder ──────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id;
        private String targetId;
        private TargetType targetType;
        private String userId;
        private Instant createdAt;

        public Builder id(String id) { this.id = id; return this; }
        public Builder targetId(String targetId) { this.targetId = targetId; return this; }
        public Builder targetType(TargetType targetType) { this.targetType = targetType; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public Like build() {
            return new Like(id, targetId, targetType, userId, createdAt);
        }
    }

    public enum TargetType {
        POST, COMMENT
    }
}

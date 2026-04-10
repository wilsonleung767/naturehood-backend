package com.naturehood.naturehood_backend.domain;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Represents a follow relationship between two users.
 *
 * - followerId: the user who is following
 * - followeeId: the user being followed
 */
@Document(collection = "follows")
@CompoundIndexes({
        @CompoundIndex(
                name = "follower_followee_uidx",
                def = "{'followerId': 1, 'followeeId': 1}",
                unique = true
        )
})
public class Follow {

    @Id
    private String id;

    private String followerId;

    @Indexed
    private String followeeId;

    @CreatedDate
    private Instant createdAt;

    public Follow() {}

    public Follow(String id, String followerId, String followeeId, Instant createdAt) {
        this.id = id;
        this.followerId = followerId;
        this.followeeId = followeeId;
        this.createdAt = createdAt;
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getFollowerId() { return followerId; }
    public String getFolloweeId() { return followeeId; }
    public Instant getCreatedAt() { return createdAt; }

    // ─── Setters ──────────────────────────────────────────────────────────────

    public void setId(String id) { this.id = id; }
    public void setFollowerId(String followerId) { this.followerId = followerId; }
    public void setFolloweeId(String followeeId) { this.followeeId = followeeId; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    // ─── Builder ──────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id;
        private String followerId;
        private String followeeId;
        private Instant createdAt;

        public Builder id(String id) { this.id = id; return this; }
        public Builder followerId(String followerId) { this.followerId = followerId; return this; }
        public Builder followeeId(String followeeId) { this.followeeId = followeeId; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public Follow build() {
            return new Follow(id, followerId, followeeId, createdAt);
        }
    }
}

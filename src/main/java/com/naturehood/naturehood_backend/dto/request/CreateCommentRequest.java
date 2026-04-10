package com.naturehood.naturehood_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * Request body for POST /api/posts/{postId}/comments
 *
 * parentCommentId:
 *  - null → top-level comment
 *  - non-null → reply to the specified top-level comment (max depth = 1)
 */
public class CreateCommentRequest {

    /** Optional: if set, this is a reply to that comment (must be a top-level comment). */
    private String parentCommentId;

    @NotBlank(message = "Comment content must not be blank")
    @Size(max = 300, message = "Comment content must not exceed 300 characters")
    private String content;

    @Size(max = 2, message = "A comment can have at most 2 images")
    private List<String> images = new ArrayList<>();

    public CreateCommentRequest() {}

    // ─── Getters ──────────────────────────────────────────────────────────────

    public String getParentCommentId() { return parentCommentId; }
    public String getContent() { return content; }
    public List<String> getImages() { return images; }

    // ─── Setters ──────────────────────────────────────────────────────────────

    public void setParentCommentId(String parentCommentId) { this.parentCommentId = parentCommentId; }
    public void setContent(String content) { this.content = content; }
    public void setImages(List<String> images) { this.images = images; }
}

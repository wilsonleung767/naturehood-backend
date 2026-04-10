package com.naturehood.naturehood_backend.dto.request;

import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * Request body for POST /api/posts
 *
 * At least one of content or images must be non-empty (enforced in service layer).
 */
public class CreatePostRequest {

    @Size(max = 500, message = "Post content must not exceed 500 characters")
    private String content;

    @Size(max = 4, message = "A post can have at most 4 images")
    private List<String> images = new ArrayList<>();

    public CreatePostRequest() {}

    // ─── Getters ──────────────────────────────────────────────────────────────

    public String getContent() { return content; }
    public List<String> getImages() { return images; }

    // ─── Setters ──────────────────────────────────────────────────────────────

    public void setContent(String content) { this.content = content; }
    public void setImages(List<String> images) { this.images = images; }
}

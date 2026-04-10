package com.naturehood.naturehood_backend.dto.response;

import java.util.List;

/**
 * Paginated feed response.
 *
 * Returned by:
 *  GET /api/feed
 *  GET /api/posts/{postId}/comments
 *
 * {@code nextCursor} is null when there are no more pages.
 * {@code hasNext} mirrors whether nextCursor is non-null (convenience field for mobile).
 */
public class FeedResponse<T> {

    private List<T> data;

    /** Opaque Base64URL cursor to pass as ?cursor= on the next request. */
    private String nextCursor;

    private boolean hasNext;

    public FeedResponse() {}

    public FeedResponse(List<T> data, String nextCursor, boolean hasNext) {
        this.data = data;
        this.nextCursor = nextCursor;
        this.hasNext = hasNext;
    }

    public static <T> FeedResponse<T> of(List<T> data, String nextCursor) {
        return new FeedResponse<>(data, nextCursor, nextCursor != null);
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public List<T> getData() { return data; }
    public String getNextCursor() { return nextCursor; }
    public boolean isHasNext() { return hasNext; }

    // ─── Setters ──────────────────────────────────────────────────────────────

    public void setData(List<T> data) { this.data = data; }
    public void setNextCursor(String nextCursor) { this.nextCursor = nextCursor; }
    public void setHasNext(boolean hasNext) { this.hasNext = hasNext; }
}

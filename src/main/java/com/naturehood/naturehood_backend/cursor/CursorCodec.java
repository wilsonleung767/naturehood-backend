package com.naturehood.naturehood_backend.cursor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Encodes and decodes opaque pagination cursors for the feed.
 *
 * Cursor format (before Base64URL encoding):
 *   {@code <score>|<postId>}
 *
 * Example:
 *   raw:     "0.7234|64f1a3b2c5d6e7f8a9b0c1d2"
 *   encoded: "MC43MjM0fDY0ZjFhM2IyYzVkNmU3ZjhhOWIwYzFkMg"
 */
public final class CursorCodec {

    private static final String DELIMITER = "|";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private CursorCodec() {}

    /**
     * Encode a (score, postId) pair into an opaque cursor string.
     */
    public static String encode(double score, String postId) {
        String raw = score + DELIMITER + postId;
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decode a cursor string back into its components.
     *
     * @throws IllegalArgumentException if the cursor is malformed
     */
    public static CursorData decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            throw new IllegalArgumentException("Cursor must not be null or blank");
        }
        try {
            String raw = new String(DECODER.decode(cursor), StandardCharsets.UTF_8);
            int delimiterIdx = raw.indexOf(DELIMITER);
            if (delimiterIdx < 0) {
                throw new IllegalArgumentException("Malformed cursor: missing delimiter");
            }
            String scorePart = raw.substring(0, delimiterIdx);
            String postIdPart = raw.substring(delimiterIdx + 1);
            double score = Double.parseDouble(scorePart);
            return new CursorData(score, postIdPart);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cursor: " + e.getMessage(), e);
        }
    }

    /**
     * Value object carrying the decoded cursor components.
     */
    public record CursorData(double score, String postId) {}
}

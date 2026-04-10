package com.naturehood.naturehood_backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Generic API response envelope.
 *
 * All REST endpoints wrap their response in this object for consistent
 * mobile client parsing:
 *
 * Success: { "success": true,  "data": {...}, "error": null }
 * Failure: { "success": false, "data": null,  "error": "..." }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private String error;

    public ApiResponse() {}

    public ApiResponse(boolean success, T data, String error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message);
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public boolean isSuccess() { return success; }
    public T getData() { return data; }
    public String getError() { return error; }

    // ─── Setters ──────────────────────────────────────────────────────────────

    public void setSuccess(boolean success) { this.success = success; }
    public void setData(T data) { this.data = data; }
    public void setError(String error) { this.error = error; }
}

package org.fourz.RVNKLore.api.model.response;

import java.time.LocalDateTime;

/**
 * Generic status response for API operations.
 */
public class StatusResponse {
    private boolean success;
    private String message;
    private int statusCode;
    private LocalDateTime timestamp;

    private StatusResponse(boolean success, String message, int statusCode) {
        this.success = success;
        this.message = message;
        this.statusCode = statusCode;
        this.timestamp = LocalDateTime.now();
    }

    public static StatusResponse success(String message) {
        return new StatusResponse(true, message, 200);
    }

    public static StatusResponse error(String message, int statusCode) {
        return new StatusResponse(false, message, statusCode);
    }

    public static StatusResponse created(String message) {
        return new StatusResponse(true, message, 201);
    }

    // Getters
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public int getStatusCode() { return statusCode; }
    public LocalDateTime getTimestamp() { return timestamp; }
}

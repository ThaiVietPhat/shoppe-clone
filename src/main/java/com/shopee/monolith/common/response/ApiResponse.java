package com.shopee.monolith.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shopee.monolith.common.exception.ErrorCode;

/**
 * Standard API response wrapper implemented as a Java 21 record.
 * Uses @JsonInclude to exclude null fields from the response payload.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        int code,
        String message,
        T data
) {

    /**
     * Factory method for successful response with data payload.
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "Success", data);
    }

    /**
     * Factory method for successful response without data payload.
     */
    public static ApiResponse<Void> success() {
        return success(null);
    }

    /**
     * Factory method for business errors mapped from ErrorCode.
     */
    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.getHttpStatus(), errorCode.getMessage(), null);
    }

    /**
     * Factory method for unexpected or custom errors (e.g. validation failures).
     */
    public static ApiResponse<Void> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}

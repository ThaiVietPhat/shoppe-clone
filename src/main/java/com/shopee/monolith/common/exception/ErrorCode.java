package com.shopee.monolith.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Centralized error codes for the entire application.
 * Each module adds its own codes here — keeps error handling in one place.
 * Format: HTTP status + human-readable message.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ==================== Common ====================
    INTERNAL_SERVER_ERROR(500, "Internal server error"),
    INVALID_REQUEST(400, "Invalid request"),
    UNAUTHORIZED(401, "Authentication required"),
    FORBIDDEN(403, "Access denied"),
    NOT_FOUND(404, "Resource not found"),
    CONFLICT(409, "Resource already exists"),
    SERVICE_UNAVAILABLE(503, "Service temporarily unavailable"),

    // ==================== Auth ====================
    INVALID_CREDENTIALS(401, "Invalid email or password"),
    INVALID_TOKEN(401, "Token is invalid or expired"),
    TOKEN_REUSE_DETECTED(401, "Security violation detected — please log in again"),
    EMAIL_NOT_VERIFIED(403, "Please verify your email before logging in"),
    ACCOUNT_NOT_ACTIVE(403, "Account is not active"),
    VERIFICATION_TOKEN_EXPIRED(400, "Verification token has expired"),
    VERIFICATION_TOKEN_REUSED(400, "Verification token has already been used"),
    OAUTH_IDENTITY_ALREADY_LINKED(409, "OAuth identity is already linked to another user"),

    // ==================== User ====================
    USER_NOT_FOUND(404, "User not found"),
    EMAIL_ALREADY_EXISTS(409, "Email is already registered"),

    // ==================== Product ====================
    PRODUCT_NOT_FOUND(404, "Product not found"),
    VARIANT_NOT_FOUND(404, "Product variant not found"),

    // ==================== Inventory ====================
    INSUFFICIENT_STOCK(409, "Insufficient stock available"),

    // ==================== Order ====================
    ORDER_NOT_FOUND(404, "Order not found"),
    ORDER_CANNOT_BE_CANCELLED(409, "Order cannot be cancelled in its current state"),
    IDEMPOTENCY_KEY_MISSING(400, "Idempotency-Key header is required"),

    // ==================== Payment ====================
    PAYMENT_NOT_FOUND(404, "Payment not found"),
    INVALID_WEBHOOK_SIGNATURE(400, "Webhook signature verification failed"),

    // ==================== Voucher ====================
    VOUCHER_NOT_FOUND(404, "Voucher not found"),
    VOUCHER_EXPIRED(409, "Voucher has expired"),
    VOUCHER_USAGE_LIMIT_REACHED(409, "Voucher usage limit has been reached"),

    // ==================== Cart ====================
    CART_EMPTY(400, "Cart is empty"),

    // ==================== Media ====================
    INVALID_FILE_TYPE(400, "File type is not allowed"),
    FILE_TOO_LARGE(400, "File size exceeds the maximum allowed limit");

    private final int httpStatus;
    private final String message;
}

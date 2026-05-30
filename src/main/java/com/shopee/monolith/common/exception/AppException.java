package com.shopee.monolith.common.exception;

import lombok.Getter;

/**
 * Standard application-specific runtime exception carrying an ErrorCode.
 * Handled globally by GlobalExceptionHandler to return unified error API responses.
 */
@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}

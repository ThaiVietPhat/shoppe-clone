package com.shopee.monolith.common.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AppExceptionTest {

    @Test
    void shouldCreateAppExceptionWithErrorCode() {
        // Arrange
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

        // Act
        AppException exception = new AppException(errorCode);

        // Assert
        assertEquals(errorCode, exception.getErrorCode());
        assertEquals(errorCode.getMessage(), exception.getMessage());
    }
}

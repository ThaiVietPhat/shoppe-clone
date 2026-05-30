package com.shopee.monolith.common.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopee.monolith.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldNotSerializeNullData() throws Exception {
        // Arrange
        ApiResponse<Void> response = ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR);

        // Act
        String json = objectMapper.writeValueAsString(response);

        // Assert
        assertTrue(json.contains("\"code\":500"));
        assertTrue(json.contains("\"message\":\"Internal server error\""));
        assertFalse(json.contains("\"data\""));
    }

    @Test
    void shouldSerializeDataWhenNotNull() throws Exception {
        // Arrange
        ApiResponse<String> response = ApiResponse.success("Hello World");

        // Act
        String json = objectMapper.writeValueAsString(response);

        // Assert
        assertTrue(json.contains("\"code\":200"));
        assertTrue(json.contains("\"message\":\"Success\""));
        assertTrue(json.contains("\"data\":\"Hello World\""));
    }
}

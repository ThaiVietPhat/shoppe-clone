package com.shopee.monolith.common.response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopee.monolith.common.config.JacksonConfig;
import com.shopee.monolith.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = JacksonConfig.class)
class ApiResponseTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldNotSerializeNullDataAndSupportDeserialization() throws Exception {
        ApiResponse<Void> response = ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR);

        // Serialize
        String json = objectMapper.writeValueAsString(response);
        assertTrue(json.contains("\"code\":500"));
        assertTrue(json.contains("\"message\":\"Internal server error\""));
        assertFalse(json.contains("\"data\""));

        // Deserialize
        ApiResponse<Void> deserialized = objectMapper.readValue(json, new TypeReference<>() {});
        assertEquals(500, deserialized.code());
        assertEquals("Internal server error", deserialized.message());
        assertNull(deserialized.data());
    }

    @Test
    void shouldSerializeDataAndSupportDeserialization() throws Exception {
        ApiResponse<String> response = ApiResponse.success("Hello World");

        // Serialize
        String json = objectMapper.writeValueAsString(response);
        assertTrue(json.contains("\"code\":200"));
        assertTrue(json.contains("\"data\":\"Hello World\""));

        // Deserialize
        ApiResponse<String> deserialized = objectMapper.readValue(json, new TypeReference<>() {});
        assertEquals(200, deserialized.code());
        assertEquals("Hello World", deserialized.data());
    }
}

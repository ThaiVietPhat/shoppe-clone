package com.shopee.monolith.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = JacksonConfig.class)
class JacksonConfigTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldSerializeInstantAsIso8601() throws Exception {
        // Arrange
        Instant now = Instant.parse("2026-05-31T00:00:00Z");

        // Act
        String json = objectMapper.writeValueAsString(now);

        // Assert
        // The output should be a string (with quotes) formatted as ISO-8601
        assertTrue(json.startsWith("\""));
        assertTrue(json.endsWith("\""));
        
        // Regex to verify ISO-8601 format inside the quotes
        String iso8601Regex = "^\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z\"$";
        assertTrue(Pattern.matches(iso8601Regex, json), "JSON should match ISO-8601 format, but was: " + json);
    }
}

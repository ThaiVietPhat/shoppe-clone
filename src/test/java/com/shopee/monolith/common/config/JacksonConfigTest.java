package com.shopee.monolith.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = JacksonConfig.class)
class JacksonConfigTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldSerializeAndDeserializeInstantAsIso8601() throws Exception {
        Instant now = Instant.parse("2026-05-31T00:00:00Z");

        // Serialize
        String json = objectMapper.writeValueAsString(now);
        assertTrue(json.contains("\"2026-05-31T00:00:00Z\""), "Should serialize as ISO string, but was: " + json);

        // Deserialize
        Instant deserialized = objectMapper.readValue(json, Instant.class);
        assertEquals(now, deserialized);
    }

    @Test
    void shouldSerializeAndDeserializeLocalDateTimeAsIso8601() throws Exception {
        LocalDateTime now = LocalDateTime.parse("2026-05-31T10:15:30");

        // Serialize
        String json = objectMapper.writeValueAsString(now);
        // It shouldn't be an array like [2026, 5, 31, 10, 15, 30]
        assertTrue(json.contains("\"2026-05-31T10:15:30\""), "Should serialize as ISO string, but was: " + json);

        // Deserialize
        LocalDateTime deserialized = objectMapper.readValue(json, LocalDateTime.class);
        assertEquals(now, deserialized);
    }
}

package com.shopee.monolith.modules.auth.security;

import com.shopee.monolith.BasePostgresRedisIntegrationTest;
import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.config.AuthSecurityProperties;
import com.shopee.monolith.modules.auth.service.RateLimitService;
import com.shopee.monolith.modules.user.model.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import({RateLimitingFilterIT.TestRateLimitController.class, RateLimitingFilterIT.TestSecurityConfig.class})
@org.springframework.test.context.TestPropertySource(properties = "app.security.rate-limit.enabled=true")
class RateLimitingFilterIT extends BasePostgresRedisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoSpyBean
    private RateLimitService rateLimitService;

    @Autowired
    private AuthSecurityProperties properties;

    @BeforeEach
    void setUp() {
        cleanRedisRateLimitKeys();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        cleanRedisRateLimitKeys();
    }

    private void cleanRedisRateLimitKeys() {
        Set<String> keys = redisTemplate.keys("rate_limit:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void anonymousGenericRequestShouldBeRateLimited() throws Exception {
        // Limit is 100 per minute
        // Let's perform a fast loop to exceed the limit or mock the count,
        // but we can also just hit it repeatedly since Testcontainers Redis is running.
        // Wait, to keep tests fast, let's configure property capacity to 2 for this test
        int originalCapacity = properties.getRateLimit().getAnonymous().getCapacity();
        properties.getRateLimit().getAnonymous().setCapacity(2);

        try {
            // First request -> 200
            mockMvc.perform(get("/api/auth/test-rate-limit-public"))
                    .andExpect(status().isOk());

            // Second request -> 200
            mockMvc.perform(get("/api/auth/test-rate-limit-public"))
                    .andExpect(status().isOk());

            // Third request -> 429
            mockMvc.perform(get("/api/auth/test-rate-limit-public"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value(429))
                    .andExpect(jsonPath("$.message").value(ErrorCode.RATE_LIMIT_EXCEEDED.getMessage()));

            // Verify Redis key exists
            Set<String> keys = redisTemplate.keys("rate_limit:ip:*");
            assertFalse(keys.isEmpty());
        } finally {
            properties.getRateLimit().getAnonymous().setCapacity(originalCapacity);
        }
    }

    @Test
    void authenticatedRequestShouldUseUserBucket() throws Exception {
        int originalCapacity = properties.getRateLimit().getAuthenticated().getCapacity();
        properties.getRateLimit().getAuthenticated().setCapacity(2);

        try {
            UUID userId = UUID.randomUUID();
            String token = jwtTokenProvider.generateAccessToken(userId, Role.BUYER);

            // First request -> 200
            mockMvc.perform(get("/test-rate-limit-protected")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk());

            // Second request -> 200
            mockMvc.perform(get("/test-rate-limit-protected")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk());

            // Third request -> 429
            mockMvc.perform(get("/test-rate-limit-protected")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isTooManyRequests());

            // Verify User-specific Redis key exists instead of IP key
            Set<String> userKeys = redisTemplate.keys("rate_limit:user:" + userId);
            assertFalse(userKeys.isEmpty());

            Set<String> ipKeys = redisTemplate.keys("rate_limit:ip:*");
            assertTrue(ipKeys.isEmpty());
        } finally {
            properties.getRateLimit().getAuthenticated().setCapacity(originalCapacity);
        }
    }

    @Test
    void routeSpecificAbuseControlEndpointShouldUseAbuseBucket() throws Exception {
        // Test login capacity
        int originalCapacity = properties.getRateLimit().getLogin().getCapacity();
        properties.getRateLimit().getLogin().setCapacity(1);

        try {
            // First login request -> 400 (Bad Request - invalid credentials, but passed rate limit)
            mockMvc.perform(postWithCsrf("/api/auth/login")
                            .contentType("application/json")
                            .content("{\"email\":\"invalid@example.com\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());

            // Second login request -> 429
            mockMvc.perform(postWithCsrf("/api/auth/login")
                            .contentType("application/json")
                            .content("{\"email\":\"invalid@example.com\",\"password\":\"wrong\"}"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value(429));

            // Verify abuse Redis key exists
            Set<String> keys = redisTemplate.keys("rate_limit:auth:login:*");
            assertFalse(keys.isEmpty());
        } finally {
            properties.getRateLimit().getLogin().setCapacity(originalCapacity);
        }
    }

    @Test
    void redisDownOnProtectedRequestShouldFailClosed() throws Exception {
        String token = jwtTokenProvider.generateAccessToken(UUID.randomUUID(), Role.BUYER);

        doThrow(new AppException(ErrorCode.SERVICE_UNAVAILABLE))
                .when(rateLimitService).consume(any(), any());

        mockMvc.perform(get("/test-rate-limit-protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void redisDownOnAbuseEndpointShouldFailClosed() throws Exception {
        doThrow(new AppException(ErrorCode.SERVICE_UNAVAILABLE))
                .when(rateLimitService).consume(any(), any());

        mockMvc.perform(postWithCsrf("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"test@example.com\",\"password\":\"pass\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void redisDownOnLogoutShouldFailOpen() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenProvider.generateAccessToken(userId, Role.BUYER);

        doThrow(new AppException(ErrorCode.SERVICE_UNAVAILABLE))
                .when(rateLimitService).consume(any(), any());

        var req = postWithCsrf("/api/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        // It should NOT return 429 or 503 from rate limiter, but proceed to controller
        // Since the logout operation is idempotent, it proceeds successfully and returns 200.
        mockMvc.perform(req)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void jwtValidationShouldPrecedeRateLimiting() throws Exception {
        // If JWT is invalid/blacklisted, it should be rejected BEFORE consuming rate limiter token.
        // Let's spy/verify if rateLimitService.consume is never called when token is invalid
        mockMvc.perform(get("/test-rate-limit-protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());

        // Verify that rateLimitService was never called
        Mockito.verify(rateLimitService, Mockito.never()).consume(any(), any());
    }

    @Test
    void logoutShouldBypassRateLimitingEvenIfUserBucketIsExhausted() throws Exception {
        int originalCapacity = properties.getRateLimit().getAuthenticated().getCapacity();
        properties.getRateLimit().getAuthenticated().setCapacity(1);

        try {
            UUID userId = UUID.randomUUID();
            String token = jwtTokenProvider.generateAccessToken(userId, Role.BUYER);

            // First request using user bucket -> 200
            mockMvc.perform(get("/test-rate-limit-protected")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk());

            // Second request using user bucket -> 429 (exhausted!)
            mockMvc.perform(get("/test-rate-limit-protected")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isTooManyRequests());

            // Performing logout -> should still succeed with 200 (since it bypasses rate limiter entirely)
            mockMvc.perform(postWithCsrf("/api/auth/logout")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        } finally {
            properties.getRateLimit().getAuthenticated().setCapacity(originalCapacity);
        }
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postWithCsrf(String url) throws Exception {
        return post(url).with(csrf());
    }

    @RestController
    static class TestRateLimitController {
        @GetMapping("/api/auth/test-rate-limit-public")
        public String publicEndpoint() {
            return "public";
        }

        @GetMapping("/test-rate-limit-protected")
        public String protectedEndpoint() {
            return "protected";
        }
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class TestSecurityConfig {
        @org.springframework.context.annotation.Bean
        @org.springframework.core.annotation.Order(1)
        public org.springframework.security.web.SecurityFilterChain testPublicSecurityFilterChain(
                org.springframework.security.config.annotation.web.builders.HttpSecurity http) throws Exception {
            http
                    .securityMatcher("/api/auth/test-rate-limit-public")
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .csrf(csrf -> csrf.disable());
            return http.build();
        }
    }
}

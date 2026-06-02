package com.shopee.monolith.modules.auth.security;

import com.shopee.monolith.BasePostgresRedisIntegrationTest;
import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import com.shopee.monolith.modules.auth.service.AccessTokenBlacklistService;
import com.shopee.monolith.modules.user.model.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(SecurityIntegrationTest.TestSecurityController.class)
class SecurityIntegrationTest extends BasePostgresRedisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoSpyBean
    private AccessTokenBlacklistService blacklistService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publicEndpointsShouldBeAccessibleAnonymously() throws Exception {
        mockMvc.perform(get("/api/auth/test-public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("public"));

        int healthStatus = mockMvc.perform(get("/actuator/health"))
                .andReturn().getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertTrue(healthStatus == 200 || healthStatus == 503);
    }

    @Test
    void protectedEndpointsWhenAnonymousShouldReturn401Unauthorized() throws Exception {
        mockMvc.perform(get("/test-protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.getMessage()));
    }

    @Test
    void protectedEndpointsWhenValidTokenShouldReturn200() throws Exception {
        String token = jwtTokenProvider.generateAccessToken(UUID.randomUUID(), Role.BUYER);

        mockMvc.perform(get("/test-protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("protected"));
    }

    @Test
    void protectedEndpointsWhenInvalidTokenShouldReturn401InvalidToken() throws Exception {
        mockMvc.perform(get("/test-protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_TOKEN.getMessage()));
    }

    @Test
    void protectedEndpointsWhenBlacklistedTokenShouldReturn401InvalidToken() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenProvider.generateAccessToken(userId, Role.BUYER);
        AccessTokenClaims claims = jwtTokenProvider.parseAccessToken(token);

        // Blacklist it
        blacklistService.blacklist(claims);

        mockMvc.perform(get("/test-protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_TOKEN.getMessage()));
    }

    @Test
    void protectedEndpointsWhenRedisUnavailableShouldReturn503ServiceUnavailable() throws Exception {
        String token = jwtTokenProvider.generateAccessToken(UUID.randomUUID(), Role.BUYER);

        // Force a SERVICE_UNAVAILABLE from blacklist check
        doThrow(new AppException(ErrorCode.SERVICE_UNAVAILABLE))
                .when(blacklistService).isBlacklisted(anyString());

        mockMvc.perform(get("/test-protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value(ErrorCode.SERVICE_UNAVAILABLE.getMessage()));
    }

    @Test
    void methodSecurityWhenRoleMatchesShouldAllowAccess() throws Exception {
        String token = jwtTokenProvider.generateAccessToken(UUID.randomUUID(), Role.ADMIN);

        mockMvc.perform(get("/test-admin")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("admin"));
    }

    @Test
    void methodSecurityWhenRoleMismatchesShouldReturn403Forbidden() throws Exception {
        String token = jwtTokenProvider.generateAccessToken(UUID.randomUUID(), Role.BUYER);

        mockMvc.perform(get("/test-admin")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value(ErrorCode.FORBIDDEN.getMessage()));
    }

    @RestController
    static class TestSecurityController {
        @GetMapping("/api/auth/test-public")
        public String publicEndpoint() {
            return "public";
        }

        @GetMapping("/test-protected")
        public String protectedEndpoint() {
            return "protected";
        }

        @GetMapping("/test-admin")
        @PreAuthorize("hasRole('ADMIN')")
        public String adminEndpoint() {
            return "admin";
        }
    }
}

package com.shopee.monolith.modules.auth.security;

import com.shopee.monolith.BasePostgresRedisIntegrationTest;
import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import com.shopee.monolith.modules.auth.config.AuthSecurityProperties;
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

import com.shopee.monolith.modules.auth.entity.RefreshToken;
import com.shopee.monolith.modules.auth.repository.RefreshTokenRepository;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertFalse;

@AutoConfigureMockMvc
@Import({SecurityIntegrationTest.TestSecurityController.class, SecurityIntegrationTest.TestSecurityConfig.class})
class SecurityIntegrationTest extends BasePostgresRedisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoSpyBean
    private AccessTokenBlacklistService blacklistService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenGenerator refreshTokenGenerator;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private AuthSecurityProperties properties;

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

    @Test
    void logoutWhenRedisUnavailableShouldNotBeBlockedByBlacklistFilterButRevokeDBAndReturn503() throws Exception {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);

        UUID userId = txTemplate.execute(status -> {
            com.shopee.monolith.modules.user.entity.User user = com.shopee.monolith.modules.user.entity.User.builder()
                    .email("redis-down-logout." + UUID.randomUUID() + "@example.com")
                    .passwordHash("hash")
                    .build();
            entityManager.persist(user);
            entityManager.flush();
            return user.getId();
        });

        String rawRt = "redis-down-rt-1";
        String rtHash = refreshTokenGenerator.hash(rawRt);
        UUID familyId = UUID.randomUUID();

        txTemplate.executeWithoutResult(status -> {
            // Insert family and token
            entityManager.createNativeQuery(
                    "INSERT INTO refresh_token_families (id, user_id, created_at, updated_at) " +
                    "VALUES (:id, :userId, NOW(), NOW())")
                    .setParameter("id", familyId)
                    .setParameter("userId", userId)
                    .executeUpdate();

            RefreshToken token = RefreshToken.builder()
                    .userId(userId)
                    .tokenHash(rtHash)
                    .familyId(familyId)
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();
            entityManager.persist(token);
            entityManager.flush();
        });

        // Set up valid Bearer access token
        String token = jwtTokenProvider.generateAccessToken(userId, Role.BUYER);

        // Spy throw service unavailable when blacklisting JTI
        doThrow(new AppException(ErrorCode.SERVICE_UNAVAILABLE))
                .when(blacklistService).blacklist(any(AccessTokenClaims.class));

        // Get CSRF cookie to bypass CSRF filter
        org.springframework.test.web.servlet.MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        var csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

        // Request logout
        var req = post("/api/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .cookie(new jakarta.servlet.http.Cookie("__Secure-refresh_token", rawRt));

        if (csrfCookie != null) {
            req.cookie(csrfCookie).header("X-XSRF-TOKEN", csrfCookie.getValue());
        }

        mockMvc.perform(req)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value(ErrorCode.SERVICE_UNAVAILABLE.getMessage()));

        // Verify Postgres changes committed (tokens revoked)
        txTemplate.executeWithoutResult(status -> {
            java.util.List<RefreshToken> tokens = refreshTokenRepository.findAllByFamilyId(familyId);
            assertFalse(tokens.isEmpty());
            for (RefreshToken t : tokens) {
                org.junit.jupiter.api.Assertions.assertNotNull(t.getRevokedAt());
            }
        });

        // Clean up
        txTemplate.executeWithoutResult(status -> {
            entityManager.createQuery("delete from RefreshToken t where t.userId = :userId")
                    .setParameter("userId", userId)
                    .executeUpdate();
            entityManager.createQuery("delete from RefreshTokenFamily f where f.userId = :userId")
                    .setParameter("userId", userId)
                    .executeUpdate();
            entityManager.createQuery("delete from User u where u.id = :id")
                    .setParameter("id", userId)
                    .executeUpdate();
        });
    }

    @Test
    void logoutAllWhenBlacklistedTokenShouldReturn401InvalidToken() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenProvider.generateAccessToken(userId, Role.BUYER);
        AccessTokenClaims claims = jwtTokenProvider.parseAccessToken(token);

        // Blacklist it
        blacklistService.blacklist(claims);

        // Get CSRF token
        org.springframework.test.web.servlet.MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        var csrfCookie = csrfResult.getResponse().getCookie(properties.getCsrf().getCookieName());

        var req = post("/api/auth/logout-all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (csrfCookie != null) {
            req.cookie(csrfCookie).header(properties.getCsrf().getHeaderName(), csrfCookie.getValue());
        }

        mockMvc.perform(req)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_TOKEN.getMessage()));
    }

    @Test
    void logoutAllWhenRedisUnavailableShouldReturn503ServiceUnavailable() throws Exception {
        String token = jwtTokenProvider.generateAccessToken(UUID.randomUUID(), Role.BUYER);

        // Force a SERVICE_UNAVAILABLE from blacklist check
        doThrow(new AppException(ErrorCode.SERVICE_UNAVAILABLE))
                .when(blacklistService).isBlacklisted(anyString());

        // Get CSRF token
        org.springframework.test.web.servlet.MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        var csrfCookie = csrfResult.getResponse().getCookie(properties.getCsrf().getCookieName());

        var req = post("/api/auth/logout-all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (csrfCookie != null) {
            req.cookie(csrfCookie).header(properties.getCsrf().getHeaderName(), csrfCookie.getValue());
        }

        mockMvc.perform(req)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value(ErrorCode.SERVICE_UNAVAILABLE.getMessage()));
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

    @org.springframework.boot.test.context.TestConfiguration
    static class TestSecurityConfig {
        @org.springframework.context.annotation.Bean
        @org.springframework.core.annotation.Order(1)
        public org.springframework.security.web.SecurityFilterChain testPublicSecurityFilterChain(
                org.springframework.security.config.annotation.web.builders.HttpSecurity http) throws Exception {
            http
                    .securityMatcher("/api/auth/test-public")
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .csrf(csrf -> csrf.disable());
            return http.build();
        }
    }
}

package com.shopee.monolith.modules.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.config.AuthSecurityProperties;
import com.shopee.monolith.modules.auth.config.JwtProperties;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import com.shopee.monolith.modules.auth.dto.internal.IssuedTokenPair;
import com.shopee.monolith.modules.auth.dto.request.LoginRequest;
import com.shopee.monolith.modules.auth.service.AuthService;
import com.shopee.monolith.modules.auth.service.RefreshTokenService;
import com.shopee.monolith.modules.auth.service.SessionRevocationService;
import com.shopee.monolith.modules.user.model.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        com.shopee.monolith.modules.auth.config.SecurityConfig.class,
                        com.shopee.monolith.modules.auth.security.JwtAuthenticationFilter.class,
                        com.shopee.monolith.modules.auth.security.BlacklistFilter.class,
                        com.shopee.monolith.modules.auth.security.RestAuthenticationEntryPoint.class,
                        com.shopee.monolith.modules.auth.security.RestAccessDeniedHandler.class
                }
        ),
        excludeAutoConfiguration = SecurityAutoConfiguration.class
)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private SessionRevocationService revocationService;

    @MockitoBean
    private AuthSecurityProperties properties;

    @MockitoBean
    private JwtProperties jwtProperties;

    private AuthSecurityProperties.AuthCookieProperties cookieProperties;

    @BeforeEach
    void setUp() {
        cookieProperties = new AuthSecurityProperties.AuthCookieProperties();
        cookieProperties.setName("__Secure-refresh_token");
        cookieProperties.setSecure(true);
        cookieProperties.setHttpOnly(true);
        cookieProperties.setSameSite("Lax");
        cookieProperties.setPath("/api/auth");

        when(properties.getAuthCookie()).thenReturn(cookieProperties);
        when(jwtProperties.getRefreshExpiration()).thenReturn(Duration.ofDays(7));
    }

    @Test
    void csrfShouldReturnSuccess() throws Exception {
        CsrfToken csrfToken = mock(CsrfToken.class);
        when(csrfToken.getToken()).thenReturn("token-val");

        mockMvc.perform(get("/api/auth/csrf")
                        .requestAttr(CsrfToken.class.getName(), csrfToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void loginWhenCredentialsValidShouldReturnAccessTokenAndSetCookie() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        IssuedTokenPair tokenPair = new IssuedTokenPair("access-token-123", "refresh-token-456");

        when(authService.login(any(LoginRequest.class))).thenReturn(tokenPair);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("access-token-123"))
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(cookie().value("__Secure-refresh_token", "refresh-token-456"))
                .andExpect(cookie().httpOnly("__Secure-refresh_token", true))
                .andExpect(cookie().secure("__Secure-refresh_token", true))
                .andExpect(cookie().path("__Secure-refresh_token", "/api/auth"));
    }

    @Test
    void refreshWhenCookiePresentShouldRotateAndReturnNewAccessToken() throws Exception {
        IssuedTokenPair tokenPair = new IssuedTokenPair("new-access-token-123", "new-refresh-token-456");
        when(refreshTokenService.rotate("refresh-token-456")).thenReturn(tokenPair);

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("__Secure-refresh_token", "refresh-token-456")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token-123"))
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(cookie().value("__Secure-refresh_token", "new-refresh-token-456"));
    }

    @Test
    void refreshWhenCookieMissingShouldReturnError() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_TOKEN.getMessage()));
    }

    @Test
    void logoutWhenCookiePresentShouldRevokeAndClearCookie() throws Exception {
        UUID userId = UUID.randomUUID();
        AccessTokenClaims claims = AccessTokenClaims.builder()
                .userId(userId)
                .role(Role.BUYER)
                .jti("jti-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        var auth = new UsernamePasswordAuthenticationToken(claims, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        doNothing().when(revocationService).logout(eq("refresh-token-456"), any(AccessTokenClaims.class));

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("__Secure-refresh_token", "refresh-token-456")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(cookie().maxAge("__Secure-refresh_token", 0))
                .andExpect(cookie().value("__Secure-refresh_token", ""));

        verify(revocationService).logout("refresh-token-456", claims);
        SecurityContextHolder.clearContext();
    }

    @Test
    void logoutWhenCookieMissingButAuthenticatedShouldBlacklistAccessToken() throws Exception {
        UUID userId = UUID.randomUUID();
        AccessTokenClaims claims = AccessTokenClaims.builder()
                .userId(userId)
                .role(Role.BUYER)
                .jti("jti-456")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        var auth = new UsernamePasswordAuthenticationToken(claims, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        doNothing().when(revocationService).logout(eq(null), any(AccessTokenClaims.class));

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(cookie().maxAge("__Secure-refresh_token", 0))
                .andExpect(cookie().value("__Secure-refresh_token", ""));

        verify(revocationService).logout(null, claims);
        SecurityContextHolder.clearContext();
    }

    @Test
    void logoutWhenCookieAndAuthenticationMissingShouldNotThrowAndClearCookie() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(null);

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(cookie().maxAge("__Secure-refresh_token", 0))
                .andExpect(cookie().value("__Secure-refresh_token", ""));
    }

    @Test
    void logoutAllWhenAuthenticatedShouldRevokeAllAndClearCookie() throws Exception {
        UUID userId = UUID.randomUUID();
        AccessTokenClaims claims = AccessTokenClaims.builder()
                .userId(userId)
                .role(Role.BUYER)
                .jti("jti-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        var auth = new UsernamePasswordAuthenticationToken(claims, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        doNothing().when(revocationService).logoutAll(userId);

        mockMvc.perform(post("/api/auth/logout-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(cookie().maxAge("__Secure-refresh_token", 0))
                .andExpect(cookie().value("__Secure-refresh_token", ""));

        verify(revocationService).logoutAll(userId);
        SecurityContextHolder.clearContext();
    }

    @Test
    void logoutAllWhenAnonymousShouldReturn401() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(null);

        mockMvc.perform(post("/api/auth/logout-all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}

package com.shopee.monolith.modules.auth.security;

import com.shopee.monolith.modules.auth.config.AuthSecurityProperties;
import com.shopee.monolith.modules.auth.config.JwtProperties;
import com.shopee.monolith.modules.auth.dto.internal.IssuedTokenPair;
import com.shopee.monolith.modules.auth.service.RefreshTokenService;
import com.shopee.monolith.modules.user.model.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2HandlersTest {

    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthSecurityProperties authSecurityProperties;
    private JwtProperties jwtProperties;

    private OAuth2AuthenticationSuccessHandler successHandler;
    private OAuth2AuthenticationFailureHandler failureHandler;

    @BeforeEach
    void setUp() {
        authSecurityProperties = new AuthSecurityProperties();
        authSecurityProperties.getCors().setAllowedOrigins(List.of("http://localhost:3000"));
        authSecurityProperties.getAuthCookie().setName("__Secure-refresh_token");
        authSecurityProperties.getAuthCookie().setHttpOnly(true);
        authSecurityProperties.getAuthCookie().setSecure(true);
        authSecurityProperties.getAuthCookie().setSameSite("Lax");
        authSecurityProperties.getAuthCookie().setPath("/api/auth");

        jwtProperties = new JwtProperties();
        jwtProperties.setRefreshExpiration(Duration.ofDays(7));

        successHandler = new OAuth2AuthenticationSuccessHandler(refreshTokenService, authSecurityProperties, jwtProperties);
        failureHandler = new OAuth2AuthenticationFailureHandler(authSecurityProperties);
    }

    @Test
    void successHandlerShouldIssueTokensSetCookieAndRedirectToAllowedOrigin() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.encodeRedirectURL(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        Authentication authentication = mock(Authentication.class);
        CustomOAuth2User customOAuth2User = mock(CustomOAuth2User.class);

        UUID userId = UUID.randomUUID();
        when(customOAuth2User.getUserId()).thenReturn(userId);
        when(customOAuth2User.getRole()).thenReturn("BUYER");
        when(authentication.getPrincipal()).thenReturn(customOAuth2User);

        IssuedTokenPair tokenPair = IssuedTokenPair.builder()
                .accessToken("mockAccessToken")
                .refreshToken("mockRefreshToken")
                .build();
        when(refreshTokenService.issueTokenPair(eq(userId), eq(Role.BUYER))).thenReturn(tokenPair);

        successHandler.onAuthenticationSuccess(request, response, authentication);

        // Verify Set-Cookie header added
        ArgumentCaptor<String> cookieCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq(HttpHeaders.SET_COOKIE), cookieCaptor.capture());
        String cookieVal = cookieCaptor.getValue();
        assertTrue(cookieVal.contains("__Secure-refresh_token=mockRefreshToken"));
        assertTrue(cookieVal.contains("Path=/api/auth"));
        assertTrue(cookieVal.contains("HttpOnly"));
        assertTrue(cookieVal.contains("Secure"));
        assertTrue(cookieVal.contains("SameSite=Lax"));

        // Verify redirection
        verify(response).sendRedirect("http://localhost:3000/oauth2/redirect?token=mockAccessToken");
    }

    @Test
    void failureHandlerShouldRedirectToAllowedOriginWithErrorMessage() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.encodeRedirectURL(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        AuthenticationException exception = mock(AuthenticationException.class);

        when(exception.getMessage()).thenReturn("Authentication failed due to duplicated email");

        failureHandler.onAuthenticationFailure(request, response, exception);

        String expectedRedirectUrl = "http://localhost:3000/login?error="
                + URLEncoder.encode("Authentication failed due to duplicated email", StandardCharsets.UTF_8);
        verify(response).sendRedirect(expectedRedirectUrl);
    }
}

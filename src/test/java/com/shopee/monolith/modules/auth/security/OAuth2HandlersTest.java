package com.shopee.monolith.modules.auth.security;

import com.shopee.monolith.modules.auth.config.AuthSecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2HandlersTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private AuthSecurityProperties authSecurityProperties;

    private OAuth2AuthenticationSuccessHandler successHandler;
    private OAuth2AuthenticationFailureHandler failureHandler;

    @BeforeEach
    void setUp() {
        authSecurityProperties = new AuthSecurityProperties();
        authSecurityProperties.getCors().setAllowedOrigins(List.of("http://localhost:3000"));

        successHandler = new OAuth2AuthenticationSuccessHandler(stringRedisTemplate, authSecurityProperties);
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

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        successHandler.onAuthenticationSuccess(request, response, authentication);

        // Verify Redis write
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), valCaptor.capture(), eq(Duration.ofSeconds(60)));

        String key = keyCaptor.getValue();
        String val = valCaptor.getValue();
        assertTrue(key.startsWith("oauth2:code:"));
        assertEquals(userId.toString() + ":BUYER", val);

        // Verify redirection containing code
        String code = key.substring("oauth2:code:".length());
        verify(response).sendRedirect("http://localhost:3000/oauth2/redirect?code=" + code);
    }

    @Test
    void failureHandlerShouldRedirectToAllowedOriginWithEmailAlreadyExistsCode() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.encodeRedirectURL(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        org.springframework.security.oauth2.core.OAuth2AuthenticationException exception =
                new org.springframework.security.oauth2.core.OAuth2AuthenticationException(
                        new org.springframework.security.oauth2.core.OAuth2Error("email_already_exists"), "Email already exists");

        failureHandler.onAuthenticationFailure(request, response, exception);

        String expectedRedirectUrl = "http://localhost:3000/login?error="
                + URLEncoder.encode("email_already_exists", StandardCharsets.UTF_8);
        verify(response).sendRedirect(expectedRedirectUrl);
    }

    @Test
    void failureHandlerShouldRedirectToAllowedOriginWithAccountLockedCode() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.encodeRedirectURL(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        org.springframework.security.oauth2.core.OAuth2AuthenticationException exception =
                new org.springframework.security.oauth2.core.OAuth2AuthenticationException(
                        new org.springframework.security.oauth2.core.OAuth2Error("account_locked"), "Account is locked");

        failureHandler.onAuthenticationFailure(request, response, exception);

        String expectedRedirectUrl = "http://localhost:3000/login?error="
                + URLEncoder.encode("account_locked", StandardCharsets.UTF_8);
        verify(response).sendRedirect(expectedRedirectUrl);
    }

    @Test
    void failureHandlerShouldRedirectToAllowedOriginWithAccountInactiveCode() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.encodeRedirectURL(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        org.springframework.security.oauth2.core.OAuth2AuthenticationException exception =
                new org.springframework.security.oauth2.core.OAuth2AuthenticationException(
                        new org.springframework.security.oauth2.core.OAuth2Error("account_inactive"), "Account is inactive");

        failureHandler.onAuthenticationFailure(request, response, exception);

        String expectedRedirectUrl = "http://localhost:3000/login?error="
                + URLEncoder.encode("account_inactive", StandardCharsets.UTF_8);
        verify(response).sendRedirect(expectedRedirectUrl);
    }

    @Test
    void failureHandlerShouldRedirectToAllowedOriginWithDefaultOauthFailedCode() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.encodeRedirectURL(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        AuthenticationException exception = mock(AuthenticationException.class);

        failureHandler.onAuthenticationFailure(request, response, exception);

        String expectedRedirectUrl = "http://localhost:3000/login?error="
                + URLEncoder.encode("oauth_failed", StandardCharsets.UTF_8);
        verify(response).sendRedirect(expectedRedirectUrl);
    }

    @Test
    void successHandlerWithRedisFailureShouldRedirectToAllowedOriginWithServiceUnavailableError() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.encodeRedirectURL(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        Authentication authentication = mock(Authentication.class);
        CustomOAuth2User customOAuth2User = mock(CustomOAuth2User.class);

        UUID userId = UUID.randomUUID();
        when(customOAuth2User.getUserId()).thenReturn(userId);
        when(customOAuth2User.getRole()).thenReturn("BUYER");
        when(authentication.getPrincipal()).thenReturn(customOAuth2User);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        org.mockito.Mockito.doThrow(new org.springframework.data.redis.RedisSystemException("Connection failed", new RuntimeException()))
                .when(valueOperations).set(anyString(), anyString(), eq(Duration.ofSeconds(60)));

        successHandler.onAuthenticationSuccess(request, response, authentication);

        String expectedRedirectUrl = "http://localhost:3000/login?error="
                + URLEncoder.encode("service_unavailable", StandardCharsets.UTF_8);
        verify(response).sendRedirect(expectedRedirectUrl);
    }
}

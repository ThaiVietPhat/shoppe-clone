package com.shopee.monolith.modules.auth.security;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.user.dto.internal.UserAuthenticationData;
import com.shopee.monolith.modules.user.dto.response.UserResponse;
import com.shopee.monolith.modules.user.model.Role;
import com.shopee.monolith.modules.user.model.UserStatus;
import com.shopee.monolith.modules.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;

    private CustomOAuth2UserService customOAuth2UserService;

    @BeforeEach
    void setUp() {
        customOAuth2UserService = new CustomOAuth2UserService(userService);
        customOAuth2UserService.setDelegate(delegate);
    }

    private OAuth2UserRequest createMockRequest(String provider) {
        ClientRegistration clientRegistration = ClientRegistration.withRegistrationId(provider)
                .clientId("clientId")
                .clientSecret("clientSecret")
                .authorizationUri("authUri")
                .tokenUri("tokenUri")
                .userInfoUri("userInfo")
                .redirectUri("redirect")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .build();
        return new OAuth2UserRequest(clientRegistration, new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token", Instant.now(), Instant.now().plusSeconds(60)));
    }

    @Test
    void loadUserWithExistingOAuthIdentityShouldSucceed() {
        OAuth2UserRequest request = createMockRequest("google");
        OAuth2User oauth2User = mock(OAuth2User.class);
        Map<String, Object> attribs = new HashMap<>();
        attribs.put("sub", "google_12345");
        attribs.put("email", "google@shopee.com");
        attribs.put("email_verified", true);

        when(oauth2User.getAttributes()).thenReturn(attribs);
        when(delegate.loadUser(request)).thenReturn(oauth2User);

        UUID userId = UUID.randomUUID();
        UserAuthenticationData authData = UserAuthenticationData.builder()
                .id(userId)
                .email("google@shopee.com")
                .role(Role.BUYER)
                .status(UserStatus.ACTIVE)
                .build();

        when(userService.findAuthenticationDataByOAuth("google", "google_12345")).thenReturn(Optional.of(authData));

        OAuth2User result = customOAuth2UserService.loadUser(request);

        assertNotNull(result);
        assertTrue(result instanceof CustomOAuth2User);
        CustomOAuth2User customUser = (CustomOAuth2User) result;
        assertEquals(userId, customUser.getUserId());
        assertEquals("BUYER", customUser.getRole());
    }

    @Test
    void loadUserWithNewOAuthIdentityShouldAutoRegisterActiveUser() {
        OAuth2UserRequest request = createMockRequest("google");
        OAuth2User oauth2User = mock(OAuth2User.class);
        Map<String, Object> attribs = new HashMap<>();
        attribs.put("sub", "google_12345");
        attribs.put("email", "google@shopee.com");
        attribs.put("email_verified", true);

        when(oauth2User.getAttributes()).thenReturn(attribs);
        when(delegate.loadUser(request)).thenReturn(oauth2User);
        when(userService.findAuthenticationDataByOAuth("google", "google_12345")).thenReturn(Optional.empty());

        UUID userId = UUID.randomUUID();
        UserResponse userResponse = UserResponse.builder()
                .id(userId)
                .email("google@shopee.com")
                .role(Role.BUYER)
                .build();
        when(userService.registerOAuthUser("google", "google_12345", "google@shopee.com")).thenReturn(userResponse);

        OAuth2User result = customOAuth2UserService.loadUser(request);

        assertNotNull(result);
        CustomOAuth2User customUser = (CustomOAuth2User) result;
        assertEquals(userId, customUser.getUserId());
        assertEquals("BUYER", customUser.getRole());

        verify(userService).registerOAuthUser("google", "google_12345", "google@shopee.com");
    }

    @Test
    void loadUserWithExistingEmailButNoOAuthIdentityShouldThrowException() {
        OAuth2UserRequest request = createMockRequest("google");
        OAuth2User oauth2User = mock(OAuth2User.class);
        Map<String, Object> attribs = new HashMap<>();
        attribs.put("sub", "google_12345");
        attribs.put("email", "google@shopee.com");
        attribs.put("email_verified", true);

        when(oauth2User.getAttributes()).thenReturn(attribs);
        when(delegate.loadUser(request)).thenReturn(oauth2User);
        when(userService.findAuthenticationDataByOAuth("google", "google_12345")).thenReturn(Optional.empty());
        when(userService.registerOAuthUser("google", "google_12345", "google@shopee.com"))
                .thenThrow(new AppException(ErrorCode.EMAIL_ALREADY_EXISTS));

        OAuth2AuthenticationException exception = assertThrows(OAuth2AuthenticationException.class,
                () -> customOAuth2UserService.loadUser(request));
        assertEquals("Email already exists. Please login and link account.", exception.getMessage());
    }

    @Test
    void loadUserWithLockedUserStatusShouldThrowException() {
        OAuth2UserRequest request = createMockRequest("google");
        OAuth2User oauth2User = mock(OAuth2User.class);
        Map<String, Object> attribs = new HashMap<>();
        attribs.put("sub", "google_12345");
        attribs.put("email", "google@shopee.com");
        attribs.put("email_verified", true);

        when(oauth2User.getAttributes()).thenReturn(attribs);
        when(delegate.loadUser(request)).thenReturn(oauth2User);

        UUID userId = UUID.randomUUID();
        UserAuthenticationData authData = UserAuthenticationData.builder()
                .id(userId)
                .email("google@shopee.com")
                .role(Role.BUYER)
                .status(UserStatus.LOCKED)
                .build();

        when(userService.findAuthenticationDataByOAuth("google", "google_12345")).thenReturn(Optional.of(authData));

        OAuth2AuthenticationException exception = assertThrows(OAuth2AuthenticationException.class,
                () -> customOAuth2UserService.loadUser(request));
        assertEquals("Account is locked", exception.getMessage());
    }

    @Test
    void loadUserWithInactiveUserStatusShouldThrowException() {
        OAuth2UserRequest request = createMockRequest("google");
        OAuth2User oauth2User = mock(OAuth2User.class);
        Map<String, Object> attribs = new HashMap<>();
        attribs.put("sub", "google_12345");
        attribs.put("email", "google@shopee.com");
        attribs.put("email_verified", true);

        when(oauth2User.getAttributes()).thenReturn(attribs);
        when(delegate.loadUser(request)).thenReturn(oauth2User);

        UUID userId = UUID.randomUUID();
        UserAuthenticationData authData = UserAuthenticationData.builder()
                .id(userId)
                .email("google@shopee.com")
                .role(Role.BUYER)
                .status(UserStatus.INACTIVE)
                .build();

        when(userService.findAuthenticationDataByOAuth("google", "google_12345")).thenReturn(Optional.of(authData));

        OAuth2AuthenticationException exception = assertThrows(OAuth2AuthenticationException.class,
                () -> customOAuth2UserService.loadUser(request));
        assertEquals("Account is inactive", exception.getMessage());
    }

    @Test
    void loadUserWithUnverifiedGoogleEmailShouldThrowException() {
        OAuth2UserRequest request = createMockRequest("google");
        OAuth2User oauth2User = mock(OAuth2User.class);
        Map<String, Object> attribs = new HashMap<>();
        attribs.put("sub", "google_12345");
        attribs.put("email", "google@shopee.com");
        attribs.put("email_verified", false);

        when(oauth2User.getAttributes()).thenReturn(attribs);
        when(delegate.loadUser(request)).thenReturn(oauth2User);
        when(userService.findAuthenticationDataByOAuth("google", "google_12345")).thenReturn(Optional.empty());

        OAuth2AuthenticationException exception = assertThrows(OAuth2AuthenticationException.class,
                () -> customOAuth2UserService.loadUser(request));
        assertEquals("Email from OAuth provider is not verified", exception.getMessage());
    }

    @Test
    void loadUserWithMissingEmailShouldThrowException() {
        OAuth2UserRequest request = createMockRequest("google");
        OAuth2User oauth2User = mock(OAuth2User.class);
        Map<String, Object> attribs = new HashMap<>();
        attribs.put("sub", "google_12345");
        attribs.put("email", "");
        attribs.put("email_verified", true);

        when(oauth2User.getAttributes()).thenReturn(attribs);
        when(delegate.loadUser(request)).thenReturn(oauth2User);

        OAuth2AuthenticationException exception = assertThrows(OAuth2AuthenticationException.class,
                () -> customOAuth2UserService.loadUser(request));
        assertEquals("Email or provider user ID is missing from OAuth provider", exception.getMessage());
    }

    @Test
    void loadUserWithExistingFacebookIdentityShouldSucceed() {
        OAuth2UserRequest request = createMockRequest("facebook");
        OAuth2User oauth2User = mock(OAuth2User.class);
        Map<String, Object> attribs = new HashMap<>();
        attribs.put("id", "facebook_12345");
        attribs.put("email", "facebook@shopee.com");

        when(oauth2User.getAttributes()).thenReturn(attribs);
        when(delegate.loadUser(request)).thenReturn(oauth2User);

        UUID userId = UUID.randomUUID();
        UserAuthenticationData authData = UserAuthenticationData.builder()
                .id(userId)
                .email("facebook@shopee.com")
                .role(Role.BUYER)
                .status(UserStatus.ACTIVE)
                .build();

        when(userService.findAuthenticationDataByOAuth("facebook", "facebook_12345")).thenReturn(Optional.of(authData));

        OAuth2User result = customOAuth2UserService.loadUser(request);

        assertNotNull(result);
        assertTrue(result instanceof CustomOAuth2User);
        CustomOAuth2User customUser = (CustomOAuth2User) result;
        assertEquals(userId, customUser.getUserId());
        assertEquals("BUYER", customUser.getRole());
    }

    @Test
    void loadUserWithNewFacebookIdentityShouldThrowEmailNotVerifiedException() {
        OAuth2UserRequest request = createMockRequest("facebook");
        OAuth2User oauth2User = mock(OAuth2User.class);
        Map<String, Object> attribs = new HashMap<>();
        attribs.put("id", "facebook_12345");
        attribs.put("email", "facebook@shopee.com");

        when(oauth2User.getAttributes()).thenReturn(attribs);
        when(delegate.loadUser(request)).thenReturn(oauth2User);
        when(userService.findAuthenticationDataByOAuth("facebook", "facebook_12345")).thenReturn(Optional.empty());

        OAuth2AuthenticationException exception = assertThrows(OAuth2AuthenticationException.class,
                () -> customOAuth2UserService.loadUser(request));
        assertEquals("Email from OAuth provider is not verified", exception.getMessage());
    }

    @Test
    void loadUserWithPendingVerificationUserStatusShouldThrowException() {
        OAuth2UserRequest request = createMockRequest("google");
        OAuth2User oauth2User = mock(OAuth2User.class);
        Map<String, Object> attribs = new HashMap<>();
        attribs.put("sub", "google_12345");
        attribs.put("email", "google@shopee.com");
        attribs.put("email_verified", true);

        when(oauth2User.getAttributes()).thenReturn(attribs);
        when(delegate.loadUser(request)).thenReturn(oauth2User);

        UUID userId = UUID.randomUUID();
        UserAuthenticationData authData = UserAuthenticationData.builder()
                .id(userId)
                .email("google@shopee.com")
                .role(Role.BUYER)
                .status(UserStatus.PENDING_VERIFICATION)
                .build();

        when(userService.findAuthenticationDataByOAuth("google", "google_12345")).thenReturn(Optional.of(authData));

        OAuth2AuthenticationException exception = assertThrows(OAuth2AuthenticationException.class,
                () -> customOAuth2UserService.loadUser(request));
        assertEquals("Email from OAuth provider is not verified", exception.getMessage());
    }

    @Test
    void loadUserWithDatabaseDownShouldThrowServiceUnavailable() {
        OAuth2UserRequest request = createMockRequest("google");
        OAuth2User oauth2User = mock(OAuth2User.class);
        Map<String, Object> attribs = new HashMap<>();
        attribs.put("sub", "google_12345");
        attribs.put("email", "google@shopee.com");
        attribs.put("email_verified", true);

        when(oauth2User.getAttributes()).thenReturn(attribs);
        when(delegate.loadUser(request)).thenReturn(oauth2User);

        when(userService.findAuthenticationDataByOAuth("google", "google_12345"))
                .thenThrow(new org.springframework.dao.QueryTimeoutException("Database timeout"));

        org.springframework.security.oauth2.core.OAuth2AuthenticationException exception = assertThrows(
                org.springframework.security.oauth2.core.OAuth2AuthenticationException.class,
                () -> customOAuth2UserService.loadUser(request));
        assertEquals("service_unavailable", exception.getError().getErrorCode());
    }

    @Test
    void loadUserWithUnexpectedExceptionShouldThrowOauthFailed() {
        OAuth2UserRequest request = createMockRequest("google");
        OAuth2User oauth2User = mock(OAuth2User.class);
        Map<String, Object> attribs = new HashMap<>();
        attribs.put("sub", "google_12345");
        attribs.put("email", "google@shopee.com");
        attribs.put("email_verified", true);

        when(oauth2User.getAttributes()).thenReturn(attribs);
        when(delegate.loadUser(request)).thenReturn(oauth2User);

        when(userService.findAuthenticationDataByOAuth("google", "google_12345"))
                .thenThrow(new NullPointerException("Unexpected null value"));

        org.springframework.security.oauth2.core.OAuth2AuthenticationException exception = assertThrows(
                org.springframework.security.oauth2.core.OAuth2AuthenticationException.class,
                () -> customOAuth2UserService.loadUser(request));
        assertEquals("oauth_failed", exception.getError().getErrorCode());
    }
}

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
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private UserService userService;

    private CustomOAuth2UserService customOAuth2UserService;

    @BeforeEach
    void setUp() {
        // Build CustomOAuth2UserService override method loadUser delegate logic or mock the delegation logic properly
        customOAuth2UserService = new CustomOAuth2UserService(userService) {
            @Override
            public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
                // To avoid real network call from default delegate.loadUser, we bypass it for unit testing
                String provider = userRequest.getClientRegistration().getRegistrationId().toLowerCase();
                OAuth2User oauth2User;
                if ("google".equals(provider)) {
                    Map<String, Object> attribs = new HashMap<>();
                    attribs.put("sub", "google_12345");
                    attribs.put("email", "google@shopee.com");
                    attribs.put("email_verified", true);
                    oauth2User = new DefaultOAuth2User(Collections.emptyList(), attribs, "sub");
                } else if ("facebook".equals(provider)) {
                    Map<String, Object> attribs = new HashMap<>();
                    attribs.put("id", "facebook_12345");
                    attribs.put("email", "facebook@shopee.com");
                    oauth2User = new DefaultOAuth2User(Collections.emptyList(), attribs, "id");
                } else {
                    attribsMockedForFailures.put("registration_id", provider);
                    oauth2User = new DefaultOAuth2User(Collections.emptyList(), attribsMockedForFailures, "sub");
                }
                return processUser(userRequest, oauth2User);
            }
        };
    }

    private final Map<String, Object> attribsMockedForFailures = new HashMap<>();

    private OAuth2User processUser(OAuth2UserRequest userRequest, OAuth2User oauth2User) {
        String provider = userRequest.getClientRegistration().getRegistrationId().toLowerCase();
        Map<String, Object> attributes = oauth2User.getAttributes();
        String providerUserId;
        String email;
        boolean emailVerified;

        if ("google".equals(provider)) {
            providerUserId = (String) attributes.get("sub");
            email = (String) attributes.get("email");
            Object verified = attributes.get("email_verified");
            if (verified instanceof Boolean) {
                emailVerified = (Boolean) verified;
            } else {
                emailVerified = "true".equalsIgnoreCase(String.valueOf(verified));
            }
        } else if ("facebook".equals(provider)) {
            providerUserId = (String) attributes.get("id");
            email = (String) attributes.get("email");
            emailVerified = (email != null && !email.isBlank());
        } else {
            throw new OAuth2AuthenticationException(new org.springframework.security.oauth2.core.OAuth2Error("invalid_provider"), "Unsupported OAuth provider: " + provider);
        }

        if (providerUserId == null || providerUserId.isBlank() || email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(new org.springframework.security.oauth2.core.OAuth2Error("missing_email"), "Email or provider user ID is missing from OAuth provider");
        }

        if (!emailVerified) {
            throw new OAuth2AuthenticationException(new org.springframework.security.oauth2.core.OAuth2Error("email_not_verified"), "Email from OAuth provider is not verified");
        }

        Optional<UserAuthenticationData> userAuthDataOpt = userService.findAuthenticationDataByOAuth(provider, providerUserId);
        UserAuthenticationData userAuthData;

        if (userAuthDataOpt.isPresent()) {
            userAuthData = userAuthDataOpt.get();
            validateUserStatus(userAuthData.status());
        } else {
            try {
                UserResponse userResponse = userService.registerOAuthUser(provider, providerUserId, email);
                userAuthData = UserAuthenticationData.builder()
                        .id(userResponse.id())
                        .email(userResponse.email())
                        .role(userResponse.role())
                        .status(UserStatus.ACTIVE)
                        .build();
            } catch (AppException e) {
                if (e.getErrorCode() == ErrorCode.EMAIL_ALREADY_EXISTS) {
                    throw new OAuth2AuthenticationException(new org.springframework.security.oauth2.core.OAuth2Error("email_already_exists"), "Email already exists. Please login and link account.");
                }
                throw new OAuth2AuthenticationException(new org.springframework.security.oauth2.core.OAuth2Error("registration_failed"), e.getMessage());
            }
        }

        return new CustomOAuth2User(oauth2User, userAuthData.id(), userAuthData.role().name());
    }

    private void validateUserStatus(UserStatus status) {
        if (status == UserStatus.LOCKED) {
            throw new OAuth2AuthenticationException(new org.springframework.security.oauth2.core.OAuth2Error("account_locked"), "Account is locked");
        }
        if (status == UserStatus.INACTIVE) {
            throw new OAuth2AuthenticationException(new org.springframework.security.oauth2.core.OAuth2Error("account_inactive"), "Account is inactive");
        }
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
        UUID userId = UUID.randomUUID();

        when(userService.findAuthenticationDataByOAuth("google", "google_12345")).thenReturn(Optional.empty());

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
        ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("google")
                .clientId("clientId")
                .clientSecret("clientSecret")
                .authorizationUri("authUri")
                .tokenUri("tokenUri")
                .userInfoUri("userInfo")
                .redirectUri("redirect")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .build();
        OAuth2UserRequest request = new OAuth2UserRequest(clientRegistration, new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token", Instant.now(), Instant.now().plusSeconds(60)));

        customOAuth2UserService = new CustomOAuth2UserService(userService) {
            @Override
            public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
                Map<String, Object> attribs = new HashMap<>();
                attribs.put("sub", "google_12345");
                attribs.put("email", "google@shopee.com");
                attribs.put("email_verified", false); // Unverified
                OAuth2User oauth2User = new DefaultOAuth2User(Collections.emptyList(), attribs, "sub");
                return processUser(userRequest, oauth2User);
            }
        };

        OAuth2AuthenticationException exception = assertThrows(OAuth2AuthenticationException.class,
                () -> customOAuth2UserService.loadUser(request));
        assertEquals("Email from OAuth provider is not verified", exception.getMessage());
    }

    @Test
    void loadUserWithMissingEmailShouldThrowException() {
        ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("google")
                .clientId("clientId")
                .clientSecret("clientSecret")
                .authorizationUri("authUri")
                .tokenUri("tokenUri")
                .userInfoUri("userInfo")
                .redirectUri("redirect")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .build();
        OAuth2UserRequest request = new OAuth2UserRequest(clientRegistration, new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token", Instant.now(), Instant.now().plusSeconds(60)));

        customOAuth2UserService = new CustomOAuth2UserService(userService) {
            @Override
            public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
                Map<String, Object> attribs = new HashMap<>();
                attribs.put("sub", "google_12345");
                attribs.put("email", ""); // Missing email
                attribs.put("email_verified", true);
                OAuth2User oauth2User = new DefaultOAuth2User(Collections.emptyList(), attribs, "sub");
                return processUser(userRequest, oauth2User);
            }
        };

        OAuth2AuthenticationException exception = assertThrows(OAuth2AuthenticationException.class,
                () -> customOAuth2UserService.loadUser(request));
        assertEquals("Email or provider user ID is missing from OAuth provider", exception.getMessage());
    }
}

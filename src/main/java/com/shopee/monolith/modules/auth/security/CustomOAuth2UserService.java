package com.shopee.monolith.modules.auth.security;

import com.shopee.monolith.modules.user.dto.internal.UserAuthenticationData;
import com.shopee.monolith.modules.user.dto.response.UserResponse;
import com.shopee.monolith.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserService userService;
    private OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();

    void setDelegate(OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate) {
        this.delegate = delegate;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = delegate.loadUser(userRequest);
        String provider = userRequest.getClientRegistration().getRegistrationId().toLowerCase();

        Map<String, Object> attributes = oauth2User.getAttributes();
        String providerUserId;
        String email;
        boolean emailVerified;

        if ("google".equals(provider)) {
            providerUserId = (String) attributes.get("sub");
            email = (String) attributes.get("email");
            emailVerified = isGoogleEmailVerified(attributes);
        } else if ("facebook".equals(provider)) {
            providerUserId = (String) attributes.get("id");
            email = (String) attributes.get("email");
            // Facebook does not provide a trusted email_verified flag.
            // We set emailVerified to false to block auto-registration, requiring prior account linking.
            emailVerified = false;
        } else {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_provider"), "Unsupported OAuth provider: " + provider);
        }

        if (providerUserId == null || providerUserId.isBlank() || email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("missing_email"), "Email or provider user ID is missing from OAuth provider");
        }

        // Load existing user or register new OAuth-only active user
        Optional<UserAuthenticationData> userAuthDataOpt;
        try {
            userAuthDataOpt = userService.findAuthenticationDataByOAuth(provider, providerUserId);
        } catch (com.shopee.monolith.common.exception.AppException e) {
            if (e.getErrorCode() == com.shopee.monolith.common.exception.ErrorCode.SERVICE_UNAVAILABLE) {
                throw new OAuth2AuthenticationException(new OAuth2Error("service_unavailable"), e.getMessage());
            }
            throw new OAuth2AuthenticationException(new OAuth2Error("oauth_failed"), e.getMessage());
        } catch (org.springframework.dao.DataAccessException e) {
            throw new OAuth2AuthenticationException(new OAuth2Error("service_unavailable"), "Database service unavailable");
        } catch (Exception e) {
            throw new OAuth2AuthenticationException(new OAuth2Error("oauth_failed"), "Authentication failed unexpected");
        }

        UserAuthenticationData userAuthData;

        if (userAuthDataOpt.isPresent()) {
            userAuthData = userAuthDataOpt.get();
            validateUserStatus(userAuthData.status());
        } else {
            if (!emailVerified) {
                throw new OAuth2AuthenticationException(new OAuth2Error("email_not_verified"), "Email from OAuth provider is not verified");
            }
            try {
                UserResponse userResponse = userService.registerOAuthUser(provider, providerUserId, email);
                userAuthData = UserAuthenticationData.builder()
                        .id(userResponse.id())
                        .email(userResponse.email())
                        .role(userResponse.role())
                        .status(com.shopee.monolith.modules.user.model.UserStatus.ACTIVE)
                        .build();
            } catch (com.shopee.monolith.common.exception.AppException e) {
                if (e.getErrorCode() == com.shopee.monolith.common.exception.ErrorCode.EMAIL_ALREADY_EXISTS) {
                    throw new OAuth2AuthenticationException(new OAuth2Error("email_already_exists"), "Email already exists. Please login and link account.");
                }
                if (e.getErrorCode() == com.shopee.monolith.common.exception.ErrorCode.SERVICE_UNAVAILABLE) {
                    throw new OAuth2AuthenticationException(new OAuth2Error("service_unavailable"), e.getMessage());
                }
                throw new OAuth2AuthenticationException(new OAuth2Error("registration_failed"), e.getMessage());
            } catch (org.springframework.dao.DataAccessException e) {
                throw new OAuth2AuthenticationException(new OAuth2Error("service_unavailable"), "Infrastructure service unavailable");
            } catch (Exception e) {
                throw new OAuth2AuthenticationException(new OAuth2Error("oauth_failed"), "Authentication failed unexpected");
            }
        }

        return new CustomOAuth2User(
                oauth2User,
                userAuthData.id(),
                userAuthData.role().name()
        );
    }

    private boolean isGoogleEmailVerified(Map<String, Object> attributes) {
        Object verified = attributes.get("email_verified");
        if (verified instanceof Boolean) {
            return (Boolean) verified;
        }
        if (verified instanceof String) {
            return "true".equalsIgnoreCase((String) verified);
        }
        return false;
    }

    private void validateUserStatus(com.shopee.monolith.modules.user.model.UserStatus status) {
        if (status == com.shopee.monolith.modules.user.model.UserStatus.PENDING_VERIFICATION) {
            throw new OAuth2AuthenticationException(new OAuth2Error("email_not_verified"), "Email from OAuth provider is not verified");
        }
        if (status == com.shopee.monolith.modules.user.model.UserStatus.LOCKED) {
            throw new OAuth2AuthenticationException(new OAuth2Error("account_locked"), "Account is locked");
        }
        if (status == com.shopee.monolith.modules.user.model.UserStatus.INACTIVE) {
            throw new OAuth2AuthenticationException(new OAuth2Error("account_inactive"), "Account is inactive");
        }
    }
}

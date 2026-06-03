package com.shopee.monolith.modules.auth.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.config.AuthSecurityProperties;
import com.shopee.monolith.modules.auth.dto.internal.IssuedTokenPair;
import com.shopee.monolith.modules.auth.dto.request.LoginRequest;
import com.shopee.monolith.modules.auth.dto.request.RegisterRequest;
import com.shopee.monolith.common.security.EventPayloadCryptoService;
import com.shopee.monolith.modules.auth.dto.request.VerifyRequest;
import com.shopee.monolith.modules.auth.security.VerificationTokenGenerator;
import com.shopee.monolith.modules.user.dto.command.RegisterUserCommand;
import com.shopee.monolith.modules.user.dto.internal.UserAuthenticationData;
import com.shopee.monolith.modules.user.dto.response.UserResponse;
import com.shopee.monolith.modules.user.entity.User;
import com.shopee.monolith.modules.user.entity.VerificationToken;
import com.shopee.monolith.modules.user.event.UserRegisteredEvent;
import com.shopee.monolith.modules.user.model.UserStatus;
import com.shopee.monolith.modules.user.repository.UserRepository;
import com.shopee.monolith.modules.user.repository.VerificationTokenRepository;
import com.shopee.monolith.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final VerificationTokenRepository verificationTokenRepository;
    private final VerificationTokenGenerator verificationTokenGenerator;
    private final EventPayloadCryptoService eventPayloadCryptoService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuthSecurityProperties securityProperties;
    private final Clock clock;
    private final StringRedisTemplate stringRedisTemplate;

    // Dummy BCrypt hash of cost 12 to run timing-equivalent check for non-existent users
    static final String DUMMY_HASH = "$2a$12$6yGZ/X4sF.FhPUp1p.2KFeZpG.0u4hW1.c.4zY5P6q7r8s9t0u1v2";

    @Override
    public IssuedTokenPair login(LoginRequest request) {
        Optional<UserAuthenticationData> userAuthDataOpt = userService.findAuthenticationDataByEmail(request.email());

        String passwordHash = userAuthDataOpt.map(UserAuthenticationData::passwordHash).orElse(null);
        boolean userExists = userAuthDataOpt.isPresent() && passwordHash != null;

        String hashToMatch = userExists ? passwordHash : DUMMY_HASH;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToMatch);

        if (!userExists || !passwordMatches) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        UserAuthenticationData userAuthData = userAuthDataOpt.get();
        validateUserStatus(userAuthData.status());

        return refreshTokenService.issueTokenPair(userAuthData.id(), userAuthData.role());
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String hashedPassword = passwordEncoder.encode(request.password());
        UserResponse userResponse = userService.registerUser(new RegisterUserCommand(request.email(), hashedPassword));

        // Generate opaque token and save its hash
        String rawToken = verificationTokenGenerator.generate();
        String tokenHash = verificationTokenGenerator.hash(rawToken);

        Instant expiresAt = Instant.now(clock).plus(securityProperties.getVerificationToken().getTtl());

        VerificationToken verificationToken = VerificationToken.builder()
                .userId(userResponse.id())
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .build();

        verificationTokenRepository.save(verificationToken);

        // Encrypt the raw token for transmission via event (safe for Modulith event log)
        String encryptedToken = eventPayloadCryptoService.encrypt(rawToken);

        // Publish event with the encrypted token
        eventPublisher.publishEvent(new UserRegisteredEvent(userResponse.id(), request.email(), encryptedToken));

        return userResponse;
    }

    @Override
    @Transactional
    public void verify(VerifyRequest request) {
        Instant now = Instant.now(clock);
        String tokenHash = verificationTokenGenerator.hash(request.token());

        // Lock verification token row
        VerificationToken verificationToken = verificationTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_TOKEN));

        if (verificationToken.isConsumed()) {
            throw new AppException(ErrorCode.VERIFICATION_TOKEN_REUSED);
        }

        if (verificationToken.isExpired(now)) {
            throw new AppException(ErrorCode.VERIFICATION_TOKEN_EXPIRED);
        }

        // Lock user row
        User user = userRepository.findByIdForUpdate(verificationToken.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.LOCKED || user.getStatus() == UserStatus.INACTIVE) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        // Activate user and consume token
        user.activate();
        verificationToken.consume(now);

        userRepository.save(user);
        verificationTokenRepository.save(verificationToken);
    }

    private void validateUserStatus(UserStatus status) {
        if (status == UserStatus.PENDING_VERIFICATION) {
            throw new AppException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (status != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }
    }

    @Override
    public IssuedTokenPair exchangeOAuth2Code(String code) {
        String key = "oauth2:code:" + code;
        String value;
        try {
            value = stringRedisTemplate.opsForValue().getAndDelete(key);
        } catch (Exception e) {
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        if (value == null) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        int colonIdx = value.indexOf(':');
        if (colonIdx == -1) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
        String userIdStr = value.substring(0, colonIdx);

        UUID userId;
        try {
            userId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
        UserAuthenticationData userAuthData = userService.findAuthenticationDataById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        validateUserStatus(userAuthData.status());

        return refreshTokenService.issueTokenPair(userId, userAuthData.role());
    }
}

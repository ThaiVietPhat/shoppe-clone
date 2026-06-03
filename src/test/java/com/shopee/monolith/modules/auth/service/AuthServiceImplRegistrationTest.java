package com.shopee.monolith.modules.auth.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.common.security.EventPayloadCryptoService;
import com.shopee.monolith.modules.auth.config.AuthSecurityProperties;
import com.shopee.monolith.modules.auth.dto.request.RegisterRequest;
import com.shopee.monolith.modules.auth.dto.request.VerifyRequest;
import com.shopee.monolith.modules.auth.security.VerificationTokenGenerator;
import com.shopee.monolith.modules.user.dto.command.RegisterUserCommand;
import com.shopee.monolith.modules.user.dto.response.UserResponse;
import com.shopee.monolith.modules.user.entity.User;
import com.shopee.monolith.modules.user.entity.VerificationToken;
import com.shopee.monolith.modules.user.event.UserRegisteredEvent;
import com.shopee.monolith.modules.user.model.Role;
import com.shopee.monolith.modules.user.model.UserStatus;
import com.shopee.monolith.modules.user.repository.UserRepository;
import com.shopee.monolith.modules.user.repository.VerificationTokenRepository;
import com.shopee.monolith.modules.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplRegistrationTest {

    @Mock
    private UserService userService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    @Mock
    private VerificationTokenGenerator verificationTokenGenerator;

    @Mock
    private EventPayloadCryptoService eventPayloadCryptoService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AuthSecurityProperties securityProperties;

    @Mock
    private Clock clock;

    private AuthServiceImpl authService;

    private final Instant fixedInstant = Instant.parse("2026-06-03T12:00:00Z");

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(fixedInstant);
        authService = new AuthServiceImpl(
                userService,
                passwordEncoder,
                refreshTokenService,
                verificationTokenRepository,
                verificationTokenGenerator,
                eventPayloadCryptoService,
                userRepository,
                eventPublisher,
                securityProperties,
                clock
        );
    }

    @Test
    void registerShouldHashPasswordCreateUserGenerateTokenAndPublishEvent() {
        String email = "test@example.com";
        String password = "mySecurePassword";
        String hashedPassword = "hashed_mySecurePassword";
        UUID userId = UUID.randomUUID();
        String rawToken = "rawOpaqueTokenString";
        String tokenHash = "sha256HashedTokenHex";
        String encryptedToken = "encryptedOpaqueTokenString";

        RegisterRequest request = new RegisterRequest(email, password);

        when(passwordEncoder.encode(password)).thenReturn(hashedPassword);
        
        UserResponse mockUserResponse = UserResponse.builder()
                .id(userId)
                .email(email)
                .role(Role.BUYER)
                .build();
        when(userService.registerUser(any(RegisterUserCommand.class))).thenReturn(mockUserResponse);
        
        when(verificationTokenGenerator.generate()).thenReturn(rawToken);
        when(verificationTokenGenerator.hash(rawToken)).thenReturn(tokenHash);
        when(eventPayloadCryptoService.encrypt(rawToken)).thenReturn(encryptedToken);

        AuthSecurityProperties.VerificationTokenProperties tokenProps = new AuthSecurityProperties.VerificationTokenProperties();
        tokenProps.setTtl(Duration.ofHours(24));
        when(securityProperties.getVerificationToken()).thenReturn(tokenProps);

        UserResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals(userId, response.id());
        assertEquals(email, response.email());

        // Verify token saved in repository
        ArgumentCaptor<VerificationToken> tokenCaptor = ArgumentCaptor.forClass(VerificationToken.class);
        verify(verificationTokenRepository).save(tokenCaptor.capture());
        VerificationToken savedToken = tokenCaptor.getValue();
        assertEquals(userId, savedToken.getUserId());
        assertEquals(tokenHash, savedToken.getTokenHash());
        assertEquals(fixedInstant.plus(Duration.ofHours(24)), savedToken.getExpiresAt());

        // Verify event published
        ArgumentCaptor<UserRegisteredEvent> eventCaptor = ArgumentCaptor.forClass(UserRegisteredEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        UserRegisteredEvent publishedEvent = eventCaptor.getValue();
        assertEquals(userId, publishedEvent.userId());
        assertEquals(email, publishedEvent.email());
        assertEquals(encryptedToken, publishedEvent.encryptedVerificationToken());
    }

    @Test
    void verifySuccessShouldActivateUserAndConsumeToken() {
        String rawToken = "rawOpaqueTokenString";
        String tokenHash = "sha256HashedTokenHex";
        UUID userId = UUID.randomUUID();

        VerifyRequest request = new VerifyRequest(rawToken);

        when(verificationTokenGenerator.hash(rawToken)).thenReturn(tokenHash);

        VerificationToken verificationToken = VerificationToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .expiresAt(fixedInstant.plus(Duration.ofHours(24)))
                .build();
        when(verificationTokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(verificationToken));

        User user = User.builder()
                .id(userId)
                .email("test@example.com")
                .role(Role.BUYER)
                .status(UserStatus.PENDING_VERIFICATION)
                .build();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        authService.verify(request);

        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertTrue(verificationToken.isConsumed());
        assertEquals(fixedInstant, verificationToken.getConsumedAt());

        verify(userRepository).save(user);
        verify(verificationTokenRepository).save(verificationToken);
    }

    @Test
    void verifyWithExpiredTokenShouldThrowException() {
        String rawToken = "expiredTokenString";
        String tokenHash = "sha256ExpiredTokenHash";
        UUID userId = UUID.randomUUID();

        VerifyRequest request = new VerifyRequest(rawToken);

        when(verificationTokenGenerator.hash(rawToken)).thenReturn(tokenHash);

        // Created 48 hours ago with TTL 24 hours -> expired
        VerificationToken verificationToken = VerificationToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .expiresAt(fixedInstant.minus(Duration.ofHours(24)))
                .build();
        when(verificationTokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(verificationToken));

        AppException ex = assertThrows(AppException.class, () -> authService.verify(request));
        assertEquals(ErrorCode.VERIFICATION_TOKEN_EXPIRED, ex.getErrorCode());
    }

    @Test
    void verifyWithReusedTokenShouldThrowException() {
        String rawToken = "reusedTokenString";
        String tokenHash = "sha256ReusedTokenHash";
        UUID userId = UUID.randomUUID();

        VerifyRequest request = new VerifyRequest(rawToken);

        when(verificationTokenGenerator.hash(rawToken)).thenReturn(tokenHash);

        VerificationToken verificationToken = VerificationToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .expiresAt(fixedInstant.plus(Duration.ofHours(24)))
                .consumedAt(fixedInstant.minus(Duration.ofHours(1)))
                .build();
        when(verificationTokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(verificationToken));

        AppException ex = assertThrows(AppException.class, () -> authService.verify(request));
        assertEquals(ErrorCode.VERIFICATION_TOKEN_REUSED, ex.getErrorCode());
    }

    @Test
    void verifyWithInvalidTokenShouldThrowException() {
        String rawToken = "invalidTokenString";
        String tokenHash = "sha256InvalidTokenHash";

        VerifyRequest request = new VerifyRequest(rawToken);

        when(verificationTokenGenerator.hash(rawToken)).thenReturn(tokenHash);
        when(verificationTokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> authService.verify(request));
        assertEquals(ErrorCode.INVALID_TOKEN, ex.getErrorCode());
    }
}

package com.shopee.monolith.modules.user.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.user.dto.command.CreateVerificationTokenCommand;
import com.shopee.monolith.modules.user.entity.User;
import com.shopee.monolith.modules.user.entity.VerificationToken;
import com.shopee.monolith.modules.user.model.Role;
import com.shopee.monolith.modules.user.model.UserStatus;
import com.shopee.monolith.modules.user.repository.UserRepository;
import com.shopee.monolith.modules.user.repository.VerificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserVerificationServiceImplTest {

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    @Mock
    private UserRepository userRepository;

    private UserVerificationServiceImpl userVerificationService;

    private final Instant now = Instant.parse("2026-06-03T12:00:00Z");

    @BeforeEach
    void setUp() {
        userVerificationService = new UserVerificationServiceImpl(verificationTokenRepository, userRepository);
    }

    @Test
    void createVerificationTokenWhenCommandValidShouldPersistTokenOwnedByUserModule() {
        UUID userId = UUID.randomUUID();
        Instant expiresAt = now.plus(Duration.ofHours(24));

        userVerificationService.createVerificationToken(new CreateVerificationTokenCommand(userId, "hash", expiresAt));

        ArgumentCaptor<VerificationToken> tokenCaptor = ArgumentCaptor.forClass(VerificationToken.class);
        verify(verificationTokenRepository).save(tokenCaptor.capture());
        VerificationToken savedToken = tokenCaptor.getValue();
        assertEquals(userId, savedToken.getUserId());
        assertEquals("hash", savedToken.getTokenHash());
        assertEquals(expiresAt, savedToken.getExpiresAt());
    }

    @Test
    void verifyTokenHashWhenValidShouldActivateUserAndConsumeToken() {
        UUID userId = UUID.randomUUID();
        VerificationToken verificationToken = VerificationToken.builder()
                .userId(userId)
                .tokenHash("hash")
                .expiresAt(now.plus(Duration.ofHours(24)))
                .build();
        User user = User.builder()
                .id(userId)
                .email("test@example.com")
                .role(Role.BUYER)
                .status(UserStatus.PENDING_VERIFICATION)
                .build();

        when(verificationTokenRepository.findByTokenHashForUpdate("hash")).thenReturn(Optional.of(verificationToken));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        userVerificationService.verifyTokenHash("hash", now);

        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertTrue(verificationToken.isConsumed());
        assertEquals(now, verificationToken.getConsumedAt());
        verify(userRepository).save(user);
        verify(verificationTokenRepository).save(verificationToken);
    }

    @Test
    void verifyTokenHashWhenExpiredShouldThrowExpired() {
        VerificationToken verificationToken = VerificationToken.builder()
                .userId(UUID.randomUUID())
                .tokenHash("hash")
                .expiresAt(now.minus(Duration.ofSeconds(1)))
                .build();
        when(verificationTokenRepository.findByTokenHashForUpdate("hash")).thenReturn(Optional.of(verificationToken));

        AppException exception = assertThrows(AppException.class,
                () -> userVerificationService.verifyTokenHash("hash", now));

        assertEquals(ErrorCode.VERIFICATION_TOKEN_EXPIRED, exception.getErrorCode());
    }

    @Test
    void verifyTokenHashWhenReusedShouldThrowReused() {
        VerificationToken verificationToken = VerificationToken.builder()
                .userId(UUID.randomUUID())
                .tokenHash("hash")
                .expiresAt(now.plus(Duration.ofHours(24)))
                .consumedAt(now.minus(Duration.ofHours(1)))
                .build();
        when(verificationTokenRepository.findByTokenHashForUpdate("hash")).thenReturn(Optional.of(verificationToken));

        AppException exception = assertThrows(AppException.class,
                () -> userVerificationService.verifyTokenHash("hash", now));

        assertEquals(ErrorCode.VERIFICATION_TOKEN_REUSED, exception.getErrorCode());
    }

    @Test
    void verifyTokenHashWhenMissingShouldThrowInvalidToken() {
        when(verificationTokenRepository.findByTokenHashForUpdate("hash")).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> userVerificationService.verifyTokenHash("hash", now));

        assertEquals(ErrorCode.INVALID_TOKEN, exception.getErrorCode());
    }
}

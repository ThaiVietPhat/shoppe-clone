package com.shopee.monolith.modules.auth.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.config.JwtProperties;
import com.shopee.monolith.modules.auth.dto.internal.IssuedTokenPair;
import com.shopee.monolith.modules.auth.entity.RefreshToken;
import com.shopee.monolith.modules.auth.repository.RefreshTokenRepository;
import com.shopee.monolith.modules.auth.security.JwtTokenProvider;
import com.shopee.monolith.modules.auth.security.RefreshTokenGenerator;
import com.shopee.monolith.modules.user.dto.internal.UserAuthenticationData;
import com.shopee.monolith.modules.user.model.Role;
import com.shopee.monolith.modules.user.model.UserStatus;
import com.shopee.monolith.modules.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenGenerator refreshTokenGenerator;

    @Mock
    private UserService userService;

    private JwtProperties jwtProperties;
    private Clock clock;
    private RefreshTokenService refreshTokenService;

    private final Instant fixedInstant = Instant.parse("2026-06-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("super-secret-key-that-is-at-least-64-bytes-long-for-testing-all-algorithms");
        jwtProperties.setExpiration(Duration.ofMinutes(10));
        jwtProperties.setRefreshExpiration(Duration.ofDays(7));

        clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

        RefreshTokenRotationWorker worker = new RefreshTokenRotationWorker(
                refreshTokenRepository,
                refreshTokenGenerator,
                jwtProperties,
                userService,
                jwtTokenProvider,
                clock
        );

        refreshTokenService = new RefreshTokenService(
                refreshTokenRepository,
                jwtTokenProvider,
                refreshTokenGenerator,
                jwtProperties,
                worker,
                clock
        );
    }

    @Test
    void issueTokenPairShouldPersistHashedTokenAndReturnPair() {
        UUID userId = UUID.randomUUID();
        String mockAccessToken = "mockAccessToken";
        String mockRawRefreshToken = "mockRawRefreshToken";
        String mockHashedRefreshToken = "mockHashedRefreshToken";

        when(jwtTokenProvider.generateAccessToken(userId, Role.BUYER)).thenReturn(mockAccessToken);
        when(refreshTokenGenerator.generate()).thenReturn(mockRawRefreshToken);
        when(refreshTokenGenerator.hash(mockRawRefreshToken)).thenReturn(mockHashedRefreshToken);

        IssuedTokenPair tokenPair = refreshTokenService.issueTokenPair(userId, Role.BUYER);

        assertNotNull(tokenPair);
        assertEquals(mockAccessToken, tokenPair.accessToken());
        assertEquals(mockRawRefreshToken, tokenPair.refreshToken());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken persistedToken = captor.getValue();
        assertEquals(userId, persistedToken.getUserId());
        assertEquals(mockHashedRefreshToken, persistedToken.getTokenHash());
        assertNotNull(persistedToken.getFamilyId());
        assertEquals(fixedInstant.plus(Duration.ofDays(7)), persistedToken.getExpiresAt());
    }

    @Test
    void rotateWhenTokenValidShouldRevokeOldTokenAndReturnNewPair() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        String rawToken = "rawToken123";
        String tokenHash = "tokenHash123";
        String rawNewToken = "rawNewToken456";
        String newHash = "newHash456";
        String mockAccessToken = "newAccessToken";

        RefreshToken oldToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .expiresAt(fixedInstant.plus(Duration.ofMinutes(5)))
                .build();

        when(refreshTokenGenerator.hash(rawToken)).thenReturn(tokenHash);
        when(refreshTokenRepository.findFamilyIdByTokenHash(tokenHash)).thenReturn(Optional.of(familyId));
        when(refreshTokenRepository.findAllByFamilyIdForUpdate(familyId)).thenReturn(Collections.singletonList(oldToken));
        when(refreshTokenGenerator.generate()).thenReturn(rawNewToken);
        when(refreshTokenGenerator.hash(rawNewToken)).thenReturn(newHash);
        when(userService.findAuthenticationDataById(userId)).thenReturn(Optional.of(
                UserAuthenticationData.builder()
                        .id(userId)
                        .email("user@test.com")
                        .role(Role.BUYER)
                        .status(UserStatus.ACTIVE)
                        .build()
        ));
        when(jwtTokenProvider.generateAccessToken(userId, Role.BUYER)).thenReturn(mockAccessToken);

        IssuedTokenPair result = refreshTokenService.rotate(rawToken);

        assertNotNull(result);
        assertEquals(mockAccessToken, result.accessToken());
        assertEquals(rawNewToken, result.refreshToken());

        // Verify old token is revoked with pointer to new token
        assertEquals(fixedInstant, oldToken.getRevokedAt());
        assertEquals(newHash, oldToken.getReplacedByTokenHash());
        verify(refreshTokenRepository).save(oldToken);

        // Verify new token is persisted with correct fields
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());

        RefreshToken savedNewToken = captor.getAllValues().get(1);
        assertEquals(userId, savedNewToken.getUserId());
        assertEquals(newHash, savedNewToken.getTokenHash());
        assertEquals(familyId, savedNewToken.getFamilyId());
        assertEquals(fixedInstant.plus(Duration.ofDays(7)), savedNewToken.getExpiresAt());
        assertNull(savedNewToken.getRevokedAt());
    }

    @Test
    void rotateWhenTokenNotFoundShouldThrowInvalidToken() {
        String rawToken = "rawToken123";
        String tokenHash = "tokenHash123";

        when(refreshTokenGenerator.hash(rawToken)).thenReturn(tokenHash);
        when(refreshTokenRepository.findFamilyIdByTokenHash(tokenHash)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> refreshTokenService.rotate(rawToken));
        assertEquals(ErrorCode.INVALID_TOKEN, ex.getErrorCode());

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotateWhenTokenExpiredShouldThrowInvalidToken() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        String rawToken = "rawToken123";
        String tokenHash = "tokenHash123";

        RefreshToken expiredToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .expiresAt(fixedInstant.minus(Duration.ofSeconds(1))) // expired
                .build();

        when(refreshTokenGenerator.hash(rawToken)).thenReturn(tokenHash);
        when(refreshTokenRepository.findFamilyIdByTokenHash(tokenHash)).thenReturn(Optional.of(familyId));
        when(refreshTokenRepository.findAllByFamilyIdForUpdate(familyId)).thenReturn(Collections.singletonList(expiredToken));

        AppException ex = assertThrows(AppException.class, () -> refreshTokenService.rotate(rawToken));
        assertEquals(ErrorCode.INVALID_TOKEN, ex.getErrorCode());

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotateWhenTokenExpiredExactlyAtNowShouldThrowInvalidToken() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        String rawToken = "rawToken123";
        String tokenHash = "tokenHash123";

        RefreshToken expiredToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .expiresAt(fixedInstant) // expired exactly now
                .build();

        when(refreshTokenGenerator.hash(rawToken)).thenReturn(tokenHash);
        when(refreshTokenRepository.findFamilyIdByTokenHash(tokenHash)).thenReturn(Optional.of(familyId));
        when(refreshTokenRepository.findAllByFamilyIdForUpdate(familyId)).thenReturn(Collections.singletonList(expiredToken));

        AppException ex = assertThrows(AppException.class, () -> refreshTokenService.rotate(rawToken));
        assertEquals(ErrorCode.INVALID_TOKEN, ex.getErrorCode());

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotateWhenTokenAlreadyRevokedShouldRevokeActiveFamilyAndThrowReuseDetected() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        String rawToken = "rawToken123";
        String tokenHash = "tokenHash123";

        RefreshToken revokedToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .expiresAt(fixedInstant.plus(Duration.ofMinutes(5)))
                .revokedAt(fixedInstant.minus(Duration.ofMinutes(2)))
                .replacedByTokenHash("anotherHash")
                .build();

        when(refreshTokenGenerator.hash(rawToken)).thenReturn(tokenHash);
        when(refreshTokenRepository.findFamilyIdByTokenHash(tokenHash)).thenReturn(Optional.of(familyId));
        when(refreshTokenRepository.findAllByFamilyIdForUpdate(familyId)).thenReturn(
                Collections.singletonList(revokedToken)
        );

        AppException ex = assertThrows(AppException.class, () -> refreshTokenService.rotate(rawToken));
        assertEquals(ErrorCode.TOKEN_REUSE_DETECTED, ex.getErrorCode());

        // Verify active family tokens are revoked via bulk update
        verify(refreshTokenRepository).revokeActiveTokensInFamily(familyId, fixedInstant);
    }

    @Test
    void rotateWhenReuseDetectedShouldNotOverwriteExistingTombstone() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        String rawToken = "rawToken123";
        String tokenHash = "tokenHash123";
        Instant originalRevokedAt = fixedInstant.minus(Duration.ofMinutes(2));
        String originalReplacedHash = "anotherHash";

        RefreshToken revokedToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .expiresAt(fixedInstant.plus(Duration.ofMinutes(5)))
                .revokedAt(originalRevokedAt)
                .replacedByTokenHash(originalReplacedHash)
                .build();

        when(refreshTokenGenerator.hash(rawToken)).thenReturn(tokenHash);
        when(refreshTokenRepository.findFamilyIdByTokenHash(tokenHash)).thenReturn(Optional.of(familyId));
        when(refreshTokenRepository.findAllByFamilyIdForUpdate(familyId)).thenReturn(
                Collections.singletonList(revokedToken)
        );

        AppException ex = assertThrows(AppException.class, () -> refreshTokenService.rotate(rawToken));
        assertEquals(ErrorCode.TOKEN_REUSE_DETECTED, ex.getErrorCode());

        // Tombstone properties should remain exactly the same in-memory
        assertEquals(originalRevokedAt, revokedToken.getRevokedAt());
        assertEquals(originalReplacedHash, revokedToken.getReplacedByTokenHash());
        verify(refreshTokenRepository, never()).save(revokedToken);
        verify(refreshTokenRepository).revokeActiveTokensInFamily(familyId, fixedInstant);
    }

    @Test
    void rotateWhenValidShouldPreserveFamilyId() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        String rawToken = "rawToken123";
        String tokenHash = "tokenHash123";
        String rawNewToken = "rawNewToken456";
        String newHash = "newHash456";

        RefreshToken oldToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .expiresAt(fixedInstant.plus(Duration.ofMinutes(5)))
                .build();

        when(refreshTokenGenerator.hash(rawToken)).thenReturn(tokenHash);
        when(refreshTokenRepository.findFamilyIdByTokenHash(tokenHash)).thenReturn(Optional.of(familyId));
        when(refreshTokenRepository.findAllByFamilyIdForUpdate(familyId)).thenReturn(Collections.singletonList(oldToken));
        when(refreshTokenGenerator.generate()).thenReturn(rawNewToken);
        when(refreshTokenGenerator.hash(rawNewToken)).thenReturn(newHash);
        when(userService.findAuthenticationDataById(userId)).thenReturn(Optional.of(
                UserAuthenticationData.builder()
                        .id(userId)
                        .email("user@test.com")
                        .role(Role.BUYER)
                        .status(UserStatus.ACTIVE)
                        .build()
        ));
        when(jwtTokenProvider.generateAccessToken(userId, Role.BUYER)).thenReturn("newAccessToken");

        refreshTokenService.rotate(rawToken);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());

        RefreshToken savedNewToken = captor.getAllValues().get(1);
        assertEquals(familyId, savedNewToken.getFamilyId());
    }

    @Test
    void rotateWhenValidShouldPersistOnlyHashAndReturnRawToken() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        String rawToken = "rawToken123";
        String tokenHash = "tokenHash123";
        String rawNewToken = "rawNewToken456";
        String newHash = "newHash456";

        RefreshToken oldToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .expiresAt(fixedInstant.plus(Duration.ofMinutes(5)))
                .build();

        when(refreshTokenGenerator.hash(rawToken)).thenReturn(tokenHash);
        when(refreshTokenRepository.findFamilyIdByTokenHash(tokenHash)).thenReturn(Optional.of(familyId));
        when(refreshTokenRepository.findAllByFamilyIdForUpdate(familyId)).thenReturn(Collections.singletonList(oldToken));
        when(refreshTokenGenerator.generate()).thenReturn(rawNewToken);
        when(refreshTokenGenerator.hash(rawNewToken)).thenReturn(newHash);
        when(userService.findAuthenticationDataById(userId)).thenReturn(Optional.of(
                UserAuthenticationData.builder()
                        .id(userId)
                        .email("user@test.com")
                        .role(Role.BUYER)
                        .status(UserStatus.ACTIVE)
                        .build()
        ));
        when(jwtTokenProvider.generateAccessToken(userId, Role.BUYER)).thenReturn("newAccessToken");

        IssuedTokenPair result = refreshTokenService.rotate(rawToken);

        assertEquals(rawNewToken, result.refreshToken());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());

        RefreshToken savedNewToken = captor.getAllValues().get(1);
        assertEquals(newHash, savedNewToken.getTokenHash());
    }

    @Test
    void rotateWhenRawTokenNullOrBlankShouldThrowInvalidToken() {
        AppException exNull = assertThrows(AppException.class, () -> refreshTokenService.rotate(null));
        assertEquals(ErrorCode.INVALID_TOKEN, exNull.getErrorCode());

        AppException exBlank = assertThrows(AppException.class, () -> refreshTokenService.rotate("   "));
        assertEquals(ErrorCode.INVALID_TOKEN, exBlank.getErrorCode());
    }

    @Test
    void rotateWhenUserPendingVerificationShouldThrowEmailNotVerified() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        String rawToken = "rawToken123";
        String tokenHash = "tokenHash123";

        RefreshToken oldToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .expiresAt(fixedInstant.plus(Duration.ofMinutes(5)))
                .build();

        when(refreshTokenGenerator.hash(rawToken)).thenReturn(tokenHash);
        when(refreshTokenRepository.findFamilyIdByTokenHash(tokenHash)).thenReturn(Optional.of(familyId));
        when(refreshTokenRepository.findAllByFamilyIdForUpdate(familyId)).thenReturn(Collections.singletonList(oldToken));
        when(userService.findAuthenticationDataById(userId)).thenReturn(Optional.of(
                UserAuthenticationData.builder()
                        .id(userId)
                        .email("user@test.com")
                        .role(Role.BUYER)
                        .status(UserStatus.PENDING_VERIFICATION)
                        .build()
        ));

        AppException ex = assertThrows(AppException.class, () -> refreshTokenService.rotate(rawToken));
        assertEquals(ErrorCode.EMAIL_NOT_VERIFIED, ex.getErrorCode());

        // Verify no mutation is saved in database due to failure rollback
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotateWhenUserInactiveOrLockedShouldThrowAccountNotActive() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        String rawToken = "rawToken123";
        String tokenHash = "tokenHash123";

        RefreshToken oldToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .expiresAt(fixedInstant.plus(Duration.ofMinutes(5)))
                .build();

        when(refreshTokenGenerator.hash(rawToken)).thenReturn(tokenHash);
        when(refreshTokenRepository.findFamilyIdByTokenHash(tokenHash)).thenReturn(Optional.of(familyId));
        when(refreshTokenRepository.findAllByFamilyIdForUpdate(familyId)).thenReturn(Collections.singletonList(oldToken));
        when(userService.findAuthenticationDataById(userId)).thenReturn(Optional.of(
                UserAuthenticationData.builder()
                        .id(userId)
                        .email("user@test.com")
                        .role(Role.BUYER)
                        .status(UserStatus.INACTIVE)
                        .build()
        ));

        AppException ex = assertThrows(AppException.class, () -> refreshTokenService.rotate(rawToken));
        assertEquals(ErrorCode.ACCOUNT_NOT_ACTIVE, ex.getErrorCode());

        // Verify no mutation is saved in database due to failure rollback
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotateWhenUserLockedShouldThrowAccountNotActive() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        String rawToken = "rawToken123";
        String tokenHash = "tokenHash123";

        RefreshToken oldToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .expiresAt(fixedInstant.plus(Duration.ofMinutes(5)))
                .build();

        when(refreshTokenGenerator.hash(rawToken)).thenReturn(tokenHash);
        when(refreshTokenRepository.findFamilyIdByTokenHash(tokenHash)).thenReturn(Optional.of(familyId));
        when(refreshTokenRepository.findAllByFamilyIdForUpdate(familyId)).thenReturn(Collections.singletonList(oldToken));
        when(userService.findAuthenticationDataById(userId)).thenReturn(Optional.of(
                UserAuthenticationData.builder()
                        .id(userId)
                        .email("user@test.com")
                        .role(Role.BUYER)
                        .status(UserStatus.LOCKED)
                        .build()
        ));

        AppException ex = assertThrows(AppException.class, () -> refreshTokenService.rotate(rawToken));
        assertEquals(ErrorCode.ACCOUNT_NOT_ACTIVE, ex.getErrorCode());

        // Verify no mutation is saved in database due to failure rollback
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotateWhenUserNotFoundShouldThrowUserNotFound() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        String rawToken = "rawToken123";
        String tokenHash = "tokenHash123";

        RefreshToken oldToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .expiresAt(fixedInstant.plus(Duration.ofMinutes(5)))
                .build();

        when(refreshTokenGenerator.hash(rawToken)).thenReturn(tokenHash);
        when(refreshTokenRepository.findFamilyIdByTokenHash(tokenHash)).thenReturn(Optional.of(familyId));
        when(refreshTokenRepository.findAllByFamilyIdForUpdate(familyId)).thenReturn(Collections.singletonList(oldToken));
        when(userService.findAuthenticationDataById(userId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> refreshTokenService.rotate(rawToken));
        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());

        // Verify no mutation is saved in database due to failure rollback
        verify(refreshTokenRepository, never()).save(any());
    }
}

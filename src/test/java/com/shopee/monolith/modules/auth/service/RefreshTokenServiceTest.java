package com.shopee.monolith.modules.auth.service;

import com.shopee.monolith.modules.auth.config.JwtProperties;
import com.shopee.monolith.modules.auth.dto.internal.IssuedTokenPair;
import com.shopee.monolith.modules.auth.entity.RefreshToken;
import com.shopee.monolith.modules.auth.repository.RefreshTokenRepository;
import com.shopee.monolith.modules.auth.security.JwtTokenProvider;
import com.shopee.monolith.modules.auth.security.RefreshTokenGenerator;
import com.shopee.monolith.modules.user.model.Role;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

        refreshTokenService = new RefreshTokenService(
                refreshTokenRepository,
                jwtTokenProvider,
                refreshTokenGenerator,
                jwtProperties,
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
}

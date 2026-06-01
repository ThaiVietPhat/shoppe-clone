package com.shopee.monolith.modules.auth.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class SessionRevocationServiceImplTest {

    @Mock
    private SessionRevocationWorker sessionRevocationWorker;

    @Mock
    private AccessTokenBlacklistService accessTokenBlacklistService;

    private SessionRevocationService sessionRevocationService;

    @BeforeEach
    void setUp() {
        sessionRevocationService = new SessionRevocationServiceImpl(
                sessionRevocationWorker,
                accessTokenBlacklistService
        );
    }

    @Test
    void logoutShouldRevokeFamilyAndBlacklistSequentially() {
        String rawToken = "raw-refresh-token";
        AccessTokenClaims claims = AccessTokenClaims.builder()
                .jti("test-jti")
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        sessionRevocationService.logout(rawToken, claims);

        InOrder inOrder = Mockito.inOrder(sessionRevocationWorker, accessTokenBlacklistService);
        inOrder.verify(sessionRevocationWorker).revokeFamily(rawToken);
        inOrder.verify(accessTokenBlacklistService).blacklist(claims);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void logoutShouldBlacklistEvenIfTokenIsBlank() {
        AccessTokenClaims claims = AccessTokenClaims.builder()
                .jti("test-jti")
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        sessionRevocationService.logout("", claims);

        verify(sessionRevocationWorker).revokeFamily("");
        verify(accessTokenBlacklistService).blacklist(claims);
        verifyNoMoreInteractions(sessionRevocationWorker, accessTokenBlacklistService);
    }

    @Test
    void logoutShouldRevokeFamilyEvenIfClaimsAreNull() {
        String rawToken = "raw-refresh-token";

        sessionRevocationService.logout(rawToken, null);

        verify(sessionRevocationWorker).revokeFamily(rawToken);
        verifyNoInteractions(accessTokenBlacklistService);
        verifyNoMoreInteractions(sessionRevocationWorker);
    }

    @Test
    void logoutShouldPropagateRedisException() {
        String rawToken = "raw-refresh-token";
        AccessTokenClaims claims = AccessTokenClaims.builder()
                .jti("test-jti")
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        doThrow(new AppException(ErrorCode.SERVICE_UNAVAILABLE))
                .when(accessTokenBlacklistService).blacklist(claims);

        AppException ex = assertThrows(AppException.class, () ->
                sessionRevocationService.logout(rawToken, claims)
        );

        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, ex.getErrorCode());

        InOrder inOrder = Mockito.inOrder(sessionRevocationWorker, accessTokenBlacklistService);
        inOrder.verify(sessionRevocationWorker).revokeFamily(rawToken);
        inOrder.verify(accessTokenBlacklistService).blacklist(claims);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void logoutAllShouldRevokeAllUserTokensWithoutInteractingWithBlacklist() {
        UUID userId = UUID.randomUUID();

        sessionRevocationService.logoutAll(userId);

        verify(sessionRevocationWorker).revokeAll(userId);
        verifyNoInteractions(accessTokenBlacklistService);
        verifyNoMoreInteractions(sessionRevocationWorker);
    }
}

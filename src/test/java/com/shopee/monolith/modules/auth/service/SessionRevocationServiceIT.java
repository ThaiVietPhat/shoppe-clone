package com.shopee.monolith.modules.auth.service;

import com.shopee.monolith.BasePostgresRedisIntegrationTest;
import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import com.shopee.monolith.modules.auth.entity.RefreshToken;
import com.shopee.monolith.modules.auth.repository.RefreshTokenRepository;
import com.shopee.monolith.modules.auth.security.RefreshTokenGenerator;
import com.shopee.monolith.modules.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionRevocationServiceIT extends BasePostgresRedisIntegrationTest {

    @Autowired
    private SessionRevocationService sessionRevocationService;

    @Autowired
    private SessionRevocationWorker sessionRevocationWorker;

    @Autowired
    private AccessTokenBlacklistService blacklistService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenGenerator refreshTokenGenerator;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID userId1;
    private UUID userId2;

    @BeforeEach
    void setUp() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.executeWithoutResult(status -> {
            // Clean up existing tokens/users
            refreshTokenRepository.deleteAllInBatch();
            entityManager.createQuery("delete from User").executeUpdate();

            // Create 2 test users
            User u1 = User.builder()
                    .id(UUID.randomUUID())
                    .email("user1@example.com")
                    .passwordHash("hash1")
                    .build();
            User u2 = User.builder()
                    .id(UUID.randomUUID())
                    .email("user2@example.com")
                    .passwordHash("hash2")
                    .build();
            entityManager.persist(u1);
            entityManager.persist(u2);
            entityManager.flush();

            userId1 = u1.getId();
            userId2 = u2.getId();
        });
    }

    @Test
    void logoutShouldRevokeCorrectFamilyAndBlacklistAccessJti() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        String rawRt1 = "raw-rt-1";
        String rt1Hash = refreshTokenGenerator.hash(rawRt1);
        UUID family1 = UUID.randomUUID();

        String rawRt2 = "raw-rt-2";
        String rt2Hash = refreshTokenGenerator.hash(rawRt2);
        UUID family2 = UUID.randomUUID();

        txTemplate.executeWithoutResult(status -> {
            // User 1 family 1 tokens (current active + tombstone)
            entityManager.persist(RefreshToken.builder()
                    .userId(userId1)
                    .tokenHash("old-tombstone-hash")
                    .familyId(family1)
                    .expiresAt(Instant.now().plusSeconds(300))
                    .revokedAt(Instant.now())
                    .replacedByTokenHash(rt1Hash)
                    .build());
            entityManager.persist(RefreshToken.builder()
                    .userId(userId1)
                    .tokenHash(rt1Hash)
                    .familyId(family1)
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build());

            // User 1 family 2 token
            entityManager.persist(RefreshToken.builder()
                    .userId(userId1)
                    .tokenHash(rt2Hash)
                    .familyId(family2)
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build());
            entityManager.flush();
        });

        // Current Access Token claims
        String jti = UUID.randomUUID().toString();
        AccessTokenClaims claims = AccessTokenClaims.builder()
                .jti(jti)
                .expiresAt(Instant.now().plusSeconds(100))
                .build();

        // Perform logout
        sessionRevocationService.logout(rawRt1, claims);

        // Verify family 1 deleted, family 2 stays
        List<RefreshToken> family1Tokens = refreshTokenRepository.findAllByFamilyId(family1);
        assertTrue(family1Tokens.isEmpty());

        List<RefreshToken> family2Tokens = refreshTokenRepository.findAllByFamilyId(family2);
        assertEquals(1, family2Tokens.size());

        // Verify Redis blacklist has the jti
        assertTrue(blacklistService.isBlacklisted(jti));

        // Idempotency check: repeated logout call does not throw
        sessionRevocationService.logout(rawRt1, claims);
    }

    @Test
    void logoutAllShouldRevokeAllUserTokens() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        txTemplate.executeWithoutResult(status -> {
            // User 1 tokens (different families)
            entityManager.persist(RefreshToken.builder()
                    .userId(userId1)
                    .tokenHash("u1-hash-1")
                    .familyId(UUID.randomUUID())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build());
            entityManager.persist(RefreshToken.builder()
                    .userId(userId1)
                    .tokenHash("u1-hash-2")
                    .familyId(UUID.randomUUID())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build());

            // User 2 token
            entityManager.persist(RefreshToken.builder()
                    .userId(userId2)
                    .tokenHash("u2-hash-1")
                    .familyId(UUID.randomUUID())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build());
            entityManager.flush();
        });

        // Perform logoutAll for user 1
        sessionRevocationService.logoutAll(userId1);

        // User 1 tokens should be deleted
        List<RefreshToken> u1Tokens = refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUserId().equals(userId1))
                .toList();
        assertTrue(u1Tokens.isEmpty());

        // User 2 token should remain
        List<RefreshToken> u2Tokens = refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUserId().equals(userId2))
                .toList();
        assertEquals(1, u2Tokens.size());
    }

    @Test
    void logoutWithRedisUnavailableShouldRevokeDBButThrowServiceUnavailable() throws IOException {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        String rawRt = "raw-rt-3";
        String rtHash = refreshTokenGenerator.hash(rawRt);
        UUID familyId = UUID.randomUUID();

        txTemplate.executeWithoutResult(status -> {
            entityManager.persist(RefreshToken.builder()
                    .userId(userId1)
                    .tokenHash(rtHash)
                    .familyId(familyId)
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build());
            entityManager.flush();
        });

        // Create a disconnected ConnectionFactory
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        LettuceConnectionFactory connectionFactory = null;
        try {
            connectionFactory = new LettuceConnectionFactory("localhost", closedPort);
            connectionFactory.afterPropertiesSet();

            StringRedisTemplate disconnectedTemplate = new StringRedisTemplate(connectionFactory);
            AccessTokenBlacklistService brokenBlacklistService = new AccessTokenBlacklistServiceImpl(disconnectedTemplate, java.time.Clock.systemUTC());
            SessionRevocationService brokenService = new SessionRevocationServiceImpl(sessionRevocationWorker, brokenBlacklistService);

            AccessTokenClaims claims = AccessTokenClaims.builder()
                    .jti("jti-retry")
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();

            // Verify exception is thrown
            AppException ex = assertThrows(AppException.class, () -> brokenService.logout(rawRt, claims));
            assertEquals(ErrorCode.SERVICE_UNAVAILABLE, ex.getErrorCode());

            // Verify Postgres changes committed (tokens deleted)
            List<RefreshToken> tokens = refreshTokenRepository.findAllByFamilyId(familyId);
            assertTrue(tokens.isEmpty());

            // Now retry with working service (simulate retry of client)
            // Postgres deletion should be idempotent (no-op), and Redis blacklist should succeed
            sessionRevocationService.logout(rawRt, claims);
            assertTrue(blacklistService.isBlacklisted("jti-retry"));

        } finally {
            if (connectionFactory != null) {
                connectionFactory.destroy();
            }
        }
    }
}

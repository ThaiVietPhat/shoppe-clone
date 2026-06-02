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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private RefreshTokenService refreshTokenService;

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
            // Create 2 test users with unique emails and without self-generating IDs
            User u1 = User.builder()
                    .email("user1." + UUID.randomUUID() + "@example.com")
                    .passwordHash("hash1")
                    .build();
            User u2 = User.builder()
                    .email("user2." + UUID.randomUUID() + "@example.com")
                    .passwordHash("hash2")
                    .build();
            u1.activate();
            u2.activate();
            entityManager.persist(u1);
            entityManager.persist(u2);
            entityManager.flush();

            userId1 = u1.getId();
            userId2 = u2.getId();
        });
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.executeWithoutResult(status -> {
            if (userId1 != null) {
                entityManager.createQuery("delete from RefreshToken t where t.userId = :userId")
                        .setParameter("userId", userId1)
                        .executeUpdate();
                entityManager.createQuery("delete from User u where u.id = :id")
                        .setParameter("id", userId1)
                        .executeUpdate();
            }
            if (userId2 != null) {
                entityManager.createQuery("delete from RefreshToken t where t.userId = :userId")
                        .setParameter("userId", userId2)
                        .executeUpdate();
                entityManager.createQuery("delete from User u where u.id = :id")
                        .setParameter("id", userId2)
                        .executeUpdate();
            }
            entityManager.flush();
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
            persistRefreshToken(RefreshToken.builder()
                    .userId(userId1)
                    .tokenHash("old-tombstone-hash")
                    .familyId(family1)
                    .expiresAt(Instant.now().plusSeconds(300))
                    .revokedAt(Instant.now())
                    .replacedByTokenHash(rt1Hash)
                    .build());
            persistRefreshToken(RefreshToken.builder()
                    .userId(userId1)
                    .tokenHash(rt1Hash)
                    .familyId(family1)
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build());
 
            // User 1 family 2 token
            persistRefreshToken(RefreshToken.builder()
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

        // Verify family 1 tokens are revoked, family 2 stays active
        List<RefreshToken> family1Tokens = refreshTokenRepository.findAllByFamilyId(family1);
        assertFalse(family1Tokens.isEmpty());
        for (RefreshToken token : family1Tokens) {
            org.junit.jupiter.api.Assertions.assertNotNull(token.getRevokedAt());
        }
 
        List<RefreshToken> family2Tokens = refreshTokenRepository.findAllByFamilyId(family2);
        assertEquals(1, family2Tokens.size());
        org.junit.jupiter.api.Assertions.assertNull(family2Tokens.get(0).getRevokedAt());

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
            persistRefreshToken(RefreshToken.builder()
                    .userId(userId1)
                    .tokenHash("u1-hash-1")
                    .familyId(UUID.randomUUID())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build());
            persistRefreshToken(RefreshToken.builder()
                    .userId(userId1)
                    .tokenHash("u1-hash-2")
                    .familyId(UUID.randomUUID())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build());
 
            // User 2 token
            persistRefreshToken(RefreshToken.builder()
                    .userId(userId2)
                    .tokenHash("u2-hash-1")
                    .familyId(UUID.randomUUID())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build());
            entityManager.flush();
        });

        // Perform logoutAll for user 1
        sessionRevocationService.logoutAll(userId1);

        // User 1 tokens should be revoked
        List<RefreshToken> u1Tokens = refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUserId().equals(userId1))
                .toList();
        assertFalse(u1Tokens.isEmpty());
        for (RefreshToken token : u1Tokens) {
            org.junit.jupiter.api.Assertions.assertNotNull(token.getRevokedAt());
        }
 
        // User 2 token should remain active
        List<RefreshToken> u2Tokens = refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUserId().equals(userId2))
                .toList();
        assertEquals(1, u2Tokens.size());
        org.junit.jupiter.api.Assertions.assertNull(u2Tokens.get(0).getRevokedAt());
    }

    @Test
    void logoutWithRedisUnavailableShouldRevokeDBButThrowServiceUnavailable() throws IOException {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        String rawRt = "raw-rt-3";
        String rtHash = refreshTokenGenerator.hash(rawRt);
        UUID familyId = UUID.randomUUID();

        txTemplate.executeWithoutResult(status -> {
            persistRefreshToken(RefreshToken.builder()
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

            // Verify Postgres changes committed (tokens revoked)
            List<RefreshToken> tokens = refreshTokenRepository.findAllByFamilyId(familyId);
            assertFalse(tokens.isEmpty());
            for (RefreshToken token : tokens) {
                org.junit.jupiter.api.Assertions.assertNotNull(token.getRevokedAt());
            }

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

    @Test
    void logoutVsRotateConcurrencyShouldNotLeaveActiveToken() throws Exception {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);

        String rawRt1 = "concur-rt-1";
        String rt1Hash = refreshTokenGenerator.hash(rawRt1);
        UUID familyId = UUID.randomUUID();

        // 1. Persist initial token
        txTemplate.executeWithoutResult(status -> {
            persistRefreshToken(RefreshToken.builder()
                    .userId(userId1)
                    .tokenHash(rt1Hash)
                    .familyId(familyId)
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build());
            entityManager.flush();
        });

        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.atomic.AtomicReference<Exception> rotateError = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Exception> logoutError = new java.util.concurrent.atomic.AtomicReference<>();

        // Thread 1: Concurrent rotate
        Thread tRotate = new Thread(() -> {
            try {
                barrier.await();
                txTemplate.execute(status -> {
                    refreshTokenService.rotate(rawRt1);
                    return null;
                });
            } catch (Exception e) {
                rotateError.set(e);
            }
        });

        // Thread 2: Concurrent logout
        Thread tLogout = new Thread(() -> {
            try {
                barrier.await();
                sessionRevocationService.logout(rawRt1, null);
            } catch (Exception e) {
                logoutError.set(e);
            }
        });

        tRotate.setName("tRotate");
        tLogout.setName("tLogout");
        tRotate.start();
        tLogout.start();

        awaitThreads(tRotate, tLogout);

        // Verify no deadlock exceptions or unexpected errors occurred
        if (logoutError.get() != null) {
            throw logoutError.get();
        }
        if (rotateError.get() != null) {
            Exception e = rotateError.get();
            if (!(e instanceof AppException && ((AppException) e).getErrorCode() == ErrorCode.INVALID_TOKEN)) {
                throw e;
            }
        }

        // 2. Verify: No matter who wins or if one fails, there must be NO active refresh token left in the database.
        txTemplate.executeWithoutResult(status -> {
            List<RefreshToken> tokens = refreshTokenRepository.findAllByFamilyId(familyId);
            boolean hasActiveToken = tokens.stream()
                    .anyMatch(t -> t.getRevokedAt() == null && t.getExpiresAt().isAfter(Instant.now()));
            assertFalse(hasActiveToken, "There should be no active refresh token left in the family!");
        });
    }

    @Test
    void logoutTombstoneVsRotateActiveConcurrencyShouldNotLeaveActiveToken() throws Exception {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);

        String rawRt1 = "tombstone-rt-1";
        String rt1Hash = refreshTokenGenerator.hash(rawRt1);
        String rawRt2 = "active-rt-2";
        String rt2Hash = refreshTokenGenerator.hash(rawRt2);
        UUID familyId = UUID.randomUUID();

        // 1. Setup family state: T1 (tombstone) replaced by T2 (active)
        txTemplate.executeWithoutResult(status -> {
            persistRefreshToken(RefreshToken.builder()
                    .userId(userId1)
                    .tokenHash(rt1Hash)
                    .familyId(familyId)
                    .expiresAt(Instant.now().plusSeconds(300))
                    .revokedAt(Instant.now())
                    .replacedByTokenHash(rt2Hash)
                    .build());
            persistRefreshToken(RefreshToken.builder()
                    .userId(userId1)
                    .tokenHash(rt2Hash)
                    .familyId(familyId)
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build());
            entityManager.flush();
        });

        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.atomic.AtomicReference<Exception> rotateError = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Exception> logoutError = new java.util.concurrent.atomic.AtomicReference<>();

        // Thread 1: Concurrent rotate on active token T2
        Thread tRotate = new Thread(() -> {
            try {
                barrier.await();
                txTemplate.execute(status -> {
                    refreshTokenService.rotate(rawRt2);
                    return null;
                });
            } catch (Exception e) {
                rotateError.set(e);
            }
        });

        // Thread 2: Concurrent logout on tombstone token T1
        Thread tLogout = new Thread(() -> {
            try {
                barrier.await();
                sessionRevocationService.logout(rawRt1, null);
            } catch (Exception e) {
                logoutError.set(e);
            }
        });

        tRotate.setName("tRotateTombstoneVsActive");
        tLogout.setName("tLogoutTombstoneVsActive");
        tRotate.start();
        tLogout.start();

        awaitThreads(tRotate, tLogout);

        // Verify no deadlock exceptions or unexpected errors occurred
        if (logoutError.get() != null) {
            throw logoutError.get();
        }
        if (rotateError.get() != null) {
            Exception e = rotateError.get();
            if (!(e instanceof AppException && ((AppException) e).getErrorCode() == ErrorCode.INVALID_TOKEN)) {
                throw e;
            }
        }

        // Verify there is no active token left in the database for this family
        txTemplate.executeWithoutResult(status -> {
            List<RefreshToken> tokens = refreshTokenRepository.findAllByFamilyId(familyId);
            boolean hasActiveToken = tokens.stream()
                    .anyMatch(t -> t.getRevokedAt() == null && t.getExpiresAt().isAfter(Instant.now()));
            assertFalse(hasActiveToken, "There should be no active refresh token left in the family!");
        });
    }

    @Test
    void loginVsLogoutAllConcurrencyShouldNotDeadlock() throws Exception {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);

        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.atomic.AtomicReference<Exception> loginError = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Exception> logoutAllError = new java.util.concurrent.atomic.AtomicReference<>();

        // Thread 1: Concurrent login/issueTokenPair
        Thread tLogin = new Thread(() -> {
            try {
                barrier.await();
                txTemplate.execute(status -> {
                    refreshTokenService.issueTokenPair(userId1, com.shopee.monolith.modules.user.model.Role.BUYER);
                    return null;
                });
            } catch (Exception e) {
                loginError.set(e);
            }
        });

        // Thread 2: Concurrent logoutAll
        Thread tLogoutAll = new Thread(() -> {
            try {
                barrier.await();
                sessionRevocationService.logoutAll(userId1);
            } catch (Exception e) {
                logoutAllError.set(e);
            }
        });

        tLogin.setName("tLogin");
        tLogoutAll.setName("tLogoutAll");
        tLogin.start();
        tLogoutAll.start();

        awaitThreads(tLogin, tLogoutAll);

        // Verify no deadlock or unhandled exception happened
        if (loginError.get() != null) {
            throw loginError.get();
        }
        if (logoutAllError.get() != null) {
            throw logoutAllError.get();
        }
    }

    private void awaitThreads(Thread... threads) {
        for (Thread thread : threads) {
            try {
                thread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        for (Thread thread : threads) {
            if (thread.isAlive()) {
                thread.interrupt();
            }
        }
        for (Thread thread : threads) {
            assertFalse(thread.isAlive(), thread.getName() + " thread did not finish within timeout");
        }
    }

    private void persistRefreshToken(RefreshToken token) {
        entityManager.createNativeQuery(
                "INSERT INTO refresh_token_families (id, user_id, created_at, updated_at) " +
                "VALUES (:id, :userId, NOW(), NOW()) " +
                "ON CONFLICT (id) DO NOTHING")
                .setParameter("id", token.getFamilyId())
                .setParameter("userId", token.getUserId())
                .executeUpdate();
        entityManager.persist(token);
    }
}

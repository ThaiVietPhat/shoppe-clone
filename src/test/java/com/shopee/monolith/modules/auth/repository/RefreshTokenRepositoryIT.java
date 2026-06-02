package com.shopee.monolith.modules.auth.repository;

import com.shopee.monolith.BasePostgresRedisIntegrationTest;
import com.shopee.monolith.modules.auth.entity.RefreshToken;
import com.shopee.monolith.modules.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.dto.internal.IssuedTokenPair;
import com.shopee.monolith.modules.auth.security.RefreshTokenGenerator;
import com.shopee.monolith.modules.auth.service.RefreshTokenService;
import com.shopee.monolith.modules.user.model.Role;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class RefreshTokenRepositoryIT extends BasePostgresRedisIntegrationTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenGenerator refreshTokenGenerator;

    private User testUser;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        testUser = txTemplate.execute(status -> {
            User user = User.builder()
                    .email("refresh." + UUID.randomUUID() + "@example.com")
                    .passwordHash("hash")
                    .build();
            entityManager.persist(user);
            entityManager.flush();
            return user;
        });
        testUserId = testUser.getId();
    }

    @Test
    void findByTokenHashWhenTokenExistsShouldReturnToken() {
        String tokenHash = "token_hash_1";
        RefreshToken token = RefreshToken.builder()
                .userId(testUserId)
                .tokenHash(tokenHash)
                .familyId(UUID.randomUUID())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        entityManager.persist(token);
        entityManager.flush();
        entityManager.clear();

        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash(tokenHash);

        assertTrue(found.isPresent());
        assertEquals(tokenHash, found.get().getTokenHash());
        assertEquals(testUserId, found.get().getUserId());
    }

    @Test
    void existsByFamilyIdWhenTokenExistsShouldReturnTrue() {
        UUID familyId = UUID.randomUUID();
        RefreshToken token = RefreshToken.builder()
                .userId(testUserId)
                .tokenHash("token_hash_2")
                .familyId(familyId)
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        entityManager.persist(token);
        entityManager.flush();
        entityManager.clear();

        boolean exists = refreshTokenRepository.existsByFamilyId(familyId);

        assertTrue(exists);
        assertFalse(refreshTokenRepository.existsByFamilyId(UUID.randomUUID()));
    }

    @Test
    void deleteByFamilyIdWhenTokensExistShouldDeleteFamilyOnly() {
        UUID familyIdDelete = UUID.randomUUID();
        UUID familyIdKeep = UUID.randomUUID();

        RefreshToken token1 = RefreshToken.builder()
                .userId(testUserId)
                .tokenHash("token_hash_3")
                .familyId(familyIdDelete)
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        RefreshToken token2 = RefreshToken.builder()
                .userId(testUserId)
                .tokenHash("token_hash_4")
                .familyId(familyIdKeep)
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        entityManager.persist(token1);
        entityManager.persist(token2);
        entityManager.flush();
        entityManager.clear();

        long deletedCount = refreshTokenRepository.deleteByFamilyId(familyIdDelete);
        entityManager.flush();
        entityManager.clear();

        assertEquals(1L, deletedCount);
        assertFalse(refreshTokenRepository.findByTokenHash("token_hash_3").isPresent());
        assertTrue(refreshTokenRepository.findByTokenHash("token_hash_4").isPresent());
    }

    @Test
    void deleteByUserIdWhenTokensExistShouldDeleteAllUserTokens() {
        User otherUser = User.builder()
                .email("other." + UUID.randomUUID() + "@example.com")
                .passwordHash("hash")
                .build();
        entityManager.persist(otherUser);
        entityManager.flush();

        RefreshToken userToken = RefreshToken.builder()
                .userId(testUserId)
                .tokenHash("token_hash_5")
                .familyId(UUID.randomUUID())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        RefreshToken otherToken = RefreshToken.builder()
                .userId(otherUser.getId())
                .tokenHash("token_hash_6")
                .familyId(UUID.randomUUID())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        entityManager.persist(userToken);
        entityManager.persist(otherToken);
        entityManager.flush();
        entityManager.clear();

        long deletedCount = refreshTokenRepository.deleteByUserId(testUserId);
        entityManager.flush();
        entityManager.clear();

        assertEquals(1L, deletedCount);
        assertFalse(refreshTokenRepository.findByTokenHash("token_hash_5").isPresent());
        assertTrue(refreshTokenRepository.findByTokenHash("token_hash_6").isPresent());
    }

    @Test
    void deleteByExpiresAtBeforeWhenExpiredTokensExistShouldDeleteExpiredOnly() {
        Instant now = Instant.now();
        Instant expiredTime = now.minusSeconds(60);
        Instant futureTime = now.plusSeconds(300);

        RefreshToken expiredToken = RefreshToken.builder()
                .userId(testUserId)
                .tokenHash("token_hash_7")
                .familyId(UUID.randomUUID())
                .expiresAt(expiredTime)
                .build();

        RefreshToken validToken = RefreshToken.builder()
                .userId(testUserId)
                .tokenHash("token_hash_8")
                .familyId(UUID.randomUUID())
                .expiresAt(futureTime)
                .build();

        entityManager.persist(expiredToken);
        entityManager.persist(validToken);
        entityManager.flush();
        entityManager.clear();

        long deletedCount = refreshTokenRepository.deleteByExpiresAtBefore(now);
        entityManager.flush();
        entityManager.clear();

        assertEquals(1L, deletedCount);
        assertFalse(refreshTokenRepository.findByTokenHash("token_hash_7").isPresent());
        assertTrue(refreshTokenRepository.findByTokenHash("token_hash_8").isPresent());
    }

    @Test
    void existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfterWhenActiveExistsShouldReturnTrueAndFalseWhenOnlyRevokedExists() {
        UUID familyId = UUID.randomUUID();

        // 1. Initially, only a revoked token exists in the family
        RefreshToken revokedToken = RefreshToken.builder()
                .userId(testUserId)
                .tokenHash("token_hash_revoked")
                .familyId(familyId)
                .expiresAt(Instant.now().plusSeconds(300))
                .revokedAt(Instant.now())
                .replacedByTokenHash("token_hash_replacement")
                .build();
        entityManager.persist(revokedToken);
        entityManager.flush();
        entityManager.clear();

        // should return false since the only token is revoked
        assertFalse(refreshTokenRepository.existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(familyId, Instant.now()));

        // 2. Persist an active token in the same family
        RefreshToken activeToken = RefreshToken.builder()
                .userId(testUserId)
                .tokenHash("token_hash_active")
                .familyId(familyId)
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        entityManager.persist(activeToken);
        entityManager.flush();
        entityManager.clear();

        // should now return true
        assertTrue(refreshTokenRepository.existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(familyId, Instant.now()));
    }

    @Test
    void existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfterWhenExpiredExistsShouldReturnFalse() {
        UUID familyId = UUID.randomUUID();

        // Persist an expired (non-revoked) token in the family
        RefreshToken expiredToken = RefreshToken.builder()
                .userId(testUserId)
                .tokenHash("token_hash_expired_in_family")
                .familyId(familyId)
                .expiresAt(Instant.now().minusSeconds(10)) // expired in the past
                .build();
        entityManager.persist(expiredToken);
        entityManager.flush();
        entityManager.clear();

        // should return false because the token is expired
        assertFalse(refreshTokenRepository.existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(familyId, Instant.now()));
    }

    @Test
    void findByTokenHashWhenTokenWasRevokedShouldReturnRevocationHistory() {
        String tokenHash = "old_token_hash";
        String replacementHash = "new_token_hash";
        RefreshToken token = RefreshToken.builder()
                .userId(testUserId)
                .tokenHash(tokenHash)
                .familyId(UUID.randomUUID())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        entityManager.persist(token);
        entityManager.flush();

        Instant now = Instant.now();
        token.revoke(now, replacementHash);
        entityManager.persist(token);
        entityManager.flush();
        entityManager.clear();

        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash(tokenHash);

        assertTrue(found.isPresent());
        assertEquals(tokenHash, found.get().getTokenHash());
        org.junit.jupiter.api.Assertions.assertNotNull(found.get().getRevokedAt());
        assertEquals(now.toEpochMilli(), found.get().getRevokedAt().toEpochMilli());
        assertEquals(replacementHash, found.get().getReplacedByTokenHash());
    }

    @Test
    void findAllByFamilyIdAndRevokeFamilyWithoutDeletingHistory() {
        UUID familyId = UUID.randomUUID();

        // 1. Persist an active token and a previously revoked token (history) in the same family
        RefreshToken activeToken = RefreshToken.builder()
                .userId(testUserId)
                .tokenHash("token_active_in_fam")
                .familyId(familyId)
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        RefreshToken revokedToken = RefreshToken.builder()
                .userId(testUserId)
                .tokenHash("token_revoked_in_fam")
                .familyId(familyId)
                .expiresAt(Instant.now().plusSeconds(300))
                .revokedAt(Instant.now().minusSeconds(10))
                .replacedByTokenHash("token_active_in_fam")
                .build();

        entityManager.persist(activeToken);
        entityManager.persist(revokedToken);
        entityManager.flush();
        entityManager.clear();

        // 2. Query family tokens
        java.util.List<RefreshToken> familyTokens = refreshTokenRepository.findAllByFamilyId(familyId);
        assertEquals(2, familyTokens.size());

        // 3. Perform rotation/theft revocation (revoke all active ones)
        Instant now = Instant.now();
        for (RefreshToken token : familyTokens) {
            if (token.getRevokedAt() == null) {
                token.revoke(now);
                refreshTokenRepository.save(token);
            }
        }
        entityManager.flush();
        entityManager.clear();

        // 4. Verify that both records still exist in the database (history is kept)
        java.util.List<RefreshToken> afterRevocation = refreshTokenRepository.findAllByFamilyId(familyId);
        assertEquals(2, afterRevocation.size());

        // Both must now have revokedAt set
        for (RefreshToken token : afterRevocation) {
            org.junit.jupiter.api.Assertions.assertNotNull(token.getRevokedAt());
            if (token.getTokenHash().equals("token_active_in_fam")) {
                org.junit.jupiter.api.Assertions.assertNull(token.getReplacedByTokenHash());
            }
        }
    }

    @Test
    void findByTokenHashForUpdateShouldReturnTokenWithPessimisticWriteLock() {
        String tokenHash = "lock_token_hash_1";
        RefreshToken token = RefreshToken.builder()
                .userId(testUserId)
                .tokenHash(tokenHash)
                .familyId(UUID.randomUUID())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        entityManager.persist(token);
        entityManager.flush();
        entityManager.clear();

        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHashForUpdate(tokenHash);

        assertTrue(found.isPresent());
        assertEquals(tokenHash, found.get().getTokenHash());
    }

    @Test
    void findAllByFamilyIdForUpdateShouldReturnTokensWithPessimisticWriteLock() {
        UUID familyId = UUID.randomUUID();
        RefreshToken token1 = RefreshToken.builder()
                .userId(testUserId)
                .tokenHash("lock_token_hash_2")
                .familyId(familyId)
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        RefreshToken token2 = RefreshToken.builder()
                .userId(testUserId)
                .tokenHash("lock_token_hash_3")
                .familyId(familyId)
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        entityManager.persist(token1);
        entityManager.persist(token2);
        entityManager.flush();
        entityManager.clear();

        java.util.List<RefreshToken> tokens = refreshTokenRepository.findAllByFamilyIdForUpdate(familyId);

        assertEquals(2, tokens.size());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rotateWhenTwoTransactionsCompeteShouldSerializeRotationAndNotRollbackRevocation() throws Exception {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        // 1. Setup user and commit to DB
        UUID userId = txTemplate.execute(status -> {
            User user = User.builder()
                    .email("compete." + UUID.randomUUID() + "@example.com")
                    .passwordHash("hash")
                    .build();
            entityManager.persist(user);
            return user.getId();
        });

        // 2. Setup initial active refresh token RT1
        IssuedTokenPair rt1Pair = txTemplate.execute(status -> {
            User user = entityManager.find(User.class, userId);
            user.activate();
            entityManager.persist(user);
            return refreshTokenService.issueTokenPair(userId, Role.BUYER);
        });

        String rawRt1Value = rt1Pair.refreshToken();

        // 3. Concurrently call rotate(rawRt1Value) from two threads
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger reuseCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicReference<String> rt2ValueRef = new java.util.concurrent.atomic.AtomicReference<>();

        java.util.concurrent.Callable<Void> task = () -> {
            barrier.await();
            try {
                IssuedTokenPair resultPair = refreshTokenService.rotate(rawRt1Value);
                rt2ValueRef.set(resultPair.refreshToken());
                successCount.incrementAndGet();
            } catch (AppException e) {
                if (e.getErrorCode() == ErrorCode.TOKEN_REUSE_DETECTED) {
                    reuseCount.incrementAndGet();
                } else {
                    throw e;
                }
            }
            return null;
        };

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            try {
                java.util.List<java.util.concurrent.Future<Void>> futures = executor.invokeAll(
                        java.util.Arrays.asList(task, task)
                );
                for (java.util.concurrent.Future<Void> future : futures) {
                    future.get();
                }

                // 4. Verify exactly one thread succeeded in rotating (producing RT2)
                assertEquals(1, successCount.get());
                // Exactly one thread got TOKEN_REUSE_DETECTED
                assertEquals(1, reuseCount.get());

                // 5. Verify database state: both RT1 and RT2 must be revoked because reuse was committed successfully
                txTemplate.executeWithoutResult(status -> {
                    String rt1Hash = refreshTokenGenerator.hash(rawRt1Value);
                    RefreshToken rt1InDb = refreshTokenRepository.findByTokenHash(rt1Hash)
                            .orElseThrow(() -> new AssertionError("RT1 not found"));
                    assertNotNull(rt1InDb.getRevokedAt());

                    String rt2Raw = rt2ValueRef.get();
                    assertNotNull(rt2Raw);
                    String rt2Hash = refreshTokenGenerator.hash(rt2Raw);
                    RefreshToken rt2InDb = refreshTokenRepository.findByTokenHash(rt2Hash)
                            .orElseThrow(() -> new AssertionError("RT2 not found"));
                    assertNotNull(rt2InDb.getRevokedAt(), "RT2 revocation was rolled back!");
                });
            } finally {
                // 6. Cleanup DB state to prevent pollution
                txTemplate.executeWithoutResult(status -> {
                    entityManager.createQuery("delete from RefreshToken t where t.userId in (:userId, :testUserId)")
                            .setParameter("userId", userId)
                            .setParameter("testUserId", testUserId)
                            .executeUpdate();
                    entityManager.createQuery("delete from User u where u.id in (:userId, :testUserId)")
                            .setParameter("userId", userId)
                            .setParameter("testUserId", testUserId)
                            .executeUpdate();
                });
            }
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void findAllByUserIdForUpdateShouldOrderStably() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.executeWithoutResult(status -> {
            // Create 3 tokens out of order
            RefreshToken token2 = RefreshToken.builder()
                    .userId(testUserId)
                    .tokenHash("hash-b")
                    .familyId(UUID.randomUUID())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();
            RefreshToken token1 = RefreshToken.builder()
                    .userId(testUserId)
                    .tokenHash("hash-a")
                    .familyId(UUID.randomUUID())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();
            RefreshToken token3 = RefreshToken.builder()
                    .userId(testUserId)
                    .tokenHash("hash-c")
                    .familyId(UUID.randomUUID())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();
            entityManager.persist(token2);
            entityManager.persist(token1);
            entityManager.persist(token3);
            entityManager.flush();
        });

        TransactionTemplate txTemplate2 = new TransactionTemplate(transactionManager);
        txTemplate2.executeWithoutResult(status -> {
            java.util.List<RefreshToken> tokens = refreshTokenRepository.findAllByUserIdForUpdate(testUserId);
            assertEquals(3, tokens.size());
            // Verify sorted by UUID ID order ascending
            String firstId = tokens.get(0).getId().toString();
            String secondId = tokens.get(1).getId().toString();
            String thirdId = tokens.get(2).getId().toString();
            assertTrue(firstId.compareTo(secondId) < 0);
            assertTrue(secondId.compareTo(thirdId) < 0);
        });

        // Cleanup
        txTemplate.executeWithoutResult(status -> {
            refreshTokenRepository.deleteByUserId(testUserId);
        });
    }

    @Test
    void deleteExpiredTokensBatchShouldDeleteOnlyExpiredUpToLimit() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.executeWithoutResult(status -> {
            Instant past = Instant.now().minusSeconds(60);
            Instant future = Instant.now().plusSeconds(300);

            // Create 3 expired tokens
            for (int i = 0; i < 3; i++) {
                entityManager.persist(RefreshToken.builder()
                        .userId(testUserId)
                        .tokenHash("expired-hash-" + i)
                        .familyId(UUID.randomUUID())
                        .expiresAt(past)
                        .build());
            }
            // Create 1 active token
            entityManager.persist(RefreshToken.builder()
                    .userId(testUserId)
                    .tokenHash("active-hash")
                    .familyId(UUID.randomUUID())
                    .expiresAt(future)
                    .build());
            entityManager.flush();
        });

        // Delete with batch limit of 2
        TransactionTemplate txTemplate2 = new TransactionTemplate(transactionManager);
        int deleted = txTemplate2.execute(status -> {
            return refreshTokenRepository.deleteExpiredTokensBatch(Instant.now(), 2);
        });
        assertEquals(2, deleted);

        // Verify remaining expired token and active token are still in DB
        txTemplate2.executeWithoutResult(status -> {
            java.util.List<RefreshToken> remaining = refreshTokenRepository.findAll();
            long expiredCount = remaining.stream().filter(t -> t.getExpiresAt().isBefore(Instant.now())).count();
            long activeCount = remaining.stream().filter(t -> t.getExpiresAt().isAfter(Instant.now())).count();
            assertEquals(1, expiredCount);
            assertEquals(1, activeCount);
        });

        // Cleanup
        txTemplate.executeWithoutResult(status -> {
            refreshTokenRepository.deleteByUserId(testUserId);
        });
    }

    @Test
    void deleteExpiredTokensBatchShouldSkipLocked() throws Exception {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);

        // Create committed user specifically for this test to avoid FK violation in REQUIRES_NEW
        UUID committedUserId = txTemplate.execute(status -> {
            User user = User.builder()
                    .email("concur-cleanup." + UUID.randomUUID() + "@example.com")
                    .passwordHash("hash")
                    .build();
            entityManager.persist(user);
            entityManager.flush();
            return user.getId();
        });

        // 1. Persist 2 expired tokens
        UUID tokenId1 = txTemplate.execute(status -> {
            RefreshToken t1 = RefreshToken.builder()
                    .userId(committedUserId)
                    .tokenHash("lock-hash-1")
                    .familyId(UUID.randomUUID())
                    .expiresAt(Instant.now().minusSeconds(10))
                    .build();
            RefreshToken t2 = RefreshToken.builder()
                    .userId(committedUserId)
                    .tokenHash("lock-hash-2")
                    .familyId(UUID.randomUUID())
                    .expiresAt(Instant.now().minusSeconds(10))
                    .build();
            entityManager.persist(t1);
            entityManager.persist(t2);
            entityManager.flush();
            return t1.getId();
        });

        java.util.concurrent.CountDownLatch lockAcquiredLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch deleteCompletedLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger deletedCount = new java.util.concurrent.atomic.AtomicInteger(-1);

        // 2. Thread 1: Acquire lock on tokenId1 and hold it
        Thread thread = new Thread(() -> {
            txTemplate.execute(status -> {
                entityManager.createQuery("select r from RefreshToken r where r.id = :id")
                        .setParameter("id", tokenId1)
                        .setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
                        .getSingleResult();

                lockAcquiredLatch.countDown();
                try {
                    deleteCompletedLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
        });
        thread.start();

        try {
            // Wait until Thread 1 holds the lock
            lockAcquiredLatch.await();

            // 3. Thread 2 (Main Thread): Try to clean up expired tokens batch
            txTemplate.executeWithoutResult(status -> {
                int deleted = refreshTokenRepository.deleteExpiredTokensBatch(Instant.now(), 10);
                deletedCount.set(deleted);
            });
        } finally {
            // Release lock
            deleteCompletedLatch.countDown();
            thread.join();

            // Cleanup DB state to prevent pollution
            txTemplate.executeWithoutResult(status -> {
                entityManager.createQuery("delete from RefreshToken t where t.userId = :userId")
                        .setParameter("userId", committedUserId)
                        .executeUpdate();
                entityManager.createQuery("delete from User u where u.id = :id")
                        .setParameter("id", committedUserId)
                        .executeUpdate();
            });
        }

        // 4. Verify only 1 token was deleted because tokenId1 was locked and skipped
        assertEquals(1, deletedCount.get());
    }
}

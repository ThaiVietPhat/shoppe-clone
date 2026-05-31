package com.shopee.monolith.modules.auth.repository;

import com.shopee.monolith.BaseIntegrationTest;
import com.shopee.monolith.modules.auth.entity.RefreshToken;
import com.shopee.monolith.modules.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class RefreshTokenRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private EntityManager entityManager;

    private User testUser;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("refresh." + UUID.randomUUID() + "@example.com")
                .passwordHash("hash")
                .build();
        entityManager.persist(testUser);
        entityManager.flush();
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
                token.revoke(now, "revoked_due_to_theft");
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
                assertEquals("revoked_due_to_theft", token.getReplacedByTokenHash());
            }
        }
    }
}

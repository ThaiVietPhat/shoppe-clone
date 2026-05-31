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
}

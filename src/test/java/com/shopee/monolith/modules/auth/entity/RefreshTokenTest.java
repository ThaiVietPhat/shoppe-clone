package com.shopee.monolith.modules.auth.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RefreshTokenTest {

    @Test
    void builderWhenAllFieldsProvidedShouldCreateRefreshToken() {
        UUID userId = UUID.randomUUID();
        String tokenHash = "hash123";
        UUID familyId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(600);
        Instant revokedAt = Instant.now();
        String replacedByTokenHash = "replaced_hash_123";

        RefreshToken token = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .expiresAt(expiresAt)
                .revokedAt(revokedAt)
                .replacedByTokenHash(replacedByTokenHash)
                .build();

        assertNotNull(token);
        assertEquals(userId, token.getUserId());
        assertEquals(tokenHash, token.getTokenHash());
        assertEquals(familyId, token.getFamilyId());
        assertEquals(expiresAt, token.getExpiresAt());
        assertEquals(revokedAt, token.getRevokedAt());
        assertEquals(replacedByTokenHash, token.getReplacedByTokenHash());
    }

    @Test
    void revokeShouldSetRevokedAtAndReplacedByTokenHash() {
        RefreshToken token = RefreshToken.builder()
                .userId(UUID.randomUUID())
                .tokenHash("hash123")
                .familyId(UUID.randomUUID())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        Instant revokedAt = Instant.now();
        String replacedByTokenHash = "new_hash";

        token.revoke(revokedAt, replacedByTokenHash);

        assertEquals(revokedAt, token.getRevokedAt());
        assertEquals(replacedByTokenHash, token.getReplacedByTokenHash());
    }
}

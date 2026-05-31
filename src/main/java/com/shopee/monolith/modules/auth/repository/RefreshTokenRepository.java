package com.shopee.monolith.modules.auth.repository;

import com.shopee.monolith.modules.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    boolean existsByFamilyId(UUID familyId);

    long deleteByFamilyId(UUID familyId);

    long deleteByUserId(UUID userId);

    long deleteByExpiresAtBefore(Instant now);
}

package com.shopee.monolith.modules.auth.repository;

import com.shopee.monolith.modules.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<RefreshToken> findAllByFamilyId(UUID familyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.familyId = :familyId")
    List<RefreshToken> findAllByFamilyIdForUpdate(@Param("familyId") UUID familyId);

    /**
     * WARNING: This method checks for any token record belonging to the family,
     * including those that have been revoked or expired (tombstones/history).
     * Do NOT use this to check if a family is still active. Use
     * {@link #existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(UUID, Instant)} instead.
     */
    boolean existsByFamilyId(UUID familyId);

    /**
     * Checks if there exists an active (non-revoked and non-expired) token in the specified family.
     * Use this when verifying if a token family is still alive during rotation.
     */
    boolean existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(UUID familyId, Instant now);

    /**
     * WARNING: This method performs a hard delete of all refresh token records in a family
     * (including revocation history).
     * Do NOT call this during token rotation. Use {@link RefreshToken#revoke(Instant, String)} or
     * {@link RefreshToken#revoke(Instant)} instead.
     * This is only intended for explicit user logouts or background cleanup routines.
     */
    long deleteByFamilyId(UUID familyId);

    /**
     * WARNING: This method performs a hard delete of all refresh token records for a user
     * (including revocation history).
     * Do NOT call this during token rotation. Use {@link RefreshToken#revoke(Instant, String)} or
     * {@link RefreshToken#revoke(Instant)} instead.
     * This is only intended for explicit user logouts or background cleanup routines.
     */
    long deleteByUserId(UUID userId);

    long deleteByExpiresAtBefore(Instant now);
}

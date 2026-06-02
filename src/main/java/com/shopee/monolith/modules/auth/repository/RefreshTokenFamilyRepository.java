package com.shopee.monolith.modules.auth.repository;

import com.shopee.monolith.modules.auth.entity.RefreshTokenFamily;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenFamilyRepository extends JpaRepository<RefreshTokenFamily, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from RefreshTokenFamily f where f.id = :id")
    Optional<RefreshTokenFamily> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from RefreshTokenFamily f where f.userId = :userId order by f.id asc")
    List<RefreshTokenFamily> findAllByUserIdForUpdate(@Param("userId") UUID userId);

    @Modifying
    @Query(value = "DELETE FROM refresh_token_families " +
                   "WHERE id IN (" +
                   "    SELECT f.id " +
                   "    FROM refresh_token_families f " +
                   "    WHERE f.id IN (" +
                   "        SELECT f2.id " +
                   "        FROM refresh_token_families f2 " +
                   "        LEFT JOIN refresh_tokens t ON t.family_id = f2.id " +
                   "        GROUP BY f2.id " +
                   "        HAVING COALESCE(MAX(t.expires_at), f2.created_at) < :now" +
                   "    ) " +
                   "    LIMIT :batchSize " +
                   "    FOR UPDATE SKIP LOCKED" +
                   ")", nativeQuery = true)
    int deleteExpiredFamiliesBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);
}

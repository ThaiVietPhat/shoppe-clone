package com.shopee.monolith.modules.order.repository;

import com.shopee.monolith.modules.order.entity.CheckoutSession;
import com.shopee.monolith.modules.order.model.CheckoutSessionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CheckoutSessionRepository extends JpaRepository<CheckoutSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CheckoutSession s where s.id = :id")
    Optional<CheckoutSession> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "SELECT * FROM checkout_sessions " +
                   "WHERE id = :id AND status = :status " +
                   "FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<CheckoutSession> findByIdAndStatusForUpdateSkipLocked(
            @Param("id") UUID id,
            @Param("status") String status
    );

    @Query("select s.id from CheckoutSession s where s.status = :status and s.expiresAt < :now")
    List<UUID> findExpiredIds(
            @Param("status") CheckoutSessionStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );
}

package com.shopee.monolith.modules.order.repository;

import com.shopee.monolith.modules.order.entity.CheckoutSession;
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
                   "WHERE status = :status AND expires_at < :now " +
                   "LIMIT :limit " +
                   "FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<CheckoutSession> findExpiredForUpdate(
            @Param("status") String status,
            @Param("now") Instant now,
            @Param("limit") int limit
    );
}

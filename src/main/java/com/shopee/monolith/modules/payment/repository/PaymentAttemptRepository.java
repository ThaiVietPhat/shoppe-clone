package com.shopee.monolith.modules.payment.repository;

import com.shopee.monolith.modules.payment.entity.PaymentAttempt;
import com.shopee.monolith.modules.payment.model.PaymentAttemptStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentAttempt p where p.id = :id")
    Optional<PaymentAttempt> findByIdForUpdate(@Param("id") UUID id);

    List<PaymentAttempt> findAllByCheckoutSessionIdOrderByCreatedAtDesc(UUID checkoutSessionId);

    @Query("select p.id from PaymentAttempt p "
            + "where p.status in :statuses and p.expiresAt < :now order by p.expiresAt asc")
    List<UUID> findExpiredIds(
            @Param("statuses") Collection<PaymentAttemptStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Query(value = "SELECT * FROM payment_attempts "
            + "WHERE id = :id AND status IN ('CREATED', 'INITIATING', 'PENDING') "
            + "FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<PaymentAttempt> findNonTerminalByIdForUpdateSkipLocked(@Param("id") UUID id);

    /**
     * Bulk-expire all non-terminal attempts for a session in a single conditional UPDATE.
     * Bypasses @Version so it never throws OptimisticLockException.
     * Rows already in a terminal state are skipped by the WHERE condition.
     */
    @Modifying
    @Query("UPDATE PaymentAttempt a SET a.status = :expired "
            + "WHERE a.checkoutSessionId = :sessionId AND a.status NOT IN :terminalStatuses")
    int expireNonTerminalByCheckoutSession(
            @Param("sessionId") UUID sessionId,
            @Param("expired") PaymentAttemptStatus expired,
            @Param("terminalStatuses") Collection<PaymentAttemptStatus> terminalStatuses);
}

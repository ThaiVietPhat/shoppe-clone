package com.shopee.monolith.modules.order.repository;

import com.shopee.monolith.modules.order.entity.IdempotencyKey;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from IdempotencyKey k where k.actorId = :actorId and k.operation = :operation and k.idempotencyKey = :idempotencyKey")
    Optional<IdempotencyKey> findByKeysForUpdate(
            @Param("actorId") UUID actorId,
            @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey
    );

    Optional<IdempotencyKey> findByActorIdAndOperationAndIdempotencyKey(UUID actorId, String operation, String idempotencyKey);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value =
            "INSERT INTO idempotency_keys (id, actor_id, operation, idempotency_key, request_hash, request_body_hash, status, expires_at, created_at, updated_at) " +
            "VALUES (:id, :actorId, :operation, :idempotencyKey, :requestHash, :requestBodyHash, :status, :expiresAt, NOW(), NOW()) " +
            "ON CONFLICT (actor_id, operation, idempotency_key) DO NOTHING", nativeQuery = true)
    int tryInsert(
            @Param("id") UUID id,
            @Param("actorId") UUID actorId,
            @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash,
            @Param("requestBodyHash") String requestBodyHash,
            @Param("status") String status,
            @Param("expiresAt") java.time.Instant expiresAt
    );
}

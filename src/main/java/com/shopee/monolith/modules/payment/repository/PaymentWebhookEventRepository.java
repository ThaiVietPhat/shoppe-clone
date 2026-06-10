package com.shopee.monolith.modules.payment.repository;

import com.shopee.monolith.modules.payment.entity.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, UUID> {

    /**
     * Atomically claims a provider event. Returns 0 when the (provider, provider_event_id)
     * pair was already claimed — caller must treat the webhook as a duplicate no-op.
     */
    @Modifying
    @Query(value = "INSERT INTO payment_webhook_events "
            + "(id, provider, provider_event_id, payment_attempt_id, raw_payload_hash, processed_at, created_at, updated_at) "
            + "VALUES (:id, :provider, :providerEventId, :paymentAttemptId, :rawPayloadHash, NOW(), NOW(), NOW()) "
            + "ON CONFLICT (provider, provider_event_id) DO NOTHING", nativeQuery = true)
    int tryClaim(
            @Param("id") UUID id,
            @Param("provider") String provider,
            @Param("providerEventId") String providerEventId,
            @Param("paymentAttemptId") UUID paymentAttemptId,
            @Param("rawPayloadHash") String rawPayloadHash
    );

    Optional<PaymentWebhookEvent> findByProviderAndProviderEventId(String provider, String providerEventId);
}

package com.shopee.monolith.modules.payment.entity;

import com.shopee.monolith.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_webhook_events")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentWebhookEvent extends BaseEntity {

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_event_id", nullable = false, length = 255)
    private String providerEventId;

    @Column(name = "payment_attempt_id")
    private UUID paymentAttemptId;

    @Column(name = "raw_payload_hash", length = 64)
    private String rawPayloadHash;

    @Column(name = "processed_at")
    private Instant processedAt;

    public void markProcessed(Instant at) {
        this.processedAt = at;
    }
}

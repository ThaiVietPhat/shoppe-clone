package com.shopee.monolith.modules.payment.entity;

import com.shopee.monolith.common.entity.BaseEntity;
import com.shopee.monolith.modules.payment.model.PaymentAttemptStatus;
import com.shopee.monolith.modules.payment.model.PaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAttempt extends BaseEntity {

    @Column(name = "checkout_session_id", nullable = false)
    private UUID checkoutSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentAttemptStatus status;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "external_tx_id", length = 255)
    private String externalTxId;

    @Column(name = "provider_payload_hash", length = 64)
    private String providerPayloadHash;

    @Column(name = "reconciliation_reason", length = 255)
    private String reconciliationReason;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Version
    @Column(name = "version", nullable = false)
    @lombok.Builder.Default
    private int version = 0;

    public void succeed(String externalTxId) {
        this.status = PaymentAttemptStatus.SUCCEEDED;
        this.externalTxId = externalTxId;
    }

    public void fail(String externalTxId) {
        this.status = PaymentAttemptStatus.FAILED;
        this.externalTxId = externalTxId;
    }

    public void expire() {
        this.status = PaymentAttemptStatus.EXPIRED;
    }

    public void requireReconciliation(String reason) {
        this.status = PaymentAttemptStatus.REQUIRES_RECONCILIATION;
        this.reconciliationReason = reason;
    }
}

package com.shopee.monolith.modules.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
@Schema(name = "PaymentStatusResponse", description = "Buyer-facing payment status for return pages and polling")
public record PaymentStatusResponse(
        @Schema(description = "Checkout session ID", example = "8f14e45f-ceea-467f-9c4e-1d2a3b4c5d6e")
        UUID checkoutSessionId,

        @Schema(description = "Latest payment attempt ID; null when no attempt exists yet")
        UUID paymentAttemptId,

        @Schema(description = "Latest payment attempt status; NONE when no attempt exists",
                example = "PENDING")
        String status,

        @Schema(description = "Order IDs created by the checkout session")
        List<UUID> orderIds,

        @Schema(description = "Next action for the client: a payment URL for pending online payments, otherwise null",
                example = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?...")
        String nextAction,

        @Schema(description = "Expiry of the latest payment attempt")
        Instant expiresAt,

        @Schema(description = "Reason the attempt requires manual reconciliation, if any", example = "AMOUNT_MISMATCH")
        String reconciliationReason
) {
}

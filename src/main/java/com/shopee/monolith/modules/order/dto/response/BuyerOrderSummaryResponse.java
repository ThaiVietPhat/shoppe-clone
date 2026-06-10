package com.shopee.monolith.modules.order.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
@Schema(name = "BuyerOrderSummaryResponse", description = "Buyer order list row")
public record BuyerOrderSummaryResponse(
        @Schema(description = "Order ID")
        UUID orderId,

        @Schema(description = "Shop ID")
        UUID shopId,

        @Schema(description = "Shop name snapshot resolved at read time", example = "Shopee Mall Demo")
        String shopName,

        @Schema(description = "Order status", example = "PENDING_PAYMENT")
        String status,

        @Schema(description = "Payment status", example = "UNPAID")
        String paymentStatus,

        @Schema(description = "Order grand total = items subtotal + shipping fee", example = "150000.00")
        BigDecimal totalAmount,

        @Schema(description = "Number of order items", example = "2")
        int itemCount,

        @Schema(description = "Order creation timestamp")
        Instant createdAt
) {
}

package com.shopee.monolith.modules.order.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
@Schema(name = "SellerOrderSummaryResponse", description = "Seller order dashboard list row")
public record SellerOrderSummaryResponse(
        @Schema(description = "Order ID")
        UUID orderId,

        @Schema(description = "Order status", example = "PAID")
        String status,

        @Schema(description = "Payment status", example = "PAID")
        String paymentStatus,

        @Schema(description = "Payment method", example = "COD")
        String paymentMethod,

        @Schema(description = "Fulfillment status; null until the order is paid", example = "READY_TO_SHIP")
        String fulfillmentStatus,

        @Schema(description = "Order grand total = items subtotal + shipping fee", example = "150000.00")
        BigDecimal totalAmount,

        @Schema(description = "Number of order items", example = "2")
        int itemCount,

        @Schema(description = "Recipient name from the immutable shipping snapshot", example = "Nguyen Van A")
        String shippingRecipientName,

        @Schema(description = "Order creation timestamp")
        Instant createdAt
) {
}

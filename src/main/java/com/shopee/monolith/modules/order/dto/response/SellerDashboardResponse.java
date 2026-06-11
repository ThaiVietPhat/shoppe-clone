package com.shopee.monolith.modules.order.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
@Schema(name = "SellerDashboardResponse", description = "Lightweight seller read model for the dashboard home")
public record SellerDashboardResponse(
        @Schema(description = "Seller's shop ID")
        UUID shopId,

        @Schema(description = "Total products in the shop, all statuses", example = "12")
        long totalProducts,

        @Schema(description = "Products currently ACTIVE", example = "8")
        long activeProducts,

        @Schema(description = "Product counts keyed by listing status name",
                example = "{\"ACTIVE\": 8, \"DRAFT\": 3, \"INACTIVE\": 1}")
        Map<String, Long> productCountsByStatus,

        @Schema(description = "Order counts keyed by fulfillment status name; UNFULFILLED groups unpaid orders",
                example = "{\"READY_TO_SHIP\": 2, \"SHIPPED\": 1, \"DELIVERED\": 5, \"UNFULFILLED\": 3}")
        Map<String, Long> orderCountsByFulfillmentStatus,

        @Schema(description = "Order counts keyed by payment status name",
                example = "{\"PAID\": 8, \"UNPAID\": 3}")
        Map<String, Long> orderCountsByPaymentStatus,

        @Schema(description = "Newest paid orders waiting to ship — the seller's actionable queue")
        List<SellerOrderSummaryResponse> latestActionableOrders
) {
}

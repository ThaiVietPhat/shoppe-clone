package com.shopee.monolith.modules.order.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
@Schema(name = "CheckoutPreviewResponse", description = "Full cost breakdown for selected cart items — no inventory reserved")
public record CheckoutPreviewResponse(
        @Schema(description = "Per-shop grouped items and fees") List<CheckoutPreviewShopGroup> shops,
        @Schema(description = "Invalid items whose shop could not be resolved (variant or product inactive); "
                + "always carry valid=false and an invalidReasonCode") List<CheckoutPreviewItemResult> invalidItems,
        @Schema(description = "Sum of all shop itemsSubtotals") BigDecimal totalItemsSubtotal,
        @Schema(description = "Sum of all shop shippingFees") BigDecimal totalShippingFee,
        @Schema(description = "totalItemsSubtotal + totalShippingFee") BigDecimal grandTotal,
        @Schema(description = "True only when every item in every shop is valid") boolean allItemsValid,
        @Schema(description = "Address used for the estimate", nullable = true) UUID addressId,
        @Schema(description = "Cart version at time of preview — include in POST /api/orders to detect cart drift") long cartVersion
) {}

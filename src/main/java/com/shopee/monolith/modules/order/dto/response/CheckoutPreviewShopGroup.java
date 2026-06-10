package com.shopee.monolith.modules.order.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
@Schema(name = "CheckoutPreviewShopGroup", description = "Per-shop breakdown in checkout preview")
public record CheckoutPreviewShopGroup(
        @Schema(description = "Shop ID") UUID shopId,
        @Schema(description = "Shop name") String shopName,
        @Schema(description = "Items from this shop") List<CheckoutPreviewItemResult> items,
        @Schema(description = "Sum of item totals for this shop") BigDecimal itemsSubtotal,
        @Schema(description = "Estimated shipping fee for this shop") BigDecimal shippingFee,
        @Schema(description = "itemsSubtotal + shippingFee") BigDecimal shopTotal
) {}

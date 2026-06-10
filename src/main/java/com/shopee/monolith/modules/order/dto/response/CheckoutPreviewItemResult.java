package com.shopee.monolith.modules.order.dto.response;

import com.shopee.monolith.modules.order.model.InvalidReasonCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
@Schema(name = "CheckoutPreviewItemResult", description = "Per-item validation result in checkout preview")
public record CheckoutPreviewItemResult(
        @Schema(description = "Variant ID") UUID variantId,
        @Schema(description = "Product ID") UUID productId,
        @Schema(description = "Product name") String productName,
        @Schema(description = "Variant display name") String variantName,
        @Schema(description = "Variant SKU") String sku,
        @Schema(description = "Quantity selected") int quantity,
        @Schema(description = "Current unit price") BigDecimal unitPrice,
        @Schema(description = "Item total (unitPrice × quantity)") BigDecimal itemTotal,
        @Schema(description = "Whether this item passes all checkout validations") boolean valid,
        @Schema(description = "Reason code when valid=false", nullable = true) InvalidReasonCode invalidReasonCode
) {}

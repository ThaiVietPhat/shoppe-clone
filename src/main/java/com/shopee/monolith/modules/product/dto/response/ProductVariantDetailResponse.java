package com.shopee.monolith.modules.product.dto.response;

import com.shopee.monolith.modules.media.dto.response.ProductMediaSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Builder
@Schema(description = "Detailed variant response with stock and checkout eligibility")
public record ProductVariantDetailResponse(
        @Schema(description = "Variant unique ID")
        UUID id,

        @Schema(description = "Product unique ID")
        UUID productId,

        @Schema(description = "Stock keeping unit", example = "IPHONE15-PRO-256-BLK")
        String sku,

        @Schema(description = "Variant display name", example = "256GB Black Titanium")
        String name,

        @Schema(description = "Variant price", example = "1099.00")
        BigDecimal price,

        @Schema(description = "Option labels for display (e.g. color/size/storage)",
                example = "{\"color\": \"Black Titanium\", \"storage\": \"256GB\"}")
        Map<String, String> optionLabels,

        @Schema(description = "Whether this variant is active")
        boolean active,

        @Schema(description = "Available stock for this variant", example = "42")
        int availableStock,

        @Schema(description = "Whether this variant can be added to cart (active + in stock)", example = "true")
        boolean checkoutEligible,

        @Schema(description = "Variant cover/thumbnail image (if separately set)", nullable = true)
        ProductMediaSummary coverMedia,

        @Schema(description = "Timestamp when the variant was created")
        Instant createdAt,

        @Schema(description = "Timestamp when the variant was last updated")
        Instant updatedAt
) {}

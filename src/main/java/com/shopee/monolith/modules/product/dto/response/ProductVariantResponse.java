package com.shopee.monolith.modules.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Builder
@Schema(description = "Response payload containing product variant details")
public record ProductVariantResponse(
        @Schema(description = "Product variant unique ID", example = "5f123eb4-7b7d-4bad-9bdd-2b0d7b3dcb6d")
        UUID id,

        @Schema(description = "Product unique ID", example = "6e123eb4-7b7d-4bad-9bdd-2b0d7b3dcb6d")
        UUID productId,

        @Schema(description = "Stock keeping unit code", example = "IPHONE15-PRO-256-BLK")
        String sku,

        @Schema(description = "Variant name", example = "256GB Black Titanium")
        String name,

        @Schema(description = "Variant price", example = "1099.00")
        BigDecimal price,

        @Schema(description = "Option labels for display", example = "{\"color\": \"Black\", \"storage\": \"256GB\"}")
        Map<String, String> optionLabels,

        @Schema(description = "Whether this variant is active", example = "true")
        boolean active,

        @Schema(description = "Timestamp when the variant was created")
        Instant createdAt,

        @Schema(description = "Timestamp when the variant was last updated")
        Instant updatedAt
) {}

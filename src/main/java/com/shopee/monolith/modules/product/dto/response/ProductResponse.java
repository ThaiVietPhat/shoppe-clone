package com.shopee.monolith.modules.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
@Schema(description = "Response payload containing product details along with variants")
public record ProductResponse(
        @Schema(description = "Product unique ID", example = "6e123eb4-7b7d-4bad-9bdd-2b0d7b3dcb6d")
        UUID id,

        @Schema(description = "Shop unique ID where the product belongs", example = "4a123eb4-7b7d-4bad-9bdd-2b0d7b3dcb6d")
        UUID shopId,

        @Schema(description = "Category unique ID (optional)", example = "3b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
        UUID categoryId,

        @Schema(description = "Product name", example = "iPhone 15 Pro")
        String name,

        @Schema(description = "Product description details", example = "The latest iPhone with titanium design")
        String description,

        @Schema(description = "List of product variants")
        List<ProductVariantResponse> variants,

        @Schema(description = "Timestamp when the product was created")
        Instant createdAt,

        @Schema(description = "Timestamp when the product was last updated")
        Instant updatedAt
) {}

package com.shopee.monolith.modules.product.dto.response;

import com.shopee.monolith.modules.product.entity.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
@Schema(description = "Lightweight product card for listing pages and search results. "
        + "Only ACTIVE products are returned from public endpoints.")
public record ProductCardResponse(
        @Schema(description = "Product unique ID")
        UUID id,

        @Schema(description = "Product name", example = "iPhone 15 Pro")
        String name,

        @Schema(description = "Brand name", example = "Apple")
        String brand,

        @Schema(description = "Public URL of the cover image (null if no cover)")
        String coverImageUrl,

        @Schema(description = "Object key of the cover image (null if no cover)")
        String coverObjectKey,

        @Schema(description = "Minimum price across active variants", example = "999.00")
        BigDecimal minPrice,

        @Schema(description = "Maximum price across active variants", example = "1299.00")
        BigDecimal maxPrice,

        @Schema(description = "Product listing status", example = "ACTIVE")
        ProductStatus status,

        @Schema(description = "Shop unique ID")
        UUID shopId,

        @Schema(description = "Shop name", example = "Apple Official Store")
        String shopName,

        @Schema(description = "Category path", example = "Electronics/Mobile Phones")
        String categoryPath,

        @Schema(description = "Timestamp when created")
        Instant createdAt
) {}

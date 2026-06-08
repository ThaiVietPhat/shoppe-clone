package com.shopee.monolith.modules.product.dto.response;

import com.shopee.monolith.modules.product.entity.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

        @Schema(description = "Seller reference SKU, when available", example = "APPLE-IP15PRO-2024")
        String sellerSku,

        @Schema(description = "Public URL of the cover image (null if no cover)")
        String coverImageUrl,

        @Schema(description = "Cover media asset ID (null if no cover)")
        UUID coverMediaId,

        @Schema(description = "Object key of the cover image (null if no cover)")
        String coverObjectKey,

        @Schema(description = "Cover media content type (null if no cover)", example = "image/png")
        String coverContentType,

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

        @Schema(description = "Shop average rating (0.0-5.0)", example = "4.8")
        BigDecimal shopRating,

        @Schema(description = "Category path", example = "Electronics/Mobile Phones")
        String categoryPath,

        @Schema(description = "Whether at least one variant can be added to cart from this card")
        boolean checkoutEligible,

        @Schema(description = "Structured card-level publish or checkout eligibility issues")
        List<ProductEligibilityIssue> eligibilityIssues,

        @Schema(description = "Timestamp when created")
        Instant createdAt
) {}

package com.shopee.monolith.modules.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import com.shopee.monolith.modules.product.entity.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

        @Schema(description = "Product listing status", example = "DRAFT")
        ProductStatus status,

        @Schema(description = "Brand name", example = "Apple")
        String brand,

        @Schema(description = "Seller internal SKU/reference", example = "APPLE-IP15PRO-2024")
        String sellerSku,

        @Schema(description = "Flexible product attributes")
        Map<String, Object> attributes,

        @Schema(description = "Minimum price across active variants", example = "999.00")
        BigDecimal minPrice,

        @Schema(description = "Maximum price across active variants", example = "1299.00")
        BigDecimal maxPrice,

        @Schema(description = "List of product variants")
        List<ProductVariantResponse> variants,

        @Schema(description = "Timestamp when the product was created")
        Instant createdAt,

        @Schema(description = "Timestamp when the product was last updated")
        Instant updatedAt
) {}

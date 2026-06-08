package com.shopee.monolith.modules.product.dto.response;

import com.shopee.monolith.modules.media.dto.response.ProductMediaSummary;
import com.shopee.monolith.modules.product.entity.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
@Schema(description = "Full product detail response for product detail page. "
        + "Public endpoint only returns products with status=ACTIVE.")
public record ProductDetailResponse(
        @Schema(description = "Product unique ID")
        UUID id,

        @Schema(description = "Shop unique ID")
        UUID shopId,

        @Schema(description = "Product listing status", example = "ACTIVE")
        ProductStatus status,

        @Schema(description = "Product name", example = "iPhone 15 Pro")
        String name,

        @Schema(description = "Product description")
        String description,

        @Schema(description = "Brand name", example = "Apple")
        String brand,

        @Schema(description = "Seller internal SKU/reference", example = "APPLE-IP15PRO-2024")
        String sellerSku,

        @Schema(description = "Category unique ID")
        UUID categoryId,

        @Schema(description = "Category materialized path", example = "Electronics/Mobile Phones/Smartphones")
        String categoryPath,

        @Schema(description = "Flexible product attributes",
                example = "{\"material\": \"Titanium\", \"os\": \"iOS 17\"}")
        Map<String, Object> attributes,

        @Schema(description = "Minimum price across active variants", example = "999.00")
        BigDecimal minPrice,

        @Schema(description = "Maximum price across active variants", example = "1299.00")
        BigDecimal maxPrice,

        @Schema(description = "Whether a cover image is attached")
        boolean hasCover,

        @Schema(description = "Product media ordered by sort_order")
        List<ProductMediaSummary> media,

        @Schema(description = "Product variants with stock and checkout eligibility")
        List<ProductVariantDetailResponse> variants,

        @Schema(description = "Structured seller-visible publish or checkout eligibility issues")
        List<ProductEligibilityIssue> eligibilityIssues,

        @Schema(description = "Shop summary")
        ShopSummaryDto shop,

        @Schema(description = "Total available stock across checkout-eligible variants", example = "250")
        int totalAvailableStock,

        @Schema(description = "Timestamp when the product was created")
        Instant createdAt,

        @Schema(description = "Timestamp when the product was last updated")
        Instant updatedAt
) {}

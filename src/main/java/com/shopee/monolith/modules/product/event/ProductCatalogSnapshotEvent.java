package com.shopee.monolith.modules.product.event;

import com.shopee.monolith.modules.product.dto.response.ProductEligibilityIssue;
import com.shopee.monolith.modules.product.entity.ProductStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Snapshot event published after product create, update, publish, unpublish, or delete.
 * Contains all fields needed by downstream consumers (Search index, AI embedding).
 * Published post-COMMIT via @TransactionalEventListener — safe for eventual consistency.
 */
public record ProductCatalogSnapshotEvent(
        UUID productId,
        UUID shopId,
        ProductStatus status,
        String name,
        String description,
        String categoryPath,
        String brand,
        Map<String, Object> attributes,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String coverImageUrl,
        UUID coverMediaId,
        String coverMediaObjectKey,
        String coverMediaContentType,
        String shopName,
        BigDecimal shopRating,
        boolean checkoutEligible,
        List<ProductEligibilityIssue> eligibilityIssues,
        List<VariantSnapshot> variants
) {

    public record VariantSnapshot(
            UUID variantId,
            String sku,
            BigDecimal price,
            Map<String, String> optionLabels,
            boolean active
    ) {}
}

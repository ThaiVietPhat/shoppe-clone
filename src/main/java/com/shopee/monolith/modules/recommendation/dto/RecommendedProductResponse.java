package com.shopee.monolith.modules.recommendation.dto;

import com.shopee.monolith.modules.product.dto.response.ProductCardResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Builder
@Schema(description = "Product card plus deterministic recommendation reason codes")
public record RecommendedProductResponse(
        @Schema(description = "Hydrated product card. Client should render commerce data from this object only.")
        ProductCardResponse product,

        @Schema(description = "Deterministic reason codes for this recommendation")
        List<RecommendationReasonCode> reasonCodes
) {
}

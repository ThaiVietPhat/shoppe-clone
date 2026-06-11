package com.shopee.monolith.modules.recommendation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Builder
@Schema(description = "Recommendation response with degraded-mode indicator")
public record RecommendationResponse(
        @Schema(description = "Recommended products. Commerce data is deterministic and comes from ProductCardResponse.")
        List<RecommendedProductResponse> items,

        @Schema(description = "True when AI/vector retrieval failed and deterministic fallback was used", example = "false")
        boolean degraded,

        @Schema(description = "Reason for degraded mode, null when degraded=false", example = "AI_PROVIDER_UNAVAILABLE")
        String degradedReason,

        @Schema(description = "Optional grounded AI explanation. Clients must not parse commerce data from this text.")
        String generatedText
) {
}

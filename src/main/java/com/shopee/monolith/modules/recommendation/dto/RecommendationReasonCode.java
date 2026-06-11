package com.shopee.monolith.modules.recommendation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Reason why a product was recommended")
public enum RecommendationReasonCode {
    TRENDING,
    RECENTLY_VIEWED,
    SIMILAR_TO_CART,
    SIMILAR_TO_ORDER,
    WISHLIST_RELATED,
    AI_SEMANTIC_MATCH
}

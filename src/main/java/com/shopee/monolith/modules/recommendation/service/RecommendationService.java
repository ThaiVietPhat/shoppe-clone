package com.shopee.monolith.modules.recommendation.service;

import com.shopee.monolith.modules.recommendation.dto.ChatRecommendRequest;
import com.shopee.monolith.modules.recommendation.dto.RecommendationResponse;

import java.util.UUID;

public interface RecommendationService {

    RecommendationResponse homeRecommendations(UUID userId, int page, int size);

    RecommendationResponse chatRecommendations(UUID userId, ChatRecommendRequest request);
}

package com.shopee.monolith.modules.review.dto.response;

import com.shopee.monolith.common.response.PagedResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
@Schema(name = "ProductReviewListResponse",
        description = "Product rating summary plus a page of reviews, newest first")
public record ProductReviewListResponse(
        @Schema(description = "Average rating across all reviews (0 when none)", example = "4.50")
        BigDecimal ratingAvg,

        @Schema(description = "Total number of reviews", example = "12")
        long ratingCount,

        @Schema(description = "Page of reviews, newest first")
        PagedResponse<ReviewResponse> reviews
) {
}

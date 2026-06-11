package com.shopee.monolith.modules.review.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
@Schema(name = "ReviewResponse", description = "A single product review")
public record ReviewResponse(
        @Schema(description = "Review ID")
        UUID id,

        @Schema(description = "Reviewed product ID")
        UUID productId,

        @Schema(description = "Reviewed order item ID")
        UUID orderItemId,

        @Schema(description = "Reviewer user ID")
        UUID buyerId,

        @Schema(description = "Star rating 1-5", example = "5")
        int rating,

        @Schema(description = "Review comment", example = "Great product, fast delivery")
        String comment,

        @Schema(description = "Timestamp when created")
        Instant createdAt,

        @Schema(description = "Timestamp when last updated")
        Instant updatedAt
) {
}

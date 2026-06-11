package com.shopee.monolith.modules.review.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
@Schema(name = "UpdateReviewRequest", description = "Payload for updating an existing review")
public record UpdateReviewRequest(
        @Schema(description = "Star rating 1-5", example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
        @Min(1) @Max(5)
        int rating,

        @Schema(description = "Optional review comment", example = "Still good after one month")
        @Size(max = 2000)
        String comment
) {
}

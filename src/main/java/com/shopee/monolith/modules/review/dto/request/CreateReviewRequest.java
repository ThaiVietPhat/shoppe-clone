package com.shopee.monolith.modules.review.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.UUID;

@Builder
@Schema(name = "CreateReviewRequest", description = "Payload for reviewing one delivered/completed order item")
public record CreateReviewRequest(
        @Schema(description = "Order item to review (one review per order item)",
                example = "7f6ad36e-1f25-4f9a-bf6e-2c1f4a1a9b01",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        UUID orderItemId,

        @Schema(description = "Star rating 1-5", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
        @Min(1) @Max(5)
        int rating,

        @Schema(description = "Optional review comment", example = "Great product, fast delivery")
        @Size(max = 2000)
        String comment
) {
}

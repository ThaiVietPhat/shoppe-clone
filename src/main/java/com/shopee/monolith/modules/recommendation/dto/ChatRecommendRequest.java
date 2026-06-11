package com.shopee.monolith.modules.recommendation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "AI shopping-assistant recommendation request")
public record ChatRecommendRequest(
        @NotBlank
        @Size(max = 500)
        @Schema(description = "User shopping message. PII is sanitized before any AI prompt.", example = "Find me a gaming mouse under 500k")
        String message,

        @DecimalMin("0.00")
        @Schema(description = "Optional minimum price filter")
        BigDecimal minPrice,

        @DecimalMin("0.00")
        @Schema(description = "Optional maximum price filter")
        BigDecimal maxPrice,

        @Min(1)
        @Max(20)
        @Schema(description = "Maximum number of products to return", example = "5")
        Integer limit
) {
}

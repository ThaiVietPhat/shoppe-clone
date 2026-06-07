package com.shopee.monolith.modules.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
@Schema(description = "Compact shop summary embedded in product responses")
public record ShopSummaryDto(
        @Schema(description = "Shop unique ID")
        UUID id,

        @Schema(description = "Shop name", example = "Apple Official Store")
        String name,

        @Schema(description = "Shop average rating (0.0–5.0)", example = "4.8")
        BigDecimal rating
) {}

package com.shopee.monolith.modules.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Keyword and facet search request parameters")
public record SearchRequest(

        @Schema(description = "Full-text keyword query", example = "iphone 15")
        String q,

        @Schema(description = "Filter by category ID (includes subcategories)")
        UUID categoryId,

        @Schema(description = "Filter by brand name", example = "Apple")
        String brand,

        @Schema(description = "Minimum price filter", example = "100.00")
        BigDecimal priceMin,

        @Schema(description = "Maximum price filter", example = "2000.00")
        BigDecimal priceMax,

        @Schema(description = "Sort order: RELEVANCE, PRICE_ASC, PRICE_DESC, NEWEST",
                example = "RELEVANCE", allowableValues = {"RELEVANCE", "PRICE_ASC", "PRICE_DESC", "NEWEST"})
        String sort,

        @Schema(description = "Page index (0-indexed)", example = "0")
        @Min(0) int page,

        @Schema(description = "Page size (max 50)", example = "20")
        @Min(1) @Max(50) int size
) {
    public SearchRequest {
        if (sort == null) {
            sort = "RELEVANCE";
        }
    }
}
